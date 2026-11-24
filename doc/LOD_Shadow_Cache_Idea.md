# LOD shadow cache (idea, not implemented)

Design for item 20 in `BACKPORT_CANDIDATES.md`: redraw only the Distant Horizons LOD shadows every N frames into a private
cache that is copied into the shadow map each frame, while near shadows stay live. Worked out with the user on 2026-11-24
(user idea, several review rounds), then dropped.

> **TL;DR: not implementing (user decision, 2026-11-24).** Technically feasible, not worth the engineering. Few packs render
> LOD shadows at all (of the tested packs only Bliss, with its DH shadowmap option, which sits behind menus and warnings).
> The gain is small where it matters: the copy cost scales with shadow resolution just like the LOD shadow cost does. At 4096
> both are high; at 2048 the copy is 1/4 of that and at 1024 1/16, and by then the shadow cost itself is minimal. Kept here in
> case a future pack makes LOD shadows common.

Idea (user): apply the interval only to the distant LOD shadows. Near shadows stay live (mobs, contraptions, placed blocks), so
the lag is in the part of the map where it is hardest to spot. Emberlens's version skips the whole shadow map, which is easy to see
on nearby shadows.

**Why a private cache, not a pack-visible texture.** Packs only sample `shadowtex0/1` and `shadowcolor0/1`. `dh_shadow` draws into
the same shadow framebuffer as near terrain (`NewWorldRenderingPipeline.createDHShadowFramebuffer`); there is no `dhShadowTex` a pack
would read. So the cached LOD shadows must be merged back into the real shadow map every frame.

**Cache only the opaque LODs; LOD water is drawn every frame.** This keeps every translucent (near and LOD) in its original draw
order every frame, so blending stays exact (a depth-tested merge cannot reproduce blend order for coloured translucent shadows). It
also means no merge shader at all (a raw copy works for every attachment format), one cached depth texture instead of two, and no
frozen water caustics. Cost: LOD water shadows are drawn every frame (flat quads, usually a small share of the LOD geometry; split
`DH_SHADOW` into opaque and water in `PassTiming` to see it).

**Per frame.**
1. Refresh frame (every N frames, or forced, see below): clear the cache with the pack's clear values (below), draw only the opaque
   LODs with `dh_shadow` into it, and take the matrix snapshot (below). Cache contents: depth, plus the `shadowcolorN` attachments
   that `dh_shadow` writes, each allocated with the identical internal format as the real attachment. Mip level 0 only.
2. Every frame, in the shadow pass:
   - for shadow depth and the cached colour attachments: copy the cache into the shadow targets (`glCopyImageSubData`, level 0)
     instead of clearing them. The copy is format-agnostic (float, integer, normalized). Attachments `dh_shadow` does not write keep
     their normal clear;
   - draw near opaque geometry as usual; the depth test merges it with the cached LODs;
   - `copyPreTranslucentDepth` as usual, so `shadowtex1` = LOD opaque + near opaque;
   - LOD water (`renderDeferredLodsForShaders`) and near translucents as usual, in their original order;
   - `generateMipmaps()` as usual: it already runs at the very end of the shadow pass, so mipmaps come from the completed map. Never
     generate mipmaps on the cache and never copy mip levels;
   - `shadowcomp` passes then run on the completed map, every frame.
3. Open question to check in DH 3.3.3: DH draws LOD shadows from inside Minecraft's `renderChunkLayer` during the shadow pass,
   interleaved with near terrain (opaque via `renderLods`, water via `renderDeferredLodsForShaders`). The refresh needs an
   opaque-LOD-only draw into the cache: e.g. call DH's opaque render at the start of the shadow pass before any near layer, with
   `DhFrameBufferWrapper` returning the cache framebuffer, and cancel DH's normal opaque call in that pass
   (`MixinDHShadowPassGuard`), while the water call still runs every frame. Find which of these works without DH redrawing or
   clearing something else.

**Opaque draw order (user concern, 2026-11-24).** With the cache, opaque LODs are always "drawn" (copied) before near opaque
geometry, which may not match the original interleaved order. For depth this does not matter: with depth testing, depth writes on and
no blending, the final depth is the nearest fragment in any order (discards only remove fragments; polygon offset is applied per draw
before the test). Where order does matter:
- Depth ties: the tie winner depends on order, but the depth written is identical; only the `shadowcolor` value can differ, and only
  where an LOD and a near block hit exactly the same depth in the same texel (practically never: LOD meshes are different geometry).
- Blending on opaque shadow geometry (`blend.shadow`, `blend.dh_shadow` or the per-buffer forms): colour becomes order-dependent.
  Cache off for such packs (pack checks below).
- Check DH's original order first: whether `renderLods` runs before or after near terrain in the shadow pass today. If it is already
  "LODs first", the cache keeps the original order exactly.

Depth-testing assumptions (user, 2026-11-24): the argument above only holds when both paths use compatible depth functions and have
depth writes on. Inspect before assuming order independence (not automatically incompatible):
- Depth writes disabled for any opaque shadow draw (its fragments then neither occlude nor get occluded consistently).
- Unusual depth functions (anything other than `LESS`/`LEQUAL`, e.g. `GREATER`, `ALWAYS`, `EQUAL`).
- Programs that write `gl_FragDepth` (still order-independent if test and write stay on, but worth reading; also disables early-Z).
- Colour write masks or draw buffers that leave an attachment untouched. Concrete case: `dh_shadow` writes depth but not
  `shadowcolor1`, while near shadow programs do. Original order with LODs drawn after near geometry: a nearer LOD fragment replaces the
  depth but `shadowcolor1` keeps the near block's value. Cache order: the near fragment fails the depth test, so `shadowcolor1` keeps
  the cleared value. Compare the `DRAWBUFFERS`/`RENDERTARGETS` of `dh_shadow` with the near shadow programs.
Two layers of checking: static (pack directives and shader source, at pipeline build) and runtime checks of the actual GL state
(depth func, depth mask, colour masks, draw buffers, blend).

State must be checked for every draw whose order changes, on every refresh, not just once (user, 2026-11-24: other render types can
change blending, depth or colour masks later). Which draws change order: the shadow pass draws terrain solid, cutout, cutout-mipped,
then entities, block entities, then translucent terrain (`ShadowRenderer`), and DH draws opaque LODs from inside the solid-layer
call. So in today's path the opaque LODs already come before cutout, entities, block entities and translucents: those see the same
order with the cache, whatever render-type state they use (eyes, no depth write, etc.). Only near solid-layer terrain can swap
places with the LODs, and only if DH draws after it in that call. So:
- The LOD draw itself: our refresh sets the required state explicitly through `RenderSystem` before DH draws (depth test on,
  `LEQUAL`, depth write on, colour masks per `dh_shadow`'s draw buffers), so it does not depend on what ran before.
- Near solid-layer terrain: check its state on every refresh from Minecraft's `GlStateManager` cached state, which is free (no GPU
  sync). Once per pipeline, also confirm with real `glGet*` calls that the cached state matches GL (a sync point, so only once; it
  catches mods that bypass `GlStateManager`). A mismatch counts as unsupported pack behaviour (cache off, logged).
- First, check DH's injection point: if DH draws opaque LODs at the start of the solid-layer call, nothing changes order at all and
  only the LOD draw's own state matters.
- Known limitation (user, 2026-11-24): the per-refresh check reads `GlStateManager`'s cached state, and the real `glGet*` check runs
  once per pipeline, so a mod that starts bypassing `GlStateManager` later (after that check) goes undetected. Querying real GL
  state continuously would undermine the optimization: a `glGet*` does not necessarily stall the GPU, but it can force a sync with
  the driver's command-processing thread, which is undesirable in a hot rendering path. So this is accepted. Symptoms would be wrong
  colours in distant shadows that differ between N=1 and N>1; the per-pack opt-out list is the workaround. Periodic re-validation
  (e.g. every ~10 s) is a debug-only option, off by default and not in the player UI (hidden config key or JVM property), never a
  recurring check in normal play, so ordinary gameplay gets no periodic frame-time spikes (user, 2026-11-24).

**Matrix snapshot, frozen in world space.** The shadow modelview is camera-relative: `ShadowMatrices.createModelViewMatrix` is the
sun rotation plus `snapModelViewToGrid`, an offset from the camera position within the `shadowIntervalSize` grid, and vertices in the
shadow pass and in gbuffers are camera-relative too. Keeping the refresh frame's literal matrix while the player moves would offset
every lookup by the distance moved (shadows would slide with the player). So the snapshot is the sun rotation, the snapped
world-space origin and the projection; each frame the camera-relative matrix is rebuilt from the snapshot and the current camera
position. Near geometry then lands on the same texels as the cached LODs, and nothing drifts between refreshes.
- One snapshot accessor, read by every user of the shadow matrices (grep 2026-11-24: `ShadowRenderer` incl. `FRUSTUM`,
  `ShadowMatrices`, `MatrixUniforms` (`shadowModelView`, `shadowProjection` and inverses), `CelestialUniforms` (`shadowAngle`),
  `IrisLodRenderProgram`, `DhShadowCullingFrustum`).
- Freeze what addresses the shadow map (the matrices, `shadowAngle`, the frustum). Keep live what is in the player's view space:
  `shadowLightPosition`, `sunPosition`, `moonPosition` (freezing them would break lighting as soon as the player turns). Their
  difference from the frozen sun angle is at most about 0.02 degrees at 60 FPS.
- This replaces the 2026-11-23 requirement (user) with the same rule, narrower: one saved matrix set, captured in the same frame as
  both the regular map and `dh_shadow`, and DH's culling never against a stale or half-updated frustum.

**Stabilization.** With a world-space snapshot nothing moves between refreshes; the origin snaps at the same grid crossings as today,
up to N-1 frames later, so fast movement adds no new stepping. The new effect is rotation: the sun angle updates in steps. The sun
moves about 0.3 degrees per second, so a step is about 0.02 degrees at N=4 and 60 FPS (about 0.04 at 30 FPS): about 0.02 blocks at
50 blocks from the player and about 0.7 blocks at the 2,000-block edge, under one texel at 4096. Iris's shadows already shimmer a
little every frame as the sun moves, so this may look the same or calmer. Check at sunrise and sunset (longest shadows).

**`DhShadowCullingFrustum`.** On refresh frames DH culls against the frozen frustum, which is also the near pass's frustum, so the two
cannot drift. On other frames DH draws no opaque LOD shadows (cancelled in `MixinDHShadowPassGuard`); the water call culls against the
same frozen frustum. Verify that DH generic objects are still never drawn into the shadow map (cancelled in the LOD shadow pass today)
and that DH never clears the shadow framebuffer (`MixinDHFramebufferClear` never clears it today).

**Interval.** Counted in frames, 1-4 (1 = off: today's code path, untouched). Frames rather than milliseconds because the saving is per
frame (N=4 always means 75% less opaque LOD shadow work), a ms window would give no saving at low FPS where it matters most, and a
fixed every-Nth-frame rhythm feels steadier than refreshes landing on irregular frames. Internal safety cap: refresh anyway if the
cache is older than about 250 ms (hitches, GC pauses, alt-tab, very low FPS). Not a user setting.

**Invalidation (next frame redraws the cache).**
- Shadow light switches between sun and moon (direction flips).
- Pipeline rebuild: dimension change, pack switch, and shader-option changes (Iris applies those by reloading the pack, which destroys
  the pipeline and the cache with it).
- Our own interval option changes (going to 1 frees the cache).
- Teleport or a camera jump larger than a few blocks; a jump in the sun angle (`/time set`, more than about 1 degree).
- Shadow distance, shadow resolution, DH render distance or render distance changed; window resize if it reallocates shadow targets.
- DH settings DH rebuilds without telling the pipeline (quality, `ignoredRenderBlockCsv`, ...): hook DH's config/event API if it has
  one, otherwise the age cap covers it. Resource pack reload.
- New LODs loading wait for the next refresh; the age cap bounds that.

**Clear values.** Before the LOD draw, clear the cache with exactly the pack's `shadowcolorNClearColor` values and the same settings
`ClearPassCreator` uses for the real shadow targets, and depth to the normal clear depth, so texels without LODs copy over exactly as a
normal clear would leave them.

**Pack checks (cache off for that pack, logged once; every frame as today).**
- The pack has no active `dh_shadow`.
- Side effects a copy cannot replay: any stage of `dh_shadow` declares an image uniform or a `buffer` (SSBO) block, or uses
  `imageStore`, `imageAtomic*` or atomic counters. Conservative and static; near shadow programs are unaffected (they run every frame).
- A `shadowcolorN` that `dh_shadow` writes has `shadowcolorNClear = false` (the pack wants it kept between frames; the copy would
  overwrite it).
- Blending enabled for opaque shadow geometry (`blend.shadow`, `blend.dh_shadow` or per-buffer blend directives for them): colour
  would depend on draw order (see "Opaque draw order").
- A depth-testing assumption that fails on inspection (see "Depth-testing assumptions"): depth writes off, an unusual depth function,
  or `dh_shadow` leaving a colour attachment untouched that near shadow programs write. `gl_FragDepth` alone is logged, not blocked.
- Per-pack opt-out list in our config, as a safety valve if a pack shows visible stepping.

**Accepted lag, logged rather than blocked.** The source scan logs these for the pack's `dh_shadow`; they are checked visually, not
used to disable the cache (a static check would hit almost every pack, since water and foliage code share files behind material
branches):
- Animated opaque LOD materials (waving foliage via `frameTimeCounter`): frozen for at most N frames / 250 ms; LOD sway is a fraction
  of a block at 100+ blocks.
- Write-side noise or dithering (`frameCounter`, `noisetex`, blue noise) in `dh_shadow`: the pattern freezes for N frames, so TAA may
  converge more slowly in distant shadows. Read-side noise (rotated PCF / blue-noise kernels in gbuffers/deferred) stays live.
- Live-camera uniforms in `dh_shadow` (`cameraPosition`, `gbufferModelView`, `eyePosition`, `relativeEyePosition`) beyond coordinate
  reconstruction, e.g. an overdraw cutout discarding LODs within `far - 32` of the player (like Bliss's `DH_solid`): in the cached map
  the hole stays where it was on the refresh frame, so a moving player could see a band of missing or doubled shadow a few blocks wide
  at the chunk/LOD seam for up to N frames (see "Camera-dependent overdraw cutoff"). Shadow distortion (Bliss's log distortion,
  radial bends) is computed from shadow-space coordinates around the shadow origin and stays consistent with the snapshot. Bliss is
  the only tested pack with `dh_shadow`: read its source when implementing.

**Camera-dependent overdraw cutoff (user concern, 2026-11-24).** If the cached LOD shadows contain a camera-relative exclusion zone,
near geometry moving into or out of that zone could briefly lose its shadow. Between refreshes the cached hole sits a little behind
the player: on the side the player moves towards, LOD and chunk shadows both exist for a moment (depth takes the nearest, nothing
visible changes); on the side the player moves away from, the pack's overlap between near chunks and LODs covers the lag. Missing
shadows need the player to move further within one window than that overlap: Bliss's margin is 32 blocks (`far - 32`), elytra
flight covers about 2 blocks per 4-frame window at 60 FPS. Risky moments: crossing a chunk boundary (the loaded area shifts by up to
16 blocks at once) and vertical flight with a sphere-shaped cutout. Tests (user): walking and sprinting across chunk boundaries;
flying vertically across the vanilla-to-LOD transition; fast elytra flight; moving around tall structures with long shadows. If the
seam shows: a movement-based refresh threshold (refresh when the camera has moved more than a fraction of the overlap since the
snapshot), applied only to packs whose `dh_shadow` uses camera uniforms (the source scan logs that), so other packs keep the saving
during travel. Costs performance only during fast travel.

**OpenGL state.** The refresh draw sits inside the shadow pass, where Iris already sets up and restores shadow state, and binds the
cache framebuffer the same way `DhFrameBufferWrapper` binds the shadow framebuffer today. Afterwards restore: draw and read
framebuffer, viewport, draw buffers, depth test/func/mask, blend, colour mask, cull, polygon offset, scissor. Go through
`RenderSystem`/`GlStateManager`: raw GL calls desync Minecraft's cached state and cause bugs frames later. The copy
(`glCopyImageSubData`) changes no binding state.

**Oculus Flywheel Compat.** It injects at `ShadowRenderer.renderShadows` HEAD and TAIL. Keep both reachable on every path
(no early return before TAIL for the cache paths), and keep its Flywheel shadow drawing inside the near pass, drawn every frame.

**Transactional refresh (user, 2026-11-24).** A refresh that fails or is interrupted must never leave partial shadow data that later
frames reuse.
- Three states (user): `INVALID`, `REFRESHING`, `VALID`. A refresh moves `INVALID`/`VALID` -> `REFRESHING`; only a refresh that
  completes moves to `VALID`; anything else goes back to `INVALID`. `REFRESHING` also guards against re-entry (a nested shadow pass
  or a second refresh in the same frame).
- `VALID` only after both: DH has completed the opaque LOD rendering path, and OpenGL state has been restored (in a `try/finally`).
  No GPU fence is needed: later copies are ordered after the draw in the same GL context.
- Completion needs an explicit signal from DH's render path (user). Not program-bind counting (a program can be bound without
  submitting geometry) and not a before-render event (it does not show that rendering finished). Candidates to check in DH 3.3.3:
  an after-render API event if DH has one for this path, or our own mixin at the end of DH's opaque LOD render method, after its
  draw loop and placed so DH's early returns (not ready, nothing loaded) skip it. Then "DH ran, culling removed every section" still
  reaches the signal (a valid empty cache), and "DH did not run" does not (stays `INVALID`, so LOD shadows never vanish for a window).
- While the cache is not `VALID`, the frame uses today's path (normal clear, DH draws opaque LODs straight into the shadow map).
- Also `INVALID` on pipeline destroy, resize/reallocation of shadow targets, or any exception during the shadow pass.

**Failure handling: never a recurring penalty (user, 2026-11-24).** A refresh that keeps failing must not cost more than today's path.
Classify why a refresh did not complete:
- DH temporarily not ready (world loading, DH starting): use today's path and retry next frame. Check readiness before clearing the
  cache or starting a refresh, so a not-ready frame costs exactly what it costs today.
- Cache allocation or framebuffer failure (`GL_OUT_OF_MEMORY` on allocation, incomplete framebuffer status): disable caching for the
  current pipeline and log the reason once; a new pipeline (pack reload, option change, dimension change) tries again.
- Unsupported pack behaviour (pack checks above, static or the one-time runtime check): disable caching until the pack or its options
  change, log once.
- Completion signal never reached while DH is ready (no exception, e.g. DH caught its own error): retry a few times (e.g. 3 in a
  row), then disable for the current pipeline and log.
- Exceptions are not swallowed (user, 2026-11-24): see "Failure recovery".
For the expected failures above, the frame itself renders with today's path, so the worst case is today's cost plus at most a few
wasted attempts.

**Failure recovery (user, 2026-11-24).** Fall back to ordinary rendering only if GL state was restored successfully.
- A refresh only ever writes the cache, never the real shadow targets (they are copied into only once the cache is `VALID`), so a
  failed refresh cannot corrupt this frame's shadow map; the only risk is GL state.
- Before the refresh, record the state it changes (framebuffer bindings, viewport, draw buffers, depth/blend/colour/cull state,
  program) from `GlStateManager` and our own bindings. Restore it in `finally`, then compare against the record.
- Restored and verified, no exception (an expected failure: not ready, incomplete framebuffer, signal not reached): cache `INVALID`,
  continue the frame on today's path.
- Any exception (from DH or our code): restore state in `finally`, set the cache `INVALID`, disable caching for the current pipeline,
  log once with the full stack trace, then rethrow. No catch-and-continue: an exception inside DH's draw would most likely also
  happen on today's path, and hiding it would hide real bugs.
- Restore fails or does not verify: do not keep rendering on unknown state. Rethrow the original exception, with the restore failure
  attached as a suppressed exception (or a new one if there was no original), so the crash report shows both.

**Costs and risks.**
- VRAM: one cached depth texture plus the cached colour attachments at the shadow resolution: about 64 MB per 32-bit target at 4096
  (so roughly 64-192 MB). Allocate only while the option is above 1; free on pipeline destroy. Say so in the tooltip.
- Memory bandwidth (user, 2026-11-24): the copy reads and writes every cached target, competing with the shader passes. A 4096
  target is 64 MB, so 128 MB moved per target per frame; depth plus two colour targets is about 384 MB per frame, about 23 GB/s at
  60 FPS and about 55 GB/s at 144 FPS, a real share of a laptop GPU's bandwidth. Copy only the attachments `dh_shadow` writes.
  Modern GPUs make clears nearly free (fast clear / depth compression set metadata), so replacing the clear saves almost nothing.
  A copied depth buffer may also lose its compression and hierarchical-Z state, so the near pass depth-tests a bit slower: a hidden
  cost that shows up in `SHADOW`, not in the copy's own timing.
- Frame pacing: the per-frame copy cannot be avoided (each frame needs a clean LOD-only base before near geometry). Estimated
  0.1-0.5 ms at 4096 plus the hidden cost above. Refresh frames are therefore
  slightly slower than today (today + the copy); other frames are today minus the opaque LOD shadow cost. Average FPS rises; 1% lows
  can get slightly worse, which can feel like a faint regular stutter. v2 idea, not in the first version: spread the refresh over the
  N frames by using `DhShadowCullingFrustum` to draw 1/N of the LOD sections per frame into a second (double-buffered) cache,
  swapping when complete (adds N frames of lag).

**Experimental page.** Its own option, not part of "Enable safe optimizations"; risk Medium; applies live (no reload); "Reset"
returns it to 1.
- Suggested starting value N=2 (user, 2026-11-24): the step from 1 to 2 gives the biggest extra saving (`C_opaque/2`; 2 -> 3 adds
  `C_opaque/6`, 3 -> 4 adds `C_opaque/12`) with the least lag. Caveat: that first step pays the whole copy, so N=2 needs `C_opaque`
  of at least 2x the copy's total cost to break even (N=4 only 1.33x); raise N only if the overlay's prediction says N=2 is close
  to break-even.
- Tooltip note on shadow resolution and bandwidth (inform, do not tell players to lower it): the copy cost scales with pixel count,
  so 4096 costs about 4x the copy of 2048, while `C_opaque` (mostly LOD geometry) drops much less, so the cache pays off more easily
  at 2048. At 4096 the copy moves a lot of GPU memory: about 384 MB per frame with depth plus two colour targets, about 23 GB/s at
  60 FPS and 55 GB/s at 144 FPS (depth only: about 128 MB per frame, 8-18 GB/s). At 2048 it is a quarter of that (about 96 MB per
  frame, 6-14 GB/s). Lower resolution costs quality (Bliss's DH shadowmap over 2,048 blocks: about 2 blocks per texel instead of
  1), so it stays the player's choice.

**Measure first, then predict (cost model: user, 2026-11-24).** Before writing code: `PassTiming` overlay at a low sun, render
distance 4, DH 128 chunks, Bliss DH shadowmap, same spot, comparing `SHADOW` and `DH_SHADOW` (split into opaque and water), plus a
one-off timing of the copy. That predicts the result for every N before anything is built:
- Average saving per frame: `S = C_opaque * (1 - 1/N) - C_copy - C_side`. Today pays `C_opaque` every frame; the cache pays it once
  per window plus the copy every frame. LOD water and near shadows cost the same either way, so they drop out.
- `C_copy`: the copy's own time. Clears are nearly free on modern GPUs, so no credit for the clear it replaces (an earlier
  `C_copy,net = C_copy - C_clear + C_clear/N` refinement was dropped, 2026-11-24).
- `C_side`: indirect costs of the copy's bandwidth (lost depth compression / hierarchical Z, cache pollution) that land in other
  passes, mostly the near shadow pass. Unknown until built, so the formula with `C_side = 0` is an upper bound. Once built, measure
  it as the increase in near-pass `SHADOW` time with the copy versus today's path at the same spot, and use that measured value in the
  break-even, `P` and the optional live prediction.
- Refresh-frame penalty: `P ≈ C_copy + C_side`, how much slower a refresh frame is than today. `S` predicts average FPS, `P` the
  1% lows.
- Compute `S` separately for CPU and GPU (the overlay shows both). The copy is almost all GPU, so the CPU saving is close to
  `C_opaque,cpu * (1 - 1/N)`. The frame only gets faster on the side that is the bottleneck.
- Break-even: `S > 0` when `C_opaque > (C_copy + C_side) * N / (N - 1)`, i.e. 2x the copy's total cost at N=2, 1.5x at N=3, 1.33x
  at N=4.
- Diminishing returns, e.g. `C_opaque = 3 ms`, `C_copy + C_side = 0.3 ms`: N=2 1.2 ms, N=3 1.7 ms, N=4 1.95 ms, N=8 2.3 ms. Going past 4 adds
  little while the lag keeps growing, which is why N stops at 4.

Build it only if the predicted `S` is worth having at the user's settings. Once built, time the copy as its own pass, but confirm
`S` from the whole shadow pass (`SHADOW` + `DH_SHADOW` + copy) and frame time, not by adding up the separate pass timings (that misses
`C_side`). Compare 1% lows, not just averages. Optional: while the option is at 1, the timing overlay
shows the predicted `S` and `P` live from the measured values, so a player can see whether turning it on would help at that spot.

**Tests.**
1. Correctness first: `/gamerule doDaylightCycle false`, player standing still. Nothing changes between refreshes, so N=1 and N=4
   screenshots from the same spot must be pixel-identical apart from noise. This checks the copy, clear values, formats and state
   restore together.
2. A/B N=1..4: opaque/water `DH_SHADOW`, the copy, frame time (average and 1% lows).
3. Motion (user, 2026-11-24): walking and sprinting across chunk boundaries; flying vertically across the vanilla-to-LOD transition
   (and ~25 blocks up so the ground below is LODs); fast elytra flight; moving around tall structures with long shadows. Watch the
   chunk/LOD seam for missing or doubled shadows.
4. Events: sunrise/sunset switch; `/time set`; teleport; pack switch; shader-option change; DH render distance change; world load
   (DH not ready yet: LOD shadows must not vanish, see "Transactional refresh").

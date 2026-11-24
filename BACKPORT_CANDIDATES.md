# Backport candidates (non-DH)

Changes from other Oculus forks that could be ported to this tree. Only changes that work with stock
Embeddium/Rubidium are listed. Items 1-13, 15 and 22 are ported (batch 1, 2026-11-23, untested in game); 16-18 and 20 are next (21 folded into 20, 2026-11-24).

Source so far: [Emberlens](https://github.com/shuttheirprayer/Emberlens) (1.20.1 fork of Oculus 1.8.x,
LGPL-3.0 like this repo; port with credit). Read 2026-11-23 at `44ebaaff9`. Each entry was checked
against this tree: the original code Emberlens changed is present here in the same form, under
`net.coderbot` instead of `net.irisshaders`.

Status: `todo` = not started, `ported` = in a commit (add the hash), `tested` = checked in game,
`skip` = decided against.

Ported optimizations are toggles on the Experimental page (shader pack screen > "Experimental..."), all off by
default and stored globally in `config/oculus-experimental.properties`; with all of them off the code paths are the
stock ones. Correctness fixes (15) are always on and committed separately. No Emberbium dependency.

## Small, low risk (CPU per draw; helps every pack)

| # | Change | Emberlens commit | Our file(s) | Status |
|---|---|---|---|---|
| 1 | `unbindAllSamplers` tracks the highest bound unit and clears only up to it with one `glBindSamplers`, instead of walking every texture unit | `15e3fe0e6` | `iris/gl/IrisRenderSystem.java` | ported `d392ef5` (toggle) |
| 2 | Skip the `glMemoryBarrier` in `Program.use()` until the pack has bound an image or storage buffer | `f62845b4b` | `iris/gl/program/Program.java`, `IrisRenderSystem.java` | ported `d392ef5` (toggle) |
| 3 | Cache the projection inverse, model-view inverse and normal matrix in `ExtendedShader`; recompute only when the matrix changes | `1e56e4871` | `iris/pipeline/newshader/ExtendedShader.java` | ported `d392ef5` (toggle) |
| 4 | Custom uniforms: version counter per uniform and a flat per-pass push list, so `push()` uploads only changed values and stops boxing locations | `8c0ea4b2b`, part of `70d2cc4a9` | `iris/uniforms/custom/CustomUniforms.java`, `cached/CachedUniform.java` | ported `d392ef5` (toggle) |
| 5 | Indexed loops instead of iterators over the per-bind uniform/sampler/image lists | `26ce8715f` | `iris/gl/program/Program{Uniforms,Samplers,Images}.java` | ported `d392ef5` (toggle) |
| 6 | Entity buffer LRU: `Object2IntLinkedOpenHashMap` (`getAndMoveToLast`) instead of `LinkedHashMap<RenderType,Integer>` | part of `70d2cc4a9` | `batchedentityrendering/impl/FullyBufferedMultiBufferSource.java` | ported `d392ef5` (toggle) |
| 7 | Entity render ordering: don't reallocate empty graphs every frame; skip empty graphs in `getRenderOrder` | `24911620d` | `batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager.java` | ported `d392ef5` (toggle) |
| 8 | `MapDigraph` vertex map: `Object2ObjectLinkedOpenHashMap` instead of `LinkedHashMap` | `56b51f4e0` | `de/odysseus/ithaka/digraph/MapDigraph.java` | ported `d392ef5` (toggle) |
| 9 | Drop the bitangent normalisation in tangent generation (only its sign is used; Emberlens ships a check) | `c85881622` | `iris/vertices/NormalHelper.java` | ported `d392ef5` (toggle) |
| 10 | Custom-uniform expressions: a named variable caches its lookup per context instead of resolving the name on every evaluation | part of `70d2cc4a9` | `kroppeb/stareval/resolver/ExpressionResolver.java` | ported `d392ef5` (toggle) |
| 11 | Date/time uniforms read the clock lazily (once per tick, only if a pack uses them) | part of `70d2cc4a9` | `iris/uniforms/IrisTimeUniforms.java` | ported `d392ef5` (toggle) |
| 12 | `PipelineManager.getPipeline()` returns a cached `Optional` instead of allocating one per call | part of `70d2cc4a9` | `iris/pipeline/PipelineManager.java` | ported `d392ef5` (toggle) |
| 13 | Don't sort the entity list when no shader pack is active | part of `70d2cc4a9` | `batchedentityrendering/mixin/MixinLevelRenderer_EntityListSorting.java` | ported `d392ef5` (toggle) |
| 14 | Shadow pass: replace the outline buffer source with one that discards everything (outlines are never drawn into the shadow map) | part of `70d2cc4a9` | `batchedentityrendering/mixin/MixinRenderBuffers.java`, `iris/pipeline/ShadowRenderer.java` | skip: our shadow pass never calls `outlineBufferSource()` (entities draw into `buffers.bufferSource()`), so it would do nothing |
| 15 | Bug fix: `RenderTargets.resizeIfNeeded` also recreates depth when the depth texture id changes, not only the version | part of `70d2cc4a9` | `iris/rendertarget/RenderTargets.java` | ported `11e0bc1` (always on) |

## Larger

| # | Change | Emberlens commit | Our file(s) | Notes | Status |
|---|---|---|---|---|---|
| 16 | Extended vertex writing: precomputed offsets per format (`ExtendedVertexLayout`), one hook at the end of each vertex with direct buffer writes instead of `putShort`/`nextElement` chains | part of `70d2cc4a9` | `iris/mixin/vertices/MixinBufferBuilder.java` (+ new `ExtendedVertexLayout`) | The biggest CPU win for entities. Our 1.19.2 mixin differs a little (UV1 injection), so it needs adapting, not copying. Test entities, text/glyphs, terrain fallback, MekaSuit | todo |
| 17 | Fast path that writes a whole 56-byte entity vertex with `MemoryUtil` stores | `44ebaaff9` | new `MixinBufferBuilder_FastEntityVertex` | Depends on 16. Guarded by a layout check | todo |
| 18 | `BufferBuilderPolygonView` reads through raw addresses | `44ebaaff9` | `iris/vertices/BufferBuilderPolygonView.java` | Small; fits with 16 | todo |
| 19 | Chunk meshing hooks: `@ModifyExpressionValue`/`@Local` instead of `@Inject` + `LocalCapture` (no callback object per block) | `c386e5ea5` | `iris/compat/sodium/mixin/block_id/MixinChunkRenderRebuildTask.java` | Needs MixinExtras, which this tree does not bundle (would need jar-in-jar). Our 1.19.2 Embeddium meshing code also differs | todo |

> Requirements for 16-18 (user, 2026-11-23): Oculus Flywheel Compat writes and reads its own unsafe 52-byte vertex format (`ExtendedBlockWriterUnsafe`, `IrisBlockVertexListUnsafe`) while shaders are on. `ExtendedVertexLayout` (16) and the 56-byte fast entity vertex path (17) must only engage when the BufferBuilder's current `VertexFormat` is exactly the Iris format they were built for (identity check, plus stride and element layout), fall back to the stock path for anything else, and never touch buffers or formats Flywheel owns. Test them together with Flywheel Compat near Create contraptions (moving and static, shaders on), with the options on and off, and watch for vertex corruption or an EXCEPTION_ACCESS_VIOLATION like the known shader-toggle crash.

## Optional features

| # | Change | Emberlens commit | Notes | Status |
|---|---|---|---|---|
| 20 | **LOD shadow update interval (1-4 frames)**: only the DH LOD shadows are redrawn every N frames, into a private cache (opaque LODs only; LOD water stays live) that is copied into the shadow map every frame; near terrain, entities and block entities are still drawn every frame. Replaces Emberlens's whole-map skip (user idea, 2026-11-24). Design below | idea from `d3a976c4c`, `4215cefa8`, `022f07372`, `027d65610`, `82d486e6e` (their saved-matrix part is the reference; the whole-map skip is not ported) | Trades distant shadow lag (hard to see) for `DH_SHADOW` time. Measure first | todo (design 2026-11-24) |
| 21 | Skip clearing custom images that only the shadow pass writes, on frames where it is skipped | part of `70d2cc4a9` | Folded into 20: with the LOD cache the shadow pass still runs every frame, so its clears stay. If ever revived: Emberlens builds the regex with `"\b"` (a backspace in Java), so it never matches; use `"\\b"` | skip (folded into 20, 2026-11-24) |
| 22 | `PassTimers`: GPU time per composite pass via `GL_TIME_ELAPSED` queries, logged every 2000 frames, off by default | `c6528b012` | A debug tool for our own A/B tests | ported `d392ef5` as `PassTiming`: CPU + GPU per pass (timestamp queries), overlay and log |

### Design for item 20: LOD shadow cache (user idea + discussion, 2026-11-24)

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

## Not porting

| Change | Why |
|---|---|
| Vertex format via Emberbium's `getAttributeBindings()` hook (`685246315`) | Needs Emberbium; no speed gain (runs only at region creation) |
| Depth prepass + auto-tuner (`c6528b012`) | The prepass alpha threshold is a guess (pack threshold scraped by regex + 0.05) and the main pass then writes no depth, so pixels between the two thresholds lose depth; packs that discard for other reasons (dithering) would leave depth with no colour. Its band skip hooks Emberbium |
| Flywheel/Create instancing (`685f2de7a`, `50e018b22`) | Flywheel 1.0 (Create 6) only exists on 1.20.1. On 1.19.2 the Oculus Flywheel Compat mod covers this |
| `MixinBufferBuilder_SegmentRendering` `@Redirect` on `ensureVertexCapacity` (part of `70d2cc4a9`) | Unclear gain; a redirect conflicts more easily with other mods |

## Planned: integrate Oculus Flywheel Compat (before batch 2's vertex work)

Source: [leon-o/iris-flw-compat](https://github.com/leon-o/iris-flw-compat) branch `1.19.2` (MIT, "Copyright (c) 2022 Red Face";
keep the notice and credit it). About 47 Java files / 2,700 lines; built against Oculus 1.6.9, i.e. our base, so its mixins into
Oculus classes (`IrisRenderingPipeline`, `ProgramSet`, `ShadowRenderer`, `ProgramSamplers`, ...) become plain code in our classes.
Agreed plan (user, 2026-11-23):
1. Port as-is into an optional `compat/flywheel` module, loaded like the DH compat: a mixin plugin applies its mixins only when
   `flywheel` is loaded and the standalone mod `irisflw` is not (conflict guard; log which one is active).
2. Flywheel 0.6 (Create 0.5.1's) as compile-only, fetched by CI like DH; never committed.
3. Parity test against the standalone mod: Photon near Create (standalone gave 67-81 -> 90-110 FPS), shadows, clean log.
4. Fix the shader-toggle crash in our copy: buffers remember the vertex format they were built in (or Flywheel models are rebuilt
   when shaders switch) instead of choosing the 52-byte reader/writer live from `isShaderPackInUse()`.
5. Only then start the vertex-writing ports (16-18), with both sides of the vertex code in one codebase.

Notes (user ideas + source check, 2026-11-23):
- Take only the `irisflw` classes. On their 1.19.2 branch `1.0.3` bundled Flywheel via Loom `include(...)`; commit `28ec825`
  ("Will not include the flywheel in jar", the `1.0.3b` bump) removed it, so the source is compile-only against
  `flywheel-forge 0.6.10-20` (the version CI should fetch). Their main-branch changelog about bundling is `1.1.3` for newer MC.
- Fix the toggle crash with a rebuild, not dual 32/52-byte readers: after the new pipeline is active (creation and destruction),
  trigger Flywheel's backend/instance reload and clear their compiled-program cache (their own TODO in
  `MixinProgramCompiler.invalidate`: "remove cache when shader pack reloaded"). Today Oculus only calls `levelRenderer.allChanged()`
  in `PipelineManager` when the block ID map changed, so a toggle does not reliably reach Flywheel. Verify that Create rebuilds its
  cached contraption models on that reload, not only Flywheel instances. Optional safety net: a buffer remembers its build format and
  is skipped (not read) on a mismatch, so a missed case drops a frame instead of crashing.
- No player-facing toggle (user decision, 2026-11-23): when on, the hooks cost nothing measurable (the standalone mod showed no
  cost with shaders off: 227-258 FPS vs 246 baseline), and turning it off only loses Flywheel instancing under shaders (Photon near
  Create 90-110 -> 67-81 FPS). It is simply on whenever Flywheel is present and `irisflw` is not. For dev testing only: a startup
  kill switch (config-file key or JVM property, e.g. `-Dspectacles.flywheel=false`) that makes the mixin plugin skip the mixins, to
  A/B against the standalone mod or rule it out in a crash; needs a restart, not shown in any GUI. (Their mixins are 17 `@Inject`,
  2 `@Redirect`, 2 `@ModifyVariable`, no `@Overwrite`, so a live flag would also be possible if ever needed; the main switch would
  be `IrisHandlerMixin`, which forces Flywheel's `ShadersModHandler.isShaderPackInUse()` to false.)

## Iris 1.7 / Oculus 1.8.0 features (port only when a pack needs one; lowest priority)

| # | Feature | Notes | Status |
|---|---|---|---|
| 23 | Tessellation shaders (`.tcs`/`.tes`) | Our loader reads only `.vsh`/`.gsh`/`.fsh`/`.csh`, so a pack's tessellation files are ignored (effect missing, or a link error if later stages need their outputs). Port: load and link both stages, draw with `GL_PATCHES` when a program has them, and add the `TESSELLATION_SHADERS` feature flag (as in the 1.8 line's `FeatureFlags`): packs then get `IRIS_FEATURE_TESSELLATION_SHADERS` when they list it as optional, and a clear "missing feature" message when they require it. Do **not** define `IRIS_VERSION` as 1.7: packs could read it as support for other Iris 1.7 features Spectacles doesn't have. Advertise only what is implemented, feature by feature. None of the tested packs (Complementary, Euphoria, Photon, Bliss, Sildur's) need it, likely because packs also target OptiFine. | todo (last) |

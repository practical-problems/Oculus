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
  at the chunk/LOD seam for up to N frames. Shadow distortion (Bliss's log distortion, radial bends) is computed from shadow-space
  coordinates around the shadow origin and stays consistent with the snapshot. Bliss is the only tested pack with `dh_shadow`: read
  its source when implementing and test the seam while moving fast.

**OpenGL state.** The refresh draw sits inside the shadow pass, where Iris already sets up and restores shadow state, and binds the
cache framebuffer the same way `DhFrameBufferWrapper` binds the shadow framebuffer today. Afterwards restore: draw and read
framebuffer, viewport, draw buffers, depth test/func/mask, blend, colour mask, cull, polygon offset, scissor. Go through
`RenderSystem`/`GlStateManager`: raw GL calls desync Minecraft's cached state and cause bugs frames later. The copy
(`glCopyImageSubData`) changes no binding state.

**Costs and risks.**
- VRAM: one cached depth texture plus the cached colour attachments at the shadow resolution: about 64 MB per 32-bit target at 4096
  (so roughly 64-192 MB). Allocate only while the option is above 1; free on pipeline destroy. Say so in the tooltip.
- Frame pacing: the per-frame copy cannot be avoided (each frame needs a clean LOD-only base before near geometry). It replaces the
  clear, so the extra cost is roughly one read of the shadow targets, estimated 0.1-0.5 ms at 4096. Refresh frames are therefore
  slightly slower than today (today + the copy); other frames are today minus the opaque LOD shadow cost. Average FPS rises; 1% lows
  can get slightly worse, which can feel like a faint regular stutter. v2 idea, not in the first version: spread the refresh over the
  N frames by using `DhShadowCullingFrustum` to draw 1/N of the LOD sections per frame into a second (double-buffered) cache,
  swapping when complete (adds N frames of lag).

**Experimental page.** Its own option, not part of "Enable safe optimizations"; risk Medium; applies live (no reload); "Reset"
returns it to 1.

**Measure first, then predict (cost model: user, 2026-11-24).** Before writing code: `PassTiming` overlay at a low sun, render
distance 4, DH 128 chunks, Bliss DH shadowmap, same spot, comparing `SHADOW` and `DH_SHADOW` (split into opaque and water), plus a
one-off timing of the copy. That predicts the result for every N before anything is built:
- Average saving per frame: `S = C_opaque * (1 - 1/N) - C_copy,net`. Today pays `C_opaque` every frame; the cache pays it once per
  window plus the copy every frame. LOD water and near shadows cost the same either way, so they drop out.
- `C_copy,net ≈ C_copy - C_clear + C_clear/N`: the copy replaces the clear of the real targets, but the cache is cleared on refresh
  frames. Using plain `C_copy` is slightly pessimistic (the safe direction).
- Refresh-frame penalty: `P ≈ C_copy,net`, how much slower a refresh frame is than today. `S` predicts average FPS, `P` the 1% lows.
- Compute `S` separately for CPU and GPU (the overlay shows both). The copy is almost all GPU, so the CPU saving is close to
  `C_opaque,cpu * (1 - 1/N)`. The frame only gets faster on the side that is the bottleneck.
- Break-even: `S > 0` when `C_opaque > C_copy * N / (N - 1)`, i.e. 2x the copy at N=2, 1.5x at N=3, 1.33x at N=4.
- Diminishing returns, e.g. `C_opaque = 3 ms`, `C_copy = 0.3 ms`: N=2 1.2 ms, N=3 1.7 ms, N=4 1.95 ms, N=8 2.3 ms. Going past 4 adds
  little while the lag keeps growing, which is why N stops at 4.

Build it only if the predicted `S` is worth having at the user's settings. Once built, time the copy as its own pass and check
the A/B results against the formula, comparing 1% lows, not just averages. Optional: while the option is at 1, the timing overlay
shows the predicted `S` and `P` live from the measured values, so a player can see whether turning it on would help at that spot.

**Tests.**
1. Correctness first: `/gamerule doDaylightCycle false`, player standing still. Nothing changes between refreshes, so N=1 and N=4
   screenshots from the same spot must be pixel-identical apart from noise. This checks the copy, clear values, formats and state
   restore together.
2. A/B N=1..4: opaque/water `DH_SHADOW`, the copy, frame time (average and 1% lows).
3. Motion and events: fly ~25 blocks up so the ground below is LODs; look for a seam between near and LOD shadows; move fast
   (elytra) and watch the chunk/LOD seam; sunrise/sunset switch; `/time set`; teleport; pack switch; shader-option change; DH
   render distance change.

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

# Backport candidates (non-DH)

Changes from other Oculus forks that could be ported to this tree. Only changes that work with stock
Embeddium/Rubidium are listed. Items 1-13, 15 and 22 are ported (batch 1, 2026-11-23, untested in game); 16-18 are next; 20 (with 21 folded in) was dropped 2026-11-24.

Source so far: [Emberlens](https://github.com/shuttheirprayer/Emberlens) (1.20.1 fork of Oculus 1.8.x,
LGPL-3.0 like this repo; port with credit). Read 2026-11-23 at `44ebaaff9`. Each entry was checked
against this tree: the original code Emberlens changed is present here in the same form, under
`net.coderbot` instead of `net.irisshaders`.

Status: `todo` = not started, `ported` = in a commit (add the hash), `tested` = checked in game,
`skip` = decided against.

Ported optimizations are toggles on the Experimental page (shader pack screen > "Experimental..."), all off by
default and stored globally in `config/oculus-experimental.properties`; with all of them off the code paths are the
stock ones. Correctness fixes (15) are always on and committed separately. No Emberbium dependency.

**Method shape rule (2026-11-24).** "All off = baseline" also has to hold for other mods' mixins, which match the bytecode
shape of a method, not its behaviour. So a port must not change, even with its option off: the local variables in scope at
existing instructions (captured-locals injectors), or the number and order of calls/field accesses/`new` of targets the method
already uses (injection points by ordinal). New code paths go into helper methods with new names; the original method only
gains the flag check and a call to the helper (and possibly an early `return`, which is still a real return). Fixed for batch 1
in `d392ef5` -> `d9d1ee6` (dev): `NormalHelper` (item 9) had moved `bitcoeff` into the `if`, changing the local variable table
(javap on stub builds before/after: original and fixed tables identical, 47 and 50 locals); items 4, 5 and 6-7 had their fast paths
inlined next to calls the methods already make. Remaining always-on shape changes, judged harmless (vendored or internal code nobody
hooks; behaviour identical when off): `ExpressionResolver` named variables are a `NamedVariable` class instead of two anonymous
classes (item 10), `IrisTimeUniforms` lambdas read the time through a helper (item 11), sampler/coherent-memory tracking fields in
`IrisRenderSystem` (items 1-2). Oculus Flywheel Compat's hooks into Oculus (checked 2026-11-24 on leon-o/iris-flw-compat `1.19.2`:
`ProgramSamplers.builder` argument, `NewWorldRenderingPipeline.<init>` TAIL, invokers, an `ExtendedShader` accessor,
`ShadowRenderer.renderShadows` HEAD and TAIL) do not touch any batch 1 change.

Item 9 numeric note: the result can differ from today only on degenerate triangles (UV area close to zero), where the unnormalized
dot can underflow to -0 (read as "not negative") or the normalized one can overflow to 0, flipping the tangent sign. The tangent
itself is meaningless there, so no visible effect. No vertex format stores a bitangent (shaders rebuild it from normal, tangent and
the sign), and all callers, including other mods' writers, only see the packed return value.

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
| 9 | Drop the bitangent normalisation in tangent generation (only its sign is used; Emberlens ships a check) | `c85881622` | `iris/vertices/NormalHelper.java` | ported `d392ef5` (toggle); method shape restored `d9d1ee6` (see below) |
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
| 20 | **LOD shadow update interval (1-4 frames)**: only the DH LOD shadows redrawn every N frames into a private cache copied into the shadow map each frame; near shadows live (user idea, 2026-11-24). Replaced Emberlens's whole-map skip | idea from `d3a976c4c`, `4215cefa8`, `022f07372`, `027d65610`, `82d486e6e` | TL;DR: few packs use LOD shadows, minimal gains. Design kept in [`doc/LOD_Shadow_Cache_Idea.md`](doc/LOD_Shadow_Cache_Idea.md) | skip (2026-11-24) |
| 21 | Skip clearing custom images that only the shadow pass writes, on frames where it is skipped | part of `70d2cc4a9` | Folded into 20: with the LOD cache the shadow pass still runs every frame, so its clears stay. If ever revived: Emberlens builds the regex with `"\b"` (a backspace in Java), so it never matches; use `"\\b"` | skip (folded into 20, 2026-11-24) |
| 22 | `PassTimers`: GPU time per composite pass via `GL_TIME_ELAPSED` queries, logged every 2000 frames, off by default | `c6528b012` | A debug tool for our own A/B tests | ported `d392ef5` as `PassTiming`: CPU + GPU per pass (timestamp queries), overlay and log |

### Item 20: dropped (2026-11-24)

Not implementing: few packs use LOD shadows (of the tested packs only Bliss, behind its DH shadowmap option and warnings), and the
gains are minimal (the cache's copy cost scales with shadow resolution like the shadow cost itself; at 2048 or 1024 there is little
left to save). The full design, cost model and review notes are kept in [`doc/LOD_Shadow_Cache_Idea.md`](doc/LOD_Shadow_Cache_Idea.md).


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

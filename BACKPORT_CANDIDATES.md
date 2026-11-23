# Backport candidates (non-DH)

Changes from other Oculus forks that could be ported to this tree. Only changes that work with stock
Embeddium/Rubidium are listed. Nothing here is ported yet; the DH backport comes first.

Source so far: [Emberlens](https://github.com/shuttheirprayer/Emberlens) (1.20.1 fork of Oculus 1.8.x,
LGPL-3.0 like this repo; port with credit). Read 2026-11-23 at `44ebaaff9`. Each entry was checked
against this tree: the original code Emberlens changed is present here in the same form, under
`net.coderbot` instead of `net.irisshaders`.

Status: `todo` = not started, `ported` = in a commit (add the hash), `tested` = checked in game,
`skip` = decided against.

## Small, low risk (CPU per draw; helps every pack)

| # | Change | Emberlens commit | Our file(s) | Status |
|---|---|---|---|---|
| 1 | `unbindAllSamplers` tracks the highest bound unit and clears only up to it with one `glBindSamplers`, instead of walking every texture unit | `15e3fe0e6` | `iris/gl/IrisRenderSystem.java` | todo |
| 2 | Skip the `glMemoryBarrier` in `Program.use()` until the pack has bound an image or storage buffer | `f62845b4b` | `iris/gl/program/Program.java`, `IrisRenderSystem.java` | todo |
| 3 | Cache the projection inverse, model-view inverse and normal matrix in `ExtendedShader`; recompute only when the matrix changes | `1e56e4871` | `iris/pipeline/newshader/ExtendedShader.java` | todo |
| 4 | Custom uniforms: version counter per uniform and a flat per-pass push list, so `push()` uploads only changed values and stops boxing locations | `8c0ea4b2b`, part of `70d2cc4a9` | `iris/uniforms/custom/CustomUniforms.java`, `cached/CachedUniform.java` | todo |
| 5 | Indexed loops instead of iterators over the per-bind uniform/sampler/image lists | `26ce8715f` | `iris/gl/program/Program{Uniforms,Samplers,Images}.java` | todo |
| 6 | Entity buffer LRU: `Object2IntLinkedOpenHashMap` (`getAndMoveToLast`) instead of `LinkedHashMap<RenderType,Integer>` | part of `70d2cc4a9` | `batchedentityrendering/impl/FullyBufferedMultiBufferSource.java` | todo |
| 7 | Entity render ordering: don't reallocate empty graphs every frame; skip empty graphs in `getRenderOrder` | `24911620d` | `batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager.java` | todo |
| 8 | `MapDigraph` vertex map: `Object2ObjectLinkedOpenHashMap` instead of `LinkedHashMap` | `56b51f4e0` | `de/odysseus/ithaka/digraph/MapDigraph.java` | todo |
| 9 | Drop the bitangent normalisation in tangent generation (only its sign is used; Emberlens ships a check) | `c85881622` | `iris/vertices/NormalHelper.java` | todo |
| 10 | Custom-uniform expressions: a named variable caches its lookup per context instead of resolving the name on every evaluation | part of `70d2cc4a9` | `kroppeb/stareval/resolver/ExpressionResolver.java` | todo |
| 11 | Date/time uniforms read the clock lazily (once per tick, only if a pack uses them) | part of `70d2cc4a9` | `iris/uniforms/IrisTimeUniforms.java` | todo |
| 12 | `PipelineManager.getPipeline()` returns a cached `Optional` instead of allocating one per call | part of `70d2cc4a9` | `iris/pipeline/PipelineManager.java` | todo |
| 13 | Don't sort the entity list when no shader pack is active | part of `70d2cc4a9` | `batchedentityrendering/mixin/MixinLevelRenderer_EntityListSorting.java` | todo |
| 14 | Shadow pass: replace the outline buffer source with one that discards everything (outlines are never drawn into the shadow map) | part of `70d2cc4a9` | `batchedentityrendering/mixin/MixinRenderBuffers.java`, `iris/pipeline/ShadowRenderer.java` | todo |
| 15 | Bug fix: `RenderTargets.resizeIfNeeded` also recreates depth when the depth texture id changes, not only the version | part of `70d2cc4a9` | `iris/rendertarget/RenderTargets.java` | todo |

## Larger

| # | Change | Emberlens commit | Our file(s) | Notes | Status |
|---|---|---|---|---|---|
| 16 | Extended vertex writing: precomputed offsets per format (`ExtendedVertexLayout`), one hook at the end of each vertex with direct buffer writes instead of `putShort`/`nextElement` chains | part of `70d2cc4a9` | `iris/mixin/vertices/MixinBufferBuilder.java` (+ new `ExtendedVertexLayout`) | The biggest CPU win for entities. Our 1.19.2 mixin differs a little (UV1 injection), so it needs adapting, not copying. Test entities, text/glyphs, terrain fallback, MekaSuit | todo |
| 17 | Fast path that writes a whole 56-byte entity vertex with `MemoryUtil` stores | `44ebaaff9` | new `MixinBufferBuilder_FastEntityVertex` | Depends on 16. Guarded by a layout check | todo |
| 18 | `BufferBuilderPolygonView` reads through raw addresses | `44ebaaff9` | `iris/vertices/BufferBuilderPolygonView.java` | Small; fits with 16 | todo |
| 19 | Chunk meshing hooks: `@ModifyExpressionValue`/`@Local` instead of `@Inject` + `LocalCapture` (no callback object per block) | `c386e5ea5` | `iris/compat/sodium/mixin/block_id/MixinChunkRenderRebuildTask.java` | Needs MixinExtras, which this tree does not bundle (would need jar-in-jar). Our 1.19.2 Embeddium meshing code also differs | todo |

## Optional features

| # | Change | Emberlens commit | Notes | Status |
|---|---|---|---|---|
| 20 | Shadow update interval option (1-4): redraw the shadow map every N frames; shadow matrix uniforms use the matrices the map was drawn with; clears skipped with it | `d3a976c4c`, `4215cefa8`, `022f07372`, `027d65610`, `82d486e6e` | Trades shadow lag for FPS. Must also skip the DH LOD shadow pass on the same frames and keep `DhShadowCullingFrustum` consistent | todo |
| 21 | Skip clearing custom images that only the shadow pass writes, on frames where it is skipped | part of `70d2cc4a9` | Only with 20. Broken in Emberlens: the regex is built with `"\b"` (a backspace in Java), so it never matches; use `"\\b"` | todo |
| 22 | `PassTimers`: GPU time per composite pass via `GL_TIME_ELAPSED` queries, logged every 2000 frames, off by default | `c6528b012` | A debug tool for our own A/B tests | todo |

## Not porting

| Change | Why |
|---|---|
| Vertex format via Emberbium's `getAttributeBindings()` hook (`685246315`) | Needs Emberbium; no speed gain (runs only at region creation) |
| Depth prepass + auto-tuner (`c6528b012`) | The prepass alpha threshold is a guess (pack threshold scraped by regex + 0.05) and the main pass then writes no depth, so pixels between the two thresholds lose depth; packs that discard for other reasons (dithering) would leave depth with no colour. Its band skip hooks Emberbium |
| Flywheel/Create instancing (`685f2de7a`, `50e018b22`) | Flywheel 1.0 (Create 6) only exists on 1.20.1. On 1.19.2 the Oculus Flywheel Compat mod covers this |
| `MixinBufferBuilder_SegmentRendering` `@Redirect` on `ensureVertexCapacity` (part of `70d2cc4a9`) | Unclear gain; a redirect conflicts more easily with other mods |

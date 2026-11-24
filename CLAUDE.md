# Oculus 1.19.2 + Distant Horizons 3.x (fork of Teqed/Oculus `1.19.2`)

Handoff notes from a long chat session. Read this first.

**Name (2026-11-23, dev and stable): the mod is now called "Spectacles".** Changed: `mods.toml` displayName/description,
`archives_base_name = spectacles` (jar `spectacles-mc1.19.2-...`), `Iris.MODNAME` (F3 lines, shader screen corner text, log
prefix `[Spectacles/]` instead of `[Oculus/]`, timing overlay header, DH config caller name), README title/intro (Oculus banner
kept on purpose). Kept for compatibility: mod ID `oculus` (`Iris.MODID`, config file names), packages `net.coderbot.iris`,
mixin config names, `mod_label` starting with `1.8.0`, shader macros. GitHub repository rename is the user's to do.
Release plan (user, 2026-11-23): stable gets the rename now (Spectacles 1.0.0, display names only, no experimental code; README marks the Emberlens work as dev-only WIP). After the user's smoke test: tag `v1.0.0` on stable and draft a GitHub release (user attaches the jar). The experimental page ships later (1.1.0) after testing on dev.
Older logs and notes below say `[Oculus/]`.

## Goal
Make a 1.19.2 Forge Oculus (Iris 1.6.x line, package `net.coderbot.iris`) work well with Distant Horizons (DH).
Stage 1 (done as a patched jar, now being moved to source): LODs render with shaders on/off, no underground light leak.
Stage 2 (the real work): a proper DH backport so DH-aware shader packs (Complementary, Euphoria, then Proton/Bliss/Photon)
get LOD depth, DH programs, uniforms and shadows, like the real Oculus 1.8.0 on 1.20.1.

## Base and provenance
- Base = `Teqed/Oculus` branch `1.19.2` (Oculus 1.6.9, Forge 43.2.23, last upstream commit 2023-12-06). Askadar's widely shared
  jar is a build of this branch with the mods.toml version bumped to "1.8.0" so DH's `[1.8.0,)` dependency passes. It is NOT based on
  Asek3's `1.19.2` branch (1.6.9a), which is a different line.
- Teqed's tree is ahead of Asek3's 1.19.2 on pack features (time uniforms, playerLookVector/relativeEyePosition, biome rework,
  BooleanStateExtended blend fix, lazy render targets). Keep them. Asek3's branch is only ahead on the Mekanism fixes.
- Teqed's own DH support is tiny: `DHCompat` (holds one framebuffer id), `IrisDHCompatMixinPlugin`, one apply-shader mixin.
  `NewWorldRenderingPipeline` creates that framebuffer as a gbuffer FBO on colortex0 only. LODs are therefore composited into
  colortex0 with no depth, no DH programs, no DH uniforms/macros. That is the whole gap.

## Branch and status
Branches: `dh-3.3.3-mekanism` = stable/default (moved forward only after the user tests); `dh-3.3.3-mekanism-dev` = work branch
(was `dh-shadow`); `stacia-dh-merge` kept for history. `dh-fallback-copy` is obsolete (fallback removed).
Builds with JDK 17 and was tested in game (Complementary Reimagined + Euphoria Patches, DH 3.3.3). Create with shaders works (user-confirmed).
Oculus Flywheel Compat (`oculus-flywheel-compat-forge1.19.2+1.0.3b`) tested 2026-11-23 with this tree: loads, shadows work, Photon FPS 67-81 -> 90-110 at the same spot (uncapped).
Working: LODs with shaders on/off, pack sees LOD depth (clouds/fog behind LODs), smaller chunk/LOD seam, no underground light leak,
MekaSuit, Mekanism flamethrower. Commits, oldest first:
1. Mekanism MekaSuit/flame fixes ported from Asek3/Oculus `1.19.2` (`ProgramId.ShadowEntities`, `ShaderKey.MEKANISM_FLAME(+_SHADOW)`,
   `LightningHandler`, `ShaderAccess`, `MixinRenderFlame`, `MixinRenderMekasuit`, `Iris.isPackInUseQuick()`).
2. DH 3.3.3 hooks as source, version label (`mod_label`), build against a local DH jar.
3. Flywheel `WorldAttached` leak fix (from Mekalus).
4. Flamethrower crash fix: `mek_flame` render type uses `POSITION_COLOR_TEX` (Asek3's `POSITION_TEX_COLOR` throws
   "Not filled all elements of the vertex"; `ShaderKey` entries keep `POSITION_TEX_COLOR`).
5. Merge of PolarBearEs's DH integration (their commit `5624f2c`, built on fangyu-mc/oculus-dh3-compat): `dh_terrain`/`dh_water`
   programs, `dhDepthTex`/`dhDepthTex1`, `dh*` uniforms (incl. projection matrices), `DISTANT_HORIZONS`/`DH_BLOCK_*` macros, DH terrain
   transformer, five DH mixins. Replaced our `DHCompat`, plugin, `MixinDHCopyShader` and `MixinDHSkipShadowPass`; removed `getDHCompat()`.
   Not taken from their commit: Loom 1.4, wrapper bump, Modrinth DH dependency, workflows.
6. Packs without `dh_terrain`: LODs are hidden while such a pack is active (`MixinDHShadowPassGuard` cancels
   `renderLods`/`renderDeferredLodsForShaders` when a pack is in use and `DHCompat.isActive()` is false), like real 1.8.0
   which marks such packs incompatible. An earlier colortex0 fallback (Teqed's old behaviour) was removed: it drew LODs into
   a buffer the pack uses for something else (Bliss showed a warped sky). Shaders off or a DH-aware pack brings LODs back.
7. `dh_shadow`: `ProgramSet.getDhShadow()`, shadow `ProgramState` in `IrisLodRenderProgram` (shadow samplers/images,
   sun matrices from `ShadowRenderer.PROJECTION`/`MODELVIEW`), shadow-map FBO via `NewWorldRenderingPipeline.createDHShadowFramebuffer`,
   `DhFrameBufferWrapper` returns it in the shadow pass, `MixinDHShadowPassGuard` only cancels when there is no shadow program,
   `MixinDHFramebufferClear` never clears the shadow map. Honours `dhShadow.enabled`; log says "LOD shadows enabled/off".
   Tested with Bliss v2.1.2 (DH shadowmap option): LODs block Bliss's light shafts and cast shadows near the LOD edge.
8. `shaders.properties` macro fix: the 1.6 base text-replaced every standard macro, erasing empty flags (`IS_IRIS`, `DISTANT_HORIZONS`)
   and turning `#ifndef PPT_NONE` into `#ifndef 0`. Broke Bliss (no dh_terrain -> warped sky, hidden DH shadowmap option), Photon and
   Euphoria's DH block. Now: replacements only on value lines, whole words, empty flags skipped, and all names passed to the preprocessor.
9. `dhRenderDistance`/`dhNearPlane`/`dhFarPlane` moved to `generalCommonUniforms` so properties expressions see them (Photon was black).
10. `shadowNearPlane`/`shadowFarPlane` (Iris 1.7+): the base hard-coded the shadow depth range to 0.05..256, clipping distant LODs from the
   shadow map. -1 resolves to +/- DH render distance (old values without DH); `shadowProjection` uniform uses the same matrix.
   Built 2026-11-23; log confirms -1 resolves to +/-2048 (DH 128 chunks) at render time.
11. Shadow-pass LOD culling (tested 2026-11-23, Bliss v2.1.2 DH shadowmap): `DhShadowCullingFrustum` (bound as `IDhApiShadowCullingFrustum` only when dh_shadow
   is active) asks `ShadowRenderer.FRUSTUM` (this frame's terrain shadow frustum): Advanced/Reversed -> plane test only, BoxCulling ->
   distance box, CullEverything -> false, else true. Same rules as 1.8.0's four frustum mixins, done as one wrapper instead of mixins.
   With dh_shadow active the advanced frustum is built from a projection with DH's far plane, (renderDistance+512)*sqrt2 from the live
   DH config like 1.8.0 (not last frame's value),
   otherwise its far plane (vanilla view distance) would cull far LODs. Near plane stays 0.05 (1.8.0 uses DH's, ~20% of render distance
   with shaders, which could cull nearby chunk shadows); so the frustum is never smaller than vanilla's. DH's F3 line "Shadow VBO Render Count: [visible/total]" shows it;
   DH config `disableShadowPassFrustumCulling` turns it off for A/B tests.
   Results (DH still generating chunks in the background): facing the sun 82/181 shadow sections, 140-149 FPS vs 181/181, 118-133 off;
   facing the sun at time 11000, 91/175, 150-152 FPS vs 175/175, 132-137 off. Facing away from a low sun nothing is culled (184/184 both
   ways, same FPS), which is correct: every LOD can shadow something in view. On/off screenshots show no missing shadows.
   Note: DH shows the "/total" on both F3 lines only while its normal `disableFrustumCulling` is off.
   All Bliss DH-shadowmap tests used shadow map resolution 4096 and DH render distance 128 chunks.
   Bliss shadowDistance test (hill, facing the sun, culling on): 128 chunks 91/175, 135-153 FPS; 64 chunks 91/175, 139-150; 32 chunks
   91/175, 143-148; 8 chunks 91/175, 148-149. The shadow count does not depend on shadow distance: neither 1.8.0 nor we cull LODs
   outside the shadow map's area. Possible extra optimization (not in 1.8.0): also cull sections outside the shadow ortho box; it only
   helps DH shadowmap with a shadow distance well below the DH distance, a rare combination, so left for later.
   With a small Bliss shadow distance (8 chunks) light shafts appear to stream out of distant LODs (outside the shadow map counts as
   lit); back at 128 chunks they are blocked again. Expected, not a bug.
   Far-LOD check (rooftop facing the sun with mountains, time 1000, shadow distance 128): culling on 78/184, 146-149 FPS; off 184/184,
   131-132 FPS; screenshots pixel-identical apart from water/FPS-counter noise. 64 chunks gives the same image.
   Nearby shadows (newest build, vanilla near plane, Bliss shadow distance 64 chunks): DH shadowmap on vs off at noon and at time 11000 facing away from the sun match
   (player shadow present); only blurrier with it on (one map over 2048 blocks). Good LOD shadow test from the user: fly ~25 blocks up so
   the area below is drawn as LODs; shadowmap on vs off differs only by added shadows (hills, trees, the tower), none removed.
   Pack switches Bliss -> Photon -> Bliss (twice): culling rebinds (79/184 facing the sun before and after), Photon logs LOD shadows off.
   DH render distance 128 -> 64 -> 128 in-world: 184 -> 142 sections (62/142 in shadow) -> 184 (80/184); log clean, no culling errors.
   Photon always logs "null:0:0: error: #endif without #if" from the JCPP preprocessor (INFO level, every build, Photon renders fine);
   its .properties files have balanced #if/#endif on HEAD, so it is probably a v1.3b file. Not investigated further.
Packs tested on this branch: Complementary/Euphoria (no dh_shadow by design), Photon v1.3b (renders), Bliss v2.1.2 (renders, LOD shadows).

## Past issues and fixes (for context)
- **DH 2.3.x crash:** the old `MixinDHApplyShader` redirected `getTargetFrameBuffer()` inside `onRender`; DH 2.3.3 moved it to
  `renderToFrameBuffer`, so the mixin missed its target. Fix was to list both method names. Superseded by the DH 3 integration.
- **Light leak:** Oculus's shadow pass calls `LevelRenderer.renderChunkLayer` with the sun's matrices and DH hooks that same method, so DH
  rendered LODs during the shadow pass (also halving FPS). Fix: skip DH render calls while `ShadowRenderingState.areShadowsCurrentlyBeingRendered()`.
  Now done by `MixinDHShadowPassGuard`. Side effect: LODs cast no shadows until `dh_shadow` exists.
- **Flamethrower crash:** see commit 4.
- **GitHub Actions:** the inherited workflows used the removed Brachyura build and deprecated actions; deleted. New
  `.github/workflows/build.yml` (2026-11-23): JDK 17, downloads DH 3.3.3 from Modrinth into `libs/` and checks its SHA-512
  (compile-only, never committed), `./gradlew build`, uploads `build/libs/*.jar` as an artifact on pushes to the stable and dev
  branches; a `v*` tag also creates a draft release with the jar. CI jars have no local timestamp suffix.
- **JDK:** builds need JDK 17; JDK 21+ fails in repackaged commons-collections. Set `org.gradle.java.home` in the user-level
  `~/.gradle/gradle.properties` (not the repo) so every terminal uses 17.

## Build
- JDK 17 for Gradle (Minecraft 1.19.2). Loom is `dev.architectury.loom 1.2-SNAPSHOT`.
- Put `DistantHorizons-3.3.3-1.19.2-fabric-forge.jar` in `./libs/` (git-ignored). It is `compileOnly` (DH's API and internals are needed to compile the mixins and event code). This works with Loom 1.2.
- `mod_version` names the jar file (Spectacles' own numbering since the rename: `1.0.0`; the code base is still Oculus 1.6.9);
  `mod_label` is the version string in mods.toml shown in the launcher ("1.8.0 # spoof for DH 3.3").
  Keep the label starting with `1.8.0`.
- A successful build prints ~19 mixin remap warnings; they are noise from the old toolchain.

## DH 3.3.3 facts (from reading the jar)
- Render flow: `ClientApi.renderLods()` / `renderDeferredLodsForShaders()` -> private `renderLodLayer(boolean)`; fade passes
  `renderFadeOpaque()` / `renderFadeTransparent()`. `LodRenderer` fires `DhApiBeforeApplyShaderRenderEvent`, then
  `metaRenderer.copyToMcTexture` -> `GlDhCopyShader_forge.renderToFrameBuffer` (copy.frag, blend disabled, no depth discard).
- DH's Forge `OculusAccessor` is constants (patched by `MixinDHOculusAccessor` to report real pack/shadow state); DH logs "Partial Oculus support enabled".
  DH's own `MixinOculusFrameBuffer` is gated on `net.irisshaders.iris.gl.framebuffer.GlFramebuffer`, which this tree lacks, so it stays off.
- `getTargetFramebuffer()` is lowercase-b in 3.x (it was `getTargetFrameBuffer` in 2.x).
- DH vanilla fade is disabled when a shader pack is active (by DH's design) and DH TAA/AA is skipped under shader packs. Not gaps.
- DH has its own 3D cloud renderer (config `enableCloudRendering`) that clashes with a pack's clouds; users should turn it off.
- Old LOD data from DH 2.x shows as magenta/black checkers until DH regenerates it. Not a bug.
- DH material IDs (`DH_BLOCK_*` 0-15) match DH 3.3.3's `EDhApiBlockMaterial`. DH's `BlockStateWrapper` falls back to `ILLUMINATED`
  for any block with light emission > 0 that matches no earlier category, so faintly glowing modded plants (Serene Shrubbery) glow as
  LODs in Complementary, Photon and Sildur's. DH/pack behaviour, not Oculus. Workaround: add those blocks to DH's ignored-blocks list
  (Advanced > Graphics > Culling > `ignoredRenderBlockCsv`: exact IDs only, no wildcards; applied at render-buffer build, no LOD
  regeneration). Serene Shrubbery 1.5.0: all 26 plants are MCreator blocks with light level 1. AE2 certus buds/clusters do the same.

## Backport status (all 1.8.0 DH features done)
Phases 1 and 2 (depth texture, uniforms, macros, `dh_terrain`/`dh_water`) are done via the PolarBearEs merge. Items 1-4 below are all done (status notes kept for history):
1. **`dh_shadow` follow-ups.** Shadow-pass culling done and tested (commit 11).
2. **Generic rendering (DH clouds/beacons): done, tested 2026-11-23** (build `d2fc7b4`; Bliss, Complementary+Euphoria, Photon:
   beacon beam drawn through the pack at the right place, hidden behind LOD hills, emissive at night, no beam shadow with Bliss's DH
   shadowmap, log line "generic objects ... use dh_terrain" for every pack, no errors). Near gap within ~32 blocks is Bliss's DH
   overdraw prevention (`far - 32` sphere in `dimensions/DH_solid.fsh`); DH's own shader shows the same gap without shaders.
   A freshly placed beacon only appears once DH updates the chunk's LOD (save/unload). DH clouds tested too: hidden with Bliss/Photon/
   Complementary's own clouds (pack clouds=off), drawn and lit by Complementary with its cloud style set to vanilla (slightly
   see-through, stars show through at night; DH blends them). Photon: beam gets bloom and rain-puddle reflections; cloud shadows
   darken its upper part (moves with Photon's cloud altitude). Photon also gives the LOD beam a much larger glow than Minecraft's
   beam (DH ILLUMINATED material -> Photon material 36, full emission; vanilla beam uses gbuffers_beaconbeam); same data as 1.8.0. Ported from 1.8.0
   (reference clone: Asek3/Oculus `1.20.1-new`). `ProgramSet.getDhGeneric()` (`dh_generic`, else `dh_terrain` like 1.8.0's
   `ProgramId.DhGeneric` fallback); `Patch.DH_GENERIC` + `DHGenericTransformer` (instanced unit box: attrib 0 vPosition vec3,
   1 iris_color, 2 aScale, 3 aTranslateChunk ivec3, 4 aTranslateSubChunk, 5 aMaterial int; locations bound at link time since our
   addIfNotExists has no layout qualifier; iris_NormalMatrix is mat4 like our terrain transformer); `IrisGenericRenderProgram`
   (IDhApiGenericObjectShaderProgram; uniforms/samplers/images from SodiumTerrainPipeline like IrisLodRenderProgram; DHCompat's
   LOD matrices so depth matches LODs; lightmap bound like 1.8.0; direct (non-instanced) path only logs a warning instead of 1.8.0's
   exception). DHCompat binds it as an override (DH's own GlGenericObjectShaderProgram.overrideThisFrame() is always true, so binding
   is the switch), binds the LOD terrain framebuffer (or a dh_generic framebuffer with DH depth) in DhApiBeforeGenericRenderSetupEvent,
   and cancels every generic object during the LOD shadow pass (DH runs its generic renderer there too; 1.8.0 does not guard this).
   Pack-owned `dh_generic` path (own framebuffer + DH depth): tested 2026-11-23 with a Bliss copy (dh_terrain.* copied to dh_generic.*;
   no real pack ships dh_generic). Fix `7f822b0`: it attached DH depth with addDepthAttachment (RenderTargets would swap it for MC depth
   on resize) and only re-attached on a new texture id; now addExternalDepthAttachment + a DH depth generation counter. Result: log
   "use dh_generic", beam correct, hidden behind terrain, still hidden after windowed/fullscreen switch. The copy needed
   `blend.dh_generic = off` (Bliss packs gbuffer data; without it DH's blend stays on, like 1.8.0, and the beam smears pink/yellow).
   If the program fails to build, generic objects are hidden while the pack is active (tested 2026-11-23 with a junk line in the Bliss
   copy's dh_generic.fsh: warning + parse error, no crash, beam gone, back after fixing; before, DH's own
   shader drew plain colours into the pack's packed buffers). Edge cases: mod-submitted API boxes take the same path as beacons/clouds
   (untested; per-group `DhApiRenderableBoxGroupShading` is ignored, as in 1.8.0); DH's direct (non-instanced) path is unreachable
   (needs GL without instancing; DH disables generic rendering on macOS); hardware tested only on NVIDIA/Windows, injected GLSL
   reviewed for strict-compiler issues (none found; a glslang build was not allowed in the sandbox). Verified offline: the vendored glsl-transformer patches a legacy test
   shader and Bliss/Photon dh_terrain without errors (harness: /tmp/tx with stubs for log4j/fastutil/LWJGL constants/annotations).
3. (Done as commit 11: the four 1.8.0 frustum mixins are replaced by one `DhShadowCullingFrustum` wrapper.)
4. Small fixes (tested 2026-11-23: Bliss loads, both warnings gone, no new ones): `program.X.enabled` values with `&&`/`||`/`!`/parentheses are now evaluated by
   `shaderpack/parsing/BooleanExpression` (names: boolean option, then defined macro, else true with a warning; unparsable -> enabled
   with a warning; single names keep the old path). Oculus 1.8.0 reads them as one option name. Bliss's `TRANSLUCENT_ENTITIES && IS_IRIS`
   now disables its translucent entity/block programs while that option is off (its default). Evaluator verified with hand-written cases
   (precedence, parentheses, negation, defined(), numbers, unknown names, 19 malformed inputs) and 20,000 random expressions compared with
   Python. DH config overrides now pass the caller name "Oculus" (DH 3.3's two-argument `setValue`), removing the deprecated-API warning.
Do not port the whole Iris 1.7 refactor (package `net.irisshaders`). Port only DH pieces, adapted to `net.coderbot.iris` classes.
See `DH_BACKPORT_PLAN.md` ideas: every DH class real 1.8.0 imports exists in DH 3.3.3, so its DH-facing code is API-compatible.

Non-DH ports from other forks (Emberlens so far) are tracked in `BACKPORT_CANDIDATES.md`, only ones that work with stock
Embeddium/Rubidium (not Emberbium). Update its Status column when one is ported or tested.

## Experimental page (Emberlens ports; batch 1 committed 2026-11-23 as `d392ef5`, untested in game)
User rules: every experimental optimization defaults to off; all off = the completed DH baseline (each port is an `if` on a
static flag around the new path, the old code is untouched); correctness fixes are separate commits and always on (item 15,
`11e0bc1`); no Emberbium dependency; keep Oculus Flywheel Compat working (matters for the vertex-writing batch, which touches
the vertex code that mod also hooks); CPU and GPU timing measured separately, in ms.
- `config/ExperimentalConfig`: options (key, group, name, description, benefit, risk Low/Medium/High, risk note, when it
  applies, `safe` flag), static booleans read by hot paths, global file `config/oculus-experimental.properties`.
  "Enable safe optimizations" turns on `safe` options only (never vertex writing or shadow interval); "Reset" restores all defaults.
- `gui/screen/ExperimentalScreen`: grouped two-column toggles drawn with GuiUtil, tooltip, right/shift-click cycles back.
  ShaderPackScreen top row: 3 x 120 buttons (folder label shortened to "Open Pack Folder..."), bottom row also 120 wide.
- `pipeline/PassTiming`: GL timestamp queries (nestable) + nanoTime per pass, ring of 4 frames, results read only when available;
  overlay via Forge `RenderGuiEvent.Post` (hidden with F3), log every 10/30/60 s. Hooks in NewWorldRenderingPipeline phases and
  MixinDHShadowPassGuard (DH LODs / dh_shadow, nested). Nothing runs while overlay and log are off.
- Batch 2 (next): vertex writing (16-18), shadow update interval (20, with 21). Shadow interval requirements (user, 2026-11-23): on skipped frames (interval > 1) the shadow matrix uniforms (`shadowModelView`, `shadowProjection` and their inverses) must be the exact matrices that were active when the regular shadow map AND `dh_shadow` last rendered (one saved set, captured in the same frame as both). Check how `DhShadowCullingFrustum` reads `ShadowRenderer.FRUSTUM` on skipped frames, so DH's shadow-section culling never runs against a stale or half-updated frustum, and make sure DH does not clear or partly redraw the LOD shadow map on a skipped frame (`MixinDHFramebufferClear` never clears it today; DH draws LOD shadows from inside Oculus's shadow pass, so skipping the pass should skip DH too: verify, including DH generic objects).
  Item 14 skipped (no effect in our shadow pass),
  19 needs MixinExtras. The sandbox cannot compile (no Minecraft/Forge jars, Maven blocked): javac on the changed files showed only
  missing-Minecraft errors; the user's Gradle build is the real check.

## Other fixes
- `FullyBufferedMultiBufferSource.getBuffer` throws IllegalArgumentException for a null RenderType (and IllegalStateException if a
  wrapping function returns null), so crashes like Asek3/Oculus#874 name the calling mod. No mod in the user's pack passes null;
  only a regression check (normal entity rendering) is possible. Done 2026-11-23 (build 20261123-1736, Bliss and Photon):
  chests, anvils, furnaces fine, no Oculus errors.

## Lifecycle edge cases (code review 2026-11-23; Nether entry tested; testing closed)
- Dimension change: MixinMinecraft_PipelineManagement destroys all pipelines and builds a new one, so DHCompat.destroy() unbinds
  its DH overrides and a fresh DHCompat binds new ones. Only a mod rendering several dimensions at once (Immersive Portals) could leave
  a cached pipeline whose DHCompat is no longer `active` (DH's OverrideInjector.bind replaces same-priority overrides); not on 1.19.2 Forge.
- World switch: MixinMinecraft_PipelineManagement compares NamespacedId with `!=` (reference), and getCurrentDimension() makes a new
  object per call, so every level change with a non-null level rebuilds the pipeline (log 2026-11-23: "overworld => overworld" on
  rejoin). Nether entry tested with Photon: pipeline rebuilt, DH integration re-enabled, no errors. Even if the pipeline were kept: DH's GL objects we hold on
  to are game-wide: `GlDhMetaRenderer.INSTANCE` creates its framebuffer and depth texture once (`renderObjectsCreated`) and only
  recreates the depth texture on viewport resize (then calls our wrapper's addDepthAttachment). Per-world DH objects (LOD buffers, the
  GlGenericObjectRenderer and its box vertex buffer) are passed to us every frame (bindVertexBuffer, event params), never cached.
- Without DH: DHCompatBridge checks the mod list before loading DHCompat by name; DH mixins are gated by IrisDHCompatMixinPlugin;
  no com.seibel imports outside compat/dh; mods.toml has no DH dependency.
- Shader toggles: destroy() frees LOD/generic programs, VAO and depth copy; DH framebuffers are owned by RenderTargets.
- Minimize: copyOpaqueDepth skips zero-size windows.
- Overworld -> the End -> Overworld tested 2026-11-23 with the stable Spectacles build (`spectacles-mc1.19.2-1.0.0-20261123-1852`,
  Photon): pipeline rebuilt each way, DH integration re-enabled, no Spectacles/DH errors (log also shows a rejoin rebuild
  "overworld => overworld"). Together with the Nether test, every vanilla dimension change has now been tested.
- Not tested in game by the user's decision: launch without DH, shaders-off null check, minimize/restore, multiplayer. Nether with DH generating can flood the log with YUNG's "broken basalt columns" warnings (not ours).

## References (clone read-only)
- `Asek3/Oculus` branch **`1.20.1-new`** (mod 1.8.0, 2025-05): the real 1.8.0 source. Primary blueprint for the DH 3.x integration.
- `Asek3/Oculus` branch `1.20.1-DH` (1.6.15aDH): older DH 2.0.3-a design on the same 1.6.x layout. Good for structure
  (`ProgramSet` DH programs, framebuffer creation), but its DH API calls (`DhApiScreenResizeEvent` etc.) are gone in DH 3.x.
- `Asek3/Oculus` branch `1.19.2`: source of the Mekanism port. Mekalus (CurseForge) is a fork with the same fixes.
- Teqed's `1.20.1` branch is the same 1.6.9 snapshot as `1.19.2`. Not useful as a reference.

## Test checklist (the user tests on a backup world)
Results 2026-11-23 (build `...20261123-1201`): no Oculus/DH errors; caves dark with Bliss LOD shadows on; Sildur's Vibrant 1.541 has full
DH support; Complementary r5.10 + Euphoria 1.11.2 fine; LODs block Bliss's light shafts. Far LOD shadows NOT visible even with Bliss shadow
distance 128 chunks. The first-frame log then showed `distance 2048.0 blocks, depth range -2048.0 .. 2048.0 (pack values -1.0 / -1.0)`
(DH render distance 128 chunks), i.e. the same as real 1.8.0, and the dh_shadow path was compared with 1.8.0 piece by piece (matches;
only differences: no shadow-frustum culling, pack DRAWBUFFERS instead of forced 0/1, DH's worldYOffset instead of -1000). Weak far
shadows are most likely Bliss's experimental DH shadowmap mode (log-distorted shadow map over 2048 blocks). Definitive check would be
Bliss + DH on real Iris. LOD hiding tested with Sildur's Vibrant v1.41 (pre-DH pack): log says LODs are hidden, none rendered, no
garbage. Switching from 1.41 to Photon brings LODs back (no restart). Flywheel compat: no crash, shadows OK, Photon 67-81 -> 90-110 FPS; shaders off 227-258 (baseline 246), so no cost without shaders. Log clean (only the mod's own harmless "@Final field should be final" mixin warnings). BUT toggling shaders off->on in a world crashes the JVM (EXCEPTION_ACCESS_VIOLATION in `top.leonx.irisflw.vertex.block.ExtendedBlockWriterUnsafe.writeVertex`). Mod bug, not ours: its source (leon-o/iris-flw-compat branch 1.19.2, built against Oculus 1.6.9 = our base) picks its 52-byte vertex reader/writer live from `isShaderPackInUse()`; the hs_err log shows `IrisBlockVertexListUnsafe$Shaded.getX` reading past the end (page boundary) of a model buffer built in the 32-byte format while shaders were off. Workaround: don't toggle shaders in a world; switch at the title screen or restart.
One intermittent crash on Save and Quit: `NoClassDefFoundError: DhApiWorldUnloadEvent` in DH's `SharedApi.setDhWorld` (class is in the
jar). Not in Oculus code; suspects: Java 24 on Forge 43, Redirector/ModernFix interaction, DH bug. Watch for repeats.
- Loads to the main menu and into the world with DH 3.3.3 and Complementary Reimagined / Euphoria Patches.
- LODs visible with shaders on and off; no underground light leak; MekaSuit renders; Mekanism flamethrower does not crash.
- Known gaps: LOD shadows only with packs that ship `dh_shadow` (Bliss with its DH shadowmap option). DH clouds/beacons work since
  generic rendering; DH clouds are hidden when the pack sets `clouds=off`, otherwise users may want DH's cloud rendering off.
- All DH features of real 1.8.0 are backported. Remaining DH items are optional (cull LOD shadow sections outside the shadow ortho box,
  force shadow drawbuffers {0,1} like 1.8.0, Bliss on real Iris far-shadow comparison).
- All 2026-11-23 tests used Minecraft render distance 4 chunks.
- LOD shadow test: render distance 4, a tall LOD object between you and a low sun, on/off shots from the same view after chunks settle.
- Keep exactly one Oculus jar enabled. DH config resets are fine. Generation of the DH database takes hours and lowers FPS meanwhile.

## Rules
- Oculus is LGPL-3.0: distributing a jar means distributing the source of changes (this repo covers it).
- Don't commit DH jars or other people's binaries.

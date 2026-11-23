# Oculus 1.19.2 + Distant Horizons 3.x (fork of Teqed/Oculus `1.19.2`)

Handoff notes from a long chat session. Read this first.

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

## Branch and status (`stacia-dh-merge`)
Builds with JDK 17 and was tested in game (Complementary Reimagined + Euphoria Patches, DH 3.3.3). Not yet tested: Create contraptions with shaders.
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

## Past issues and fixes (for context)
- **DH 2.3.x crash:** the old `MixinDHApplyShader` redirected `getTargetFrameBuffer()` inside `onRender`; DH 2.3.3 moved it to
  `renderToFrameBuffer`, so the mixin missed its target. Fix was to list both method names. Superseded by the DH 3 integration.
- **Light leak:** Oculus's shadow pass calls `LevelRenderer.renderChunkLayer` with the sun's matrices and DH hooks that same method, so DH
  rendered LODs during the shadow pass (also halving FPS). Fix: skip DH render calls while `ShadowRenderingState.areShadowsCurrentlyBeingRendered()`.
  Now done by `MixinDHShadowPassGuard`. Side effect: LODs cast no shadows until `dh_shadow` exists.
- **Flamethrower crash:** see commit 4.
- **GitHub Actions:** the inherited workflows used the removed Brachyura build and deprecated actions; deleted.
- **JDK:** builds need JDK 17; JDK 21+ fails in repackaged commons-collections. Set `org.gradle.java.home` in the user-level
  `~/.gradle/gradle.properties` (not the repo) so every terminal uses 17.

## Build
- JDK 17 for Gradle (Minecraft 1.19.2). Loom is `dev.architectury.loom 1.2-SNAPSHOT`.
- Put `DistantHorizons-3.3.3-1.19.2-fabric-forge.jar` in `./libs/` (git-ignored). It is `compileOnly` (DH's API and internals are needed to compile the mixins and event code). This works with Loom 1.2.
- `mod_version` names the jar file; `mod_label` is the version string in mods.toml shown in the launcher ("1.8.0 # spoof for DH 3.3").
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

## What is missing (remaining backport)
Phases 1 and 2 (depth texture, uniforms, macros, `dh_terrain`/`dh_water`) are done via the PolarBearEs merge. Still to do:
1. **`dh_shadow` / LOD shadows.** Today DH is cancelled in the shadow pass. Real 1.8.0 lets DH render into the shadow map when the pack has a
   `dh_shadow` program (`shouldOverrideShadow`, `DhApiBeforeRenderEvent` cancel logic in `LodRendererEvents`); then narrow `MixinDHShadowPassGuard`.
2. **Generic rendering (DH clouds/beacons):** `IrisGenericRenderProgram`, `DhApiBeforeGenericObjectRenderEvent`,
   `DhApiBeforeGenericRenderSetupEvent`, `DHGenericTransformer`. Until then, turn DH's own cloud rendering off in DH config.
3. The four DH frustum mixins from real 1.8.0 (`AdvancedShadowCullingFrustum`, `BoxCullingFrustum`, `CullEverythingFrustum`,
   `NonCullingFrustum`), then test Proton/Bliss/Photon.
Do not port the whole Iris 1.7 refactor (package `net.irisshaders`). Port only DH pieces, adapted to `net.coderbot.iris` classes.
See `DH_BACKPORT_PLAN.md` ideas: every DH class real 1.8.0 imports exists in DH 3.3.3, so its DH-facing code is API-compatible.

## References (clone read-only)
- `Asek3/Oculus` branch **`1.20.1-new`** (mod 1.8.0, 2025-05): the real 1.8.0 source. Primary blueprint for the DH 3.x integration.
- `Asek3/Oculus` branch `1.20.1-DH` (1.6.15aDH): older DH 2.0.3-a design on the same 1.6.x layout. Good for structure
  (`ProgramSet` DH programs, framebuffer creation), but its DH API calls (`DhApiScreenResizeEvent` etc.) are gone in DH 3.x.
- `Asek3/Oculus` branch `1.19.2`: source of the Mekanism port. Mekalus (CurseForge) is a fork with the same fixes.
- Teqed's `1.20.1` branch is the same 1.6.9 snapshot as `1.19.2`. Not useful as a reference.

## Test checklist (the user tests on a backup world)
- Loads to the main menu and into the world with DH 3.3.3 and Complementary Reimagined / Euphoria Patches.
- LODs visible with shaders on and off; no underground light leak; MekaSuit renders; Mekanism flamethrower does not crash.
- Known gaps: LODs cast no shadows; DH clouds/beacons unsupported (disable DH cloud rendering).
- Keep exactly one Oculus jar enabled. DH config resets are fine. Generation of the DH database takes hours and lowers FPS meanwhile.

## Rules
- Oculus is LGPL-3.0: distributing a jar means distributing the source of changes (this repo covers it).
- Don't commit DH jars or other people's binaries.

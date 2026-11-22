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

## Commits on this branch (`dh-3.3.3-mekanism`)
1. Mekanism MekaSuit/flame fixes ported from Asek3/Oculus `1.19.2`: `ProgramId.ShadowEntities`, `ShaderKey.MEKANISM_FLAME(+_SHADOW)`,
   `LightningHandler` render types, `ShaderAccess` getters, `MixinRenderFlame`, `MixinRenderMekasuit`, `Iris.isPackInUseQuick()`.
   (Replaces a constant-pool jar patch that was used before.)
2. DH 3.3.3 hooks as source: `MixinDHCopyShader` (replaces Teqed's `MixinDHApplyShader`; DH 3.x has no `DhApplyShader`),
   `MixinDHSkipShadowPass`, mixin json, version label (`mod_label` in gradle.properties), `build.gradle` compiles against a local DH jar.

**None of this has been compiled yet.** The first job is to get `./gradlew build` working (see below) and fix whatever breaks.

## Build
- JDK 17 for Gradle (Minecraft 1.19.2). Loom is `dev.architectury.loom 1.2-SNAPSHOT`.
- Put `DistantHorizons-3.3.3-1.19.2-fabric-forge.jar` in `./libs/` (git-ignored). It is `compileOnly` because we only need DH's
  own interfaces (`IMinecraftRenderWrapper`). If Loom complains, try `modCompileOnly`, or a different DH version's API jar.
- `mod_version` names the jar file; `mod_label` is the version string in mods.toml shown in the launcher ("1.8.0 # spoof for DH 3.3").
  Keep the label starting with `1.8.0`.
- Unknown: whether the old Modrinth maven coordinates for Rubidium still resolve. Fix as needed.

## DH 3.3.3 facts (from reading the jar)
- Render flow: `ClientApi.renderLods()` / `renderDeferredLodsForShaders()` -> private `renderLodLayer(boolean)`; fade passes
  `renderFadeOpaque()` / `renderFadeTransparent()`. `LodRenderer` fires `DhApiBeforeApplyShaderRenderEvent`, then
  `metaRenderer.copyToMcTexture` -> `GlDhCopyShader_forge.renderToFrameBuffer` (copy.frag, blend disabled, no depth discard).
- DH's Forge `OculusAccessor` is constants: shader pack always in use, no shadow pass, forward-Z, "Partial Oculus support enabled".
  DH's own `MixinOculusFrameBuffer` is gated on `net.irisshaders.iris.gl.framebuffer.GlFramebuffer`, which this tree lacks, so it stays off.
- `getTargetFramebuffer()` is lowercase-b in 3.x (it was `getTargetFrameBuffer` in 2.x).
- DH vanilla fade is disabled when a shader pack is active (by DH's design) and DH TAA/AA is skipped under shader packs. Not gaps.
- DH has its own 3D cloud renderer (config `enableCloudRendering`) that clashes with a pack's clouds; users should turn it off.
- Old LOD data from DH 2.x shows as magenta/black checkers until DH regenerates it. Not a bug.

## What is missing (the backport)
Targets, in order of value:
1. **Phase 1 (Complementary/Euphoria fix):** DH colour+depth textures in the pipeline (`dhDepthTex0/1`), filled via DH API events
   (the real 1.8.0 uses `DhApiColorDepthTextureCreatedEvent`); uniforms `dhNearPlane`, `dhFarPlane`, `dhRenderDistance`;
   macros `DISTANT_HORIZONS`, `DH_BLOCK_*`. Result: clouds/fog stop drawing over LODs.
2. **Phase 2:** `ProgramSet` reads `dh_terrain`, `dh_water`, `dh_shadow`; DH framebuffers/shadow wiring. Result: pack-shaded LODs,
   no brightness seam at the chunk edge.
3. **Phase 3:** generic-object program (clouds/beacons: `IrisGenericRenderProgram`, `DhApiBeforeGenericObjectRenderEvent`,
   `DhApiBeforeGenericRenderSetupEvent`), the four DH frustum mixins, DH terrain transformer; then test Proton/Bliss/Photon.
Do not port the whole Iris 1.7 refactor (package `net.irisshaders`, GL class reorg). Port only the DH pieces, adapted to
`net.coderbot.iris` classes. Packs only see the interface (programs, samplers, uniforms, macros), not Oculus internals.
Embeddium/Rubidium does not need changes: DH renders LODs separately.

## References (clone read-only)
- `Asek3/Oculus` branch **`1.20.1-new`** (mod 1.8.0, 2025-05): the real 1.8.0 source. Primary blueprint for the DH 3.x integration.
- `Asek3/Oculus` branch `1.20.1-DH` (1.6.15aDH): older DH 2.0.3-a design on the same 1.6.x layout. Good for structure
  (`ProgramSet` DH programs, framebuffer creation), but its DH API calls (`DhApiScreenResizeEvent` etc.) are gone in DH 3.x.
- `Asek3/Oculus` branch `1.19.2`: source of the Mekanism port. Mekalus (CurseForge) is a fork with the same fixes.
- Teqed's `1.20.1` branch is the same 1.6.9 snapshot as `1.19.2`. Not useful as a reference.

## Test checklist (the user tests on a backup world)
- Loads to the main menu and into the world with DH 3.3.3 and Complementary Reimagined / Euphoria Patches.
- LODs visible with shaders on and off; no underground light leak; MekaSuit renders; Mekanism flamethrower does not crash.
- Known cosmetic: pack clouds/fog draw over LODs and the LOD-to-chunk seam is visible with shaders on (until Phase 1/2).
- Keep exactly one Oculus jar enabled. DH config resets are fine. Generation of the DH database takes hours and lowers FPS meanwhile.

## Rules
- Oculus is LGPL-3.0: distributing a jar means distributing the source of changes (this repo covers it).
- Don't commit DH jars or other people's binaries.

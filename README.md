<p align="center">
  <img src="banner.png">
</p>

# Oculus
[![](http://cf.way2muchnoise.eu/short_oculus_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/oculus)
[![](http://cf.way2muchnoise.eu/versions/Available%20for_oculus_full.svg)](https://www.curseforge.com/minecraft/mc-mods/oculus/files)

Oculus is an Unofficial Fork of ["Iris"](https://www.curseforge.com/minecraft/mc-mods/irisshaders), made to work with Forge Mod Loader.

## TL;DR:
Distant Horizons 3.3.3 LODs render with shaders on and the pack can see LOD depth (clouds and fog draw behind LODs), but this is **not feature complete**.
Tested in game with Complementary Reimagined + Euphoria Patches, Photon, Bliss and Sildur's Vibrant 1.541: LODs, MekaSuit, the Mekanism
flamethrower and Create contraptions behaved; no underground light leak.
**LOD shadows (`dh_shadow`) are a preliminary implementation**: they work in Bliss with its DH shadowmap option, but are lightly tested.
DH clouds/beacons (generic rendering) are still missing. Shader packs without DH support hide LODs while they are active.

## Disclaimer
Oculus is not and never will be compatible with Optifine!

## Dependencies
Oculus requires [Rubidium](https://www.curseforge.com/minecraft/mc-mods/rubidium) made by Asek3!

## Notes

This is a fork of Teqed's `1.19.2` branch (Oculus 1.6.9) to make **Distant Horizons 3.3.3** work with Oculus on Forge 1.19.2.
The version label says "1.8.0" only so DH's version check passes (the mod itself is based on 1.6.9).

**Status:** DH LODs render with shaders on. This branch adds `dh_terrain` and `dh_water` programs, the `dhDepthTex`/`dhDepthTex1`
samplers, the `dh*` uniforms and the `DISTANT_HORIZONS`/`DH_BLOCK_*` macros (ported from PolarBearEs's fork), so packs like
Euphoria can see LOD depth.

**LOD shadows (preliminary):** when a pack ships a `dh_shadow` program and doesn't disable it (`dhShadow.enabled`), LODs are drawn
into the shadow map using the sun's matrices. Only Bliss does this among the packs tested (turn on its "DH shadowmap support" option);
Complementary, Euphoria and Photon turn LOD shadows off themselves. LODs that cannot cast a shadow into view are skipped in the
shadow pass (about half of them when facing the sun or looking sideways, roughly 10-15% more FPS in Bliss; DH's "Disable Shadow Pass
Frustum Culling" option turns this off). With `shadowNearPlane`/`shadowFarPlane = -1` the shadow depth range covers the full DH render distance
(same as real Oculus 1.8.0); far LOD shadows in Bliss still look faint, most likely due to Bliss's experimental DH shadowmap mode.

**Shader packs without DH support** (no `dh_terrain`, e.g. Sildur's Vibrant 1.41) hide LODs while they are active; switching to a
DH-aware pack or turning shaders off brings them back without a restart.

**Oculus Flywheel Compat** (`oculus-flywheel-compat-forge1.19.2+1.0.3b`) works with this build and raised FPS with Photon by about a
third around Create contraptions. Choose your shader setting before joining a world: turning shaders on while in a world crashes the
game in that mod's vertex writer (its bug, not Oculus's).

**Known DH quirks (not Oculus bugs):** modded blocks that give off even a little light (e.g. Serene Shrubbery plants) can glow as LODs
with shaders, because DH classifies them as illuminated; add them to DH's ignored-blocks list. DH's own clouds clash with pack clouds;
turn DH cloud rendering off.

Not implemented yet: generic rendering (DH clouds/beacons; turn DH's own cloud rendering off).
Backporting the rest of the real Oculus 1.8.0 DH support is ongoing. In-game test results are in [TEST_RESULTS.md](TEST_RESULTS.md).

### Changes
- DH shader integration from PolarBearEs's `Oculus` fork (itself based on fangyu-mc's `oculus-dh3-compat`): DH programs, depth
  textures, framebuffer override, uniforms, macros, DH terrain transformer, plus mixins into DH 3.3.3 (`MixinDHApplyShader`,
  `MixinDHFramebufferClear`, `MixinDHOculusAccessor`, `MixinDHRenderPassState`, `MixinDHShadowPassGuard`).
- The shadow guard stops DH from rendering LODs during Oculus's shadow pass unless the pack has `dh_shadow`, which fixed light
  leaks and a large FPS cost. It also hides LODs for packs without `dh_terrain` (as real Oculus 1.8.0 treats them as incompatible),
  instead of drawing them into a buffer the pack uses for something else.
- `dh_shadow` support (preliminary): DH shadow program and shadow-map framebuffer, sun matrices, no clearing of the shadow map by DH,
  `dhShadow.enabled`, and `shadowNearPlane`/`shadowFarPlane` (Iris 1.7+) so distant LODs fit in the shadow map.
- Shadow-pass LOD culling: DH culls LOD sections against Oculus's shadow frustum (same rules as Oculus 1.8.0), with the frustum's
  far plane extended to DH's render distance so distant LOD shadows are kept.
- Fixes for packs written for Iris 1.7+: `shaders.properties` no longer has `IS_IRIS`/`DISTANT_HORIZONS` erased or `#ifndef` lines
  broken by text replacement (this broke Bliss, Photon and parts of Euphoria), and `dhRenderDistance`/`dhNearPlane`/`dhFarPlane`
  are visible to `shaders.properties` expressions (Photon rendered black without it).
- Mekanism fixes ported from Asek3's 1.19.2 branch and Mekalus: MekaSuit and flamethrower shaders, with a corrected flame vertex format.
- Flywheel `WorldAttached` memory leak fix (from Mekalus).

### Building
Needs JDK 17 (JDK 21+ fails to compile). Put `libs/DistantHorizons-3.3.3-1.19.2-fabric-forge.jar` in `libs/` (compile-only), then `./gradlew build`.

## Credits
Based on Teqed's Oculus 1.19.2 branch, Asek3's Oculus (its 1.20.1 Oculus 1.8.0 source is the reference for the DH backport), Askadar's DH spoof, and KostromDan's Mekalus fork. DH shader integration from PolarBearEs's Oculus fork and fangyu-mc's oculus-dh3-compat. Original Iris by coderbot, IMS212 and contributors.

## Contributors
<a href="https://github.com/Asek3/Oculus/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=Asek3/Oculus" />
</a>

## License

[LGPL-3.0 license](https://github.com/Asek3/Oculus/blob/1.16.5/LICENSE)



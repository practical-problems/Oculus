<p align="center">
  <img src="banner.png">
</p>

# Oculus
[![](http://cf.way2muchnoise.eu/short_oculus_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/oculus)
[![](http://cf.way2muchnoise.eu/versions/Available%20for_oculus_full.svg)](https://www.curseforge.com/minecraft/mc-mods/oculus/files)

Oculus is an Unofficial Fork of ["Iris"](https://www.curseforge.com/minecraft/mc-mods/irisshaders), made to work with Forge Mod Loader.

## TL;DR:
Distant Horizons 3.3.3 LODs render with shaders on, but this is **not feature complete**.
This branch merges PolarBearEs's DH shader integration (`dh_terrain`/`dh_water`, LOD depth texture, DH uniforms and macros) and is **untested**.
LOD shadows (`dh_shadow`) and DH clouds/beacons (generic rendering) are still missing.

## Disclaimer
Oculus is not and never will be compatible with Optifine!

## Dependencies
Oculus requires [Rubidium](https://www.curseforge.com/minecraft/mc-mods/rubidium) made by Asek3!

## Notes

This is a fork of Teqed's `1.19.2` branch (Oculus 1.6.9) to make **Distant Horizons 3.3.3** work with Oculus on Forge 1.19.2.
The version label says "1.8.0" only so DH's version check passes (the mod itself is based on 1.6.9).

**Status:** DH LODs render with shaders on. This branch adds `dh_terrain` and `dh_water` programs, the `dhDepthTex`/`dhDepthTex1`
samplers, the `dh*` uniforms and the `DISTANT_HORIZONS`/`DH_BLOCK_*` macros (ported from PolarBearEs's fork), so packs like
Euphoria can see LOD depth. Not implemented: `dh_shadow` (LODs don't cast shadows; DH is skipped during the shadow pass) and
generic rendering (DH clouds/beacons). Backporting the rest of the real Oculus 1.8.0 DH support is the planned next step.

### Changes
- DH shader integration from PolarBearEs's `Oculus` fork (itself based on fangyu-mc's `oculus-dh3-compat`): DH programs, depth
  textures, framebuffer override, uniforms, macros, DH terrain transformer, plus mixins into DH 3.3.3 (`MixinDHApplyShader`,
  `MixinDHFramebufferClear`, `MixinDHOculusAccessor`, `MixinDHRenderPassState`, `MixinDHShadowPassGuard`).
- The shadow guard stops DH from rendering LODs during Oculus's shadow pass, which fixed light leaks and a large FPS cost.
  (This replaces our earlier `MixinDHCopyShader` and `MixinDHSkipShadowPass`.)
- Mekanism fixes ported from Asek3's 1.19.2 branch and Mekalus: MekaSuit and flamethrower shaders, with a corrected flame vertex format.
- Flywheel `WorldAttached` memory leak fix (from Mekalus).

### Building
Needs JDK 17 (JDK 21+ fails to compile). Put `libs/DistantHorizons-3.3.3-1.19.2-fabric-forge.jar` in `libs/` (compile-only), then `./gradlew build`.

## Credits
Based on Teqed's Oculus 1.19.2 branch, Asek3's Oculus, Askadar's DH spoof, and KostromDan's Mekalus fork. DH shader integration from PolarBearEs's Oculus fork and fangyu-mc's oculus-dh3-compat. Original Iris by coderbot, IMS212 and contributors.

## Contributors
<a href="https://github.com/Asek3/Oculus/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=Asek3/Oculus" />
</a>

## License

[LGPL-3.0 license](https://github.com/Asek3/Oculus/blob/1.16.5/LICENSE)



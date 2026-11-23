<p align="center">
  <img src="banner.png">
</p>

# Oculus
[![](http://cf.way2muchnoise.eu/short_oculus_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/oculus)
[![](http://cf.way2muchnoise.eu/versions/Available%20for_oculus_full.svg)](https://www.curseforge.com/minecraft/mc-mods/oculus/files)

Oculus is an Unofficial Fork of ["Iris"](https://www.curseforge.com/minecraft/mc-mods/irisshaders), made to work with Forge Mod Loader.

## Disclaimer
Oculus is not and never will be compatible with Optifine!

## Dependencies
Oculus requires [Rubidium](https://www.curseforge.com/minecraft/mc-mods/rubidium) made by Asek3!

## Notes

This is a fork of Teqed's `1.19.2` branch (Oculus 1.6.9) to make **Distant Horizons 3.3.3** work with Oculus on Forge 1.19.2.
The version label says "1.8.0" only so DH's version check passes (the mod itself is based on 1.6.9).

**Status:** DH LODs render and composite with shaders on. DH-aware shader features are NOT implemented yet: `dh_terrain`,
`dh_water`, `dh_shadow`, the LOD depth texture (`dhDepthTex`), the DH uniforms and macros. Because of that, packs cannot see
LOD depth (clouds and fog can draw over LODs), LODs don't cast shadows, and there can be a brightness seam where LODs meet
chunks. Backporting the real Oculus 1.8.0 DH support is the planned next step.

Only v1/legacy shader packs work currently, since v2 packs need the 1.8.0 features above.

### Changes
- `MixinDHCopyShader`: redirects DH's copy pass into the shader pack's framebuffer (DH 3.x; replaces the old `MixinDHApplyShader`).
- `MixinDHSkipShadowPass`: stops DH from rendering its LODs during Oculus's shadow pass, which fixed light leaks and a large FPS cost.
- Mekanism fixes ported from Asek3's 1.19.2 branch and Mekalus: MekaSuit and flamethrower shaders, with a corrected flame vertex format.
- Flywheel `WorldAttached` memory leak fix (from Mekalus).

### Building
Needs JDK 17 (JDK 21+ fails to compile). Put `libs/DistantHorizons-3.3.3-1.19.2-fabric-forge.jar` in `libs/` (compile-only), then `./gradlew build`.

## Credits
Based on Teqed's Oculus 1.19.2 branch, Asek3's Oculus, Askadar's DH spoof, and KostromDan's Mekalus fork. Original Iris by coderbot, IMS212 and contributors.

## Contributors
<a href="https://github.com/Asek3/Oculus/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=Asek3/Oculus" />
</a>

## License

[LGPL-3.0 license](https://github.com/Asek3/Oculus/blob/1.16.5/LICENSE)



<p align="center">
  <img src="banner.png">
</p>

# Spectacles
Spectacles is an unofficial fork of [Oculus](https://github.com/Asek3/Oculus) (the Forge port of ["Iris"](https://www.curseforge.com/minecraft/mc-mods/irisshaders))
for Forge 1.19.2, focused on Distant Horizons. It was called Oculus until 2026-11-23; please don't report its issues to Oculus or Iris.

Only the names people see changed (display name, jar file name, log/F3 prefix). The mod ID is still `oculus`, the Java packages
are still `net.coderbot.iris`, and the version label still starts with `1.8.0`, so Distant Horizons, Oculus Flywheel Compat and
other mods that look for Oculus keep working. Existing settings files (`config/oculus*.properties`) are kept.

## TL;DR:
Spectacles is Oculus 1.6.9 for Forge 1.19.2 with the Distant Horizons 3.3.3 support of Oculus 1.8.0 backported: LODs with shaders, LOD shadows,
DH beacon beams and clouds. Tested with Complementary + Euphoria, Photon, Bliss and Sildur's Vibrant ([TEST_RESULTS.md](TEST_RESULTS.md)).
Dev branch only: optional optimizations from Emberlens and a CPU/GPU timing overlay on a new Experimental page (all off by default, not yet tested).

## Disclaimer
Like Oculus, Spectacles is not compatible with OptiFine.

## Dependencies
Spectacles requires [Rubidium](https://www.curseforge.com/minecraft/mc-mods/rubidium) made by Asek3, or its maintained fork
[Embeddium](https://www.curseforge.com/minecraft/mc-mods/embeddium). All testing of this fork used Embeddium 0.3.18.1 for 1.19.2.

## Notes

This is a fork of Teqed's `1.19.2` branch (Oculus 1.6.9) to make **Distant Horizons 3.3.3** work with shaders on Forge 1.19.2.
**Versions:** Spectacles is based on Oculus 1.6.9, with the Distant Horizons features of Oculus 1.8.0 ported over. The rest of
1.8.0 (the Iris 1.7 rewrite, e.g. tessellation shaders) is not included; single features are ported when a pack needs them. The
Spectacles has its own version numbers, starting at 1.0.0 (jar `spectacles-mc1.19.2-1.0.0.jar`), because it no longer follows
Oculus's numbering; the Oculus code underneath is 1.6.9. The version label inside the mod says "1.8.0" only so DH's version check passes.

**Status:** DH LODs render with shaders on. This branch adds `dh_terrain` and `dh_water` programs, the `dhDepthTex`/`dhDepthTex1`
samplers, the `dh*` uniforms and the `DISTANT_HORIZONS`/`DH_BLOCK_*` macros (ported from PolarBearEs's fork), so packs like
Euphoria can see LOD depth.

**LOD shadows:** when a pack ships a `dh_shadow` program and doesn't disable it (`dhShadow.enabled`), LODs are drawn
into the shadow map using the sun's matrices. Only Bliss does this among the packs tested (turn on its "DH shadowmap support" option);
Complementary, Euphoria and Photon turn LOD shadows off themselves. LODs that cannot cast a shadow into view are skipped in the
shadow pass (about half of them when facing the sun or looking sideways, roughly 10-15% more FPS in Bliss; DH's "Disable Shadow Pass
Frustum Culling" option turns this off). With `shadowNearPlane`/`shadowFarPlane = -1` the shadow depth range covers the full DH render distance
(same as real Oculus 1.8.0). Tested with Bliss at shadow map resolution 4096, DH 128 chunks and Minecraft render distance 4: no shadows lost with culling on,
nearby and player shadows unchanged, and pack switches and DH render distance changes work in-world.

**Shader pack options in program switches:** `program.X.enabled` values that combine options with `&&`, `||`, `!` or parentheses
(Bliss uses `TRANSLUCENT_ENTITIES && IS_IRIS`) are now evaluated instead of being read as one unknown option that always counts as
on. Names resolve to pack options, then to defined macros such as `IS_IRIS`; anything unreadable keeps the old behaviour (on).
Real Oculus 1.8.0 does not do this.

**Shader packs without DH support** (no `dh_terrain`, e.g. Sildur's Vibrant 1.41) hide LODs while they are active; switching to a
DH-aware pack or turning shaders off brings them back without a restart.

**Oculus Flywheel Compat** (`oculus-flywheel-compat-forge1.19.2+1.0.3b`) works with this build and raised FPS with Photon by about a
third around Create contraptions. Choose your shader setting before joining a world (see Known quirks below).

**DH generic objects** (beacon beams beyond your render distance, DH's own clouds, and boxes other mods add through DH's API) are
drawn through the pack's `dh_generic` program, or its `dh_terrain` program if it has none (as in real Oculus 1.8.0), so they are lit,
fogged and hidden behind LOD terrain like the LODs themselves, and glowing ones get the pack's glow (beacon beams are
emissive at night in Bliss and get Photon's bloom; DH clouds are lit and fogged by Complementary). They are left out of the LOD shadow pass. A pack's own `dh_generic` program was tested with a modified Bliss (no released pack
ships one yet); like 1.8.0, it uses the pack's `blend.dh_generic` setting. Packs that discard LOD pixels
near the player (Bliss's DH overdraw prevention) cut the same zone out of the beam; Minecraft's own beam covers it once the chunk is loaded.

The DH features of real Oculus 1.8.0 are now all backported. In-game test results are in [TEST_RESULTS.md](TEST_RESULTS.md).

**Experimental page (dev branch, not yet tested in game):** the shader pack screen has an "Experimental..." button. It opens
global options (saved in `config/oculus-experimental.properties`, not per pack) for small CPU optimizations ported from the
[Emberlens](https://github.com/shuttheirprayer/Emberlens) fork. Everything is off by default, and with everything off Oculus
runs the same code as before. Each option's tooltip gives what it does, the expected benefit, a risk level and when it applies.
"Enable safe optimizations" turns on the low-risk ones; "Reset to defaults" turns everything off again. The same page has a
**timing overlay** and **timing log**: CPU and GPU milliseconds per render pass (shadow, DH LODs, gbuffers, deferred,
composite, final), for comparing options. No Emberbium needed. See [BACKPORT_CANDIDATES.md](BACKPORT_CANDIDATES.md).

### Known quirks (not Oculus bugs)
These come from other mods or shader packs; they are listed so they are not reported as Oculus bugs.

- **Oculus Flywheel Compat: turning shaders on inside a world crashes the game** (a native access violation). The mod switches its
  unsafe vertex reader and writer to a 52-byte vertex format as soon as shaders are on, but Create models already built with shaders
  off are still in the 32-byte format, so the reader runs past the end of that data. Workaround: choose your shader setting before
  joining a world, or switch at the title screen.
- **DH: faintly glowing modded blocks glow as LODs with shaders** (e.g. all 26 Serene Shrubbery plants, which give off light level 1,
  and AE2's certus quartz buds and clusters). DH classifies any block that gives off light as illuminated. Workaround: add them to
  DH's Advanced > Graphics > Culling > "Ignored Block CSV" (exact `modid:block` IDs separated by commas; no wildcards), e.g.
  `ae2:small_quartz_bud,ae2:medium_quartz_bud,ae2:large_quartz_bud,ae2:quartz_cluster`. They then no longer appear in LODs;
  nearby chunks are unchanged.
- **DH clouds show only with some packs.** Oculus hides DH's clouds when the pack turns clouds off (Bliss, Photon, Complementary with
  its own clouds). With Complementary's cloud style set to vanilla, DH's clouds replace vanilla's and are drawn through the pack.
  If they clash with a pack's own clouds, turn off DH's cloud rendering in DH's settings. Far DH clouds can cover distant mountains
  that rise above cloud height; the clouds are closer, so that is correct.
- **DH: a beacon or other blocks you just placed do not show in the LOD yet.** DH updates a changed chunk's LOD when the chunk is saved
  or unloaded, which can take a while while DH is still generating. Moving far away or rejoining the world updates it.
- **DH: LOD data from DH 2.x shows as magenta/black checkers** until DH regenerates it.
- **DH: an occasional crash on Save and Quit** (`NoClassDefFoundError: DhApiWorldUnloadEvent` in DH's own code). Seen once, not
  reproduced. If it repeats, try Java 17 or 21.
- **DH: `Unable to convert config value ... for config [mapTest]`** is logged each time DH saves its settings. Harmless.
- **Bliss: far LOD shadows look faint** with its DH shadowmap option, most likely because that mode is experimental (one shadow map
  stretched over the whole LOD range).
- **Bliss: with a small shadow distance, light shafts seem to stream out of distant LODs.** Areas outside the shadow map count as
  lit. Set Bliss's shadow distance to about your DH render distance.
- **Bliss: the "IRIS REQUIRED" label** next to its DH options is fixed text in Bliss's menu, not a detection failure.
- **Photon: the upper part of a DH beacon beam turns dark with cloud shadows on.** Photon's clouds darken the beam from about the
  cloud layer upward; raising Photon's cloud altitude moves that point up with them. Its own behaviour with DH objects.
- **Photon: DH beacon beams glow much more strongly than vanilla beams**, with a large halo in rain. DH gives beams its
  "illuminated" material (the one used for glowstone, lanterns and lava in LODs), which Photon renders as a full-strength light
  source; Minecraft's own beam goes through Photon's dedicated beacon-beam program. Real Oculus 1.8.0 passes the same data.
  Photon's bloom, emission or fog settings can soften it.
- **Photon: `#endif without #if`** is logged on every load. It comes from Photon's own files and does not affect rendering.

### Changes
- DH shader integration from PolarBearEs's `Oculus` fork (itself based on fangyu-mc's `oculus-dh3-compat`): DH programs, depth
  textures, framebuffer override, uniforms, macros, DH terrain transformer, plus mixins into DH 3.3.3 (`MixinDHApplyShader`,
  `MixinDHFramebufferClear`, `MixinDHOculusAccessor`, `MixinDHRenderPassState`, `MixinDHShadowPassGuard`).
- The shadow guard stops DH from rendering LODs during Oculus's shadow pass unless the pack has `dh_shadow`, which fixed light
  leaks and a large FPS cost. It also hides LODs for packs without `dh_terrain` (as real Oculus 1.8.0 treats them as incompatible),
  instead of drawing them into a buffer the pack uses for something else.
- `dh_shadow` support: DH shadow program and shadow-map framebuffer, sun matrices, no clearing of the shadow map by DH,
  `dhShadow.enabled`, and `shadowNearPlane`/`shadowFarPlane` (Iris 1.7+) so distant LODs fit in the shadow map.
- Shadow-pass LOD culling (only while a pack's `dh_shadow` is active): DH culls LOD sections against Oculus's shadow frustum, with the
  same rules as Oculus 1.8.0 but in one class instead of four mixins. The frustum's far plane is extended to DH's render distance, read
  live from DH's config, so distant LOD shadows are kept; unlike 1.8.0 the vanilla near plane is kept, so the frustum is never
  smaller than before and nearby shadows cannot be culled.
- DH generic rendering (beacon beams, DH clouds, API boxes), ported from Oculus 1.8.0: the pack's `dh_generic` program (or `dh_terrain`),
  a shader transformer for DH's instanced boxes, the LOD matrices and lightmap, and DH's generic object shader override. If the program
  cannot be built, DH keeps its own shader. Unlike 1.8.0, generic objects are skipped in the LOD shadow pass.
- If a pack's DH generic program cannot be built, DH's beams and clouds are hidden while that pack is active (before, DH drew them
  with its own shader into the pack's buffers, which can show garbage colours).
- Entity batching rejects a `null` RenderType right away, so a crash caused by another mod names that mod instead of Oculus
  (same code as Asek3/Oculus#874 on 1.20.1).
- Fixes for packs written for Iris 1.7+: `shaders.properties` no longer has `IS_IRIS`/`DISTANT_HORIZONS` erased or `#ifndef` lines
  broken by text replacement (this broke Bliss, Photon and parts of Euphoria), and `dhRenderDistance`/`dhNearPlane`/`dhFarPlane`
  are visible to `shaders.properties` expressions (Photon rendered black without it).
- `program.X.enabled` values are evaluated as boolean expressions (`&&`, `||`, `!`, parentheses, `defined()`), so programs a pack
  switches with combined options (Bliss's `TRANSLUCENT_ENTITIES && IS_IRIS`) follow those options.
- DH's SSAO and fog overrides under shader packs now pass Oculus as the caller, so DH no longer logs a deprecated-API warning.
- Mekanism fixes ported from Asek3's 1.19.2 branch and Mekalus: MekaSuit and flamethrower shaders, with a corrected flame vertex format.
- Experimental page with toggleable Emberlens optimizations (all off by default) and CPU/GPU pass timing. The shader pack screen's
  top row is now three buttons: Open Pack Folder, Shader Pack Settings, Experimental.
- `RenderTargets` re-attaches depth when Minecraft's depth texture id changes, not only its version (fix from Emberlens).
- Memory leak fix for Flywheel's `WorldAttached` (Create's renderer; from Mekalus). Unrelated to the Oculus Flywheel Compat mod.

### Building
Needs JDK 17 (JDK 21+ fails to compile). Put `libs/DistantHorizons-3.3.3-1.19.2-fabric-forge.jar` in `libs/` (compile-only), then `./gradlew build`.

## Credits
- **Teqed**: the [Oculus `1.19.2` branch](https://github.com/Teqed/Oculus/tree/1.19.2) this fork is based on.
- **Asek3**: [Oculus](https://github.com/Asek3/Oculus); its 1.20.1 Oculus 1.8.0 source is the reference for the DH backport, and its 1.19.2 branch the source of the Mekanism fixes.
- **Askadar**: the original DH version spoof.
- **KostromDan**: the [Mekalus](https://github.com/KostromDan/Mekalus) fork (Mekanism and Flywheel fixes).
- **PolarBearEs** and **fangyu-mc**: DH shader integration ([PolarBearEs's Oculus fork](https://github.com/PolarBearEs/Oculus/tree/1.19.2),
  built on fangyu-mc's oculus-dh3-compat, whose repository is no longer online).
- **shuttheirprayer**: [Emberlens](https://github.com/shuttheirprayer/Emberlens) (1.20.1 Oculus fork, LGPL-3.0-only), source of the
  experimental optimizations, the `RenderTargets` depth fix and the idea for the pass timing tool. Ported code is marked
  "(Emberlens)" in comments; [BACKPORT_CANDIDATES.md](BACKPORT_CANDIDATES.md) lists each change with its Emberlens commit.
  Nothing from Emberlens's companion Embeddium fork (Emberbium) is used or required.
- **coderbot, IMS212 and contributors**: the original Iris.

## Contributors
<a href="https://github.com/Asek3/Oculus/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=Asek3/Oculus" />
</a>

## License

[LGPL-3.0 license](https://github.com/Asek3/Oculus/blob/1.16.5/LICENSE)

This fork, including the code ported from Emberlens (LGPL-3.0-only) and the other forks credited above, is distributed under the
same LGPL-3.0 license. This repository is the source for any jar built from it.



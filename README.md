<p align="center">
  <img src="banner.png">
</p>

# Spectacles
*An Oculus fork that got Distant Horizons 3.3.3 working on Forge 1.19.2 and got slightly carried away.*

Spectacles is an unofficial fork of [Oculus](https://github.com/Asek3/Oculus) (the Forge port of ["Iris"](https://www.curseforge.com/minecraft/mc-mods/irisshaders))
for Forge 1.19.2, focused on Distant Horizons. It was called Oculus until 2026-11-23; please don't report its issues to Oculus or Iris.

## TL;DR:
Spectacles is Oculus 1.6.9 for Forge 1.19.2 with the Distant Horizons 3.3.3 support of Oculus 1.8.0 backported: LODs with shaders, LOD shadows,
DH beacon beams and clouds. Tested with Complementary + Euphoria, Photon, Bliss and Sildur's Vibrant ([TEST_RESULTS.md](TEST_RESULTS.md)).
Work in progress (dev branch only, not in this release): optional optimizations from Emberlens and a CPU/GPU timing overlay.

## Requirements and installation
- **Minecraft 1.19.2 with Forge.**
- **[Embeddium](https://www.curseforge.com/minecraft/mc-mods/embeddium) 0.3.18.1 or newer** (or
  [Rubidium](https://www.curseforge.com/minecraft/mc-mods/rubidium), which Embeddium is a maintained fork of). All testing used Embeddium 0.3.18.1.
- **[Distant Horizons](https://modrinth.com/mod/distanthorizons) 3.3.3**, the version Spectacles is built and tested against.
- Put `spectacles-mc1.19.2-<version>.jar` in your `mods` folder and remove any other Oculus jar: keep exactly one.
- Not compatible with OptiFine (like Oculus).
- Optional, for Create: [Oculus Flywheel Compat](https://www.curseforge.com/minecraft/mc-mods/iris-flywheel-compat)
  (`1.0.3b` tested). Choose your shader setting before joining a world (see Known quirks).

## Shader pack support

What was tested with Distant Horizons 3.3.3 (details in [TEST_RESULTS.md](TEST_RESULTS.md)):

| Pack | LOD terrain/water | LOD shadows | DH beacons/clouds | Notes |
|---|---|---|---|---|
| Bliss v2.1.2 | Yes | Yes, with Bliss's "DH shadowmap" option | Beams yes; DH clouds hidden (Bliss draws its own) | Far LOD shadows are faint (Bliss's DH shadowmap is experimental). Beam has a gap within ~32 blocks of you (Bliss's LOD overdraw cutoff) |
| Complementary Reimagined r5.10 + Euphoria Patches 1.11.2 | Yes | No (not supported by the pack) | Beams yes; DH clouds only with the cloud style set to vanilla | |
| Photon v1.3b | Yes | No (the pack turns them off) | Beams yes; DH clouds hidden (Photon draws its own) | LOD beams glow much brighter than vanilla beams (Photon treats them as full light sources) |
| Sildur's Vibrant 1.541 | Yes | Not tested | Not tested | |
| Sildur's Vibrant 1.41 | Hidden | No | No | Made before DH support: LODs are hidden while it is active instead of drawn wrong |

Packs without DH support get the same treatment as Sildur's 1.41: switching to a DH-aware pack or turning shaders off brings LODs
back without a restart. With shaders off, LODs render as normal.

## Notes
- **What Spectacles is:** Oculus 1.6.9 (Teqed's `1.19.2` branch) with the Distant Horizons features of Oculus 1.8.0 ported over.
  The rest of 1.8.0 (the Iris 1.7 rewrite, e.g. tessellation shaders) is not included; single features are ported when a pack
  needs them. Spectacles has its own version numbers, starting at 1.0.0.
- **Why the version says `1.8.0`:** Distant Horizons only accepts Oculus 1.8.0 or newer, so the version label inside the mod
  starts with `1.8.0`. The mod ID (`oculus`), the Java packages and the settings files (`config/oculus*.properties`) are also
  unchanged, so DH, Oculus Flywheel Compat and other mods that look for Oculus keep working.
- **Dev branch:** `dh-3.3.3-mekanism-dev` carries the work in progress for 1.1.0: optional CPU optimizations from Emberlens and a
  CPU/GPU timing overlay, all off by default. It is not part of this release; see [BACKPORT_CANDIDATES.md](BACKPORT_CANDIDATES.md).

## Known quirks (not Spectacles bugs)
These come from other mods or shader packs; they are listed so they are not reported as Spectacles bugs.

- **Oculus Flywheel Compat**
  - **Turning shaders on inside a world crashes the game** (a native access violation). The mod switches its unsafe vertex reader
    and writer to a 52-byte vertex format as soon as shaders are on, but Create models already built with shaders off are still in
    the 32-byte format, so the reader runs past the end of that data. Workaround: choose your shader setting before joining a
    world, or switch at the title screen.
- **Distant Horizons**
  - **Faintly glowing modded blocks glow as LODs with shaders** (e.g. all 26 Serene Shrubbery plants, which give off light level 1,
    and AE2's certus quartz buds and clusters). DH classifies any block that gives off light as illuminated. Workaround: add them to
    DH's Advanced > Graphics > Culling > "Ignored Block CSV" (exact `modid:block` IDs separated by commas; no wildcards), e.g.
    `ae2:small_quartz_bud,ae2:medium_quartz_bud,ae2:large_quartz_bud,ae2:quartz_cluster`. They then no longer appear in LODs;
    nearby chunks are unchanged.
  - **DH clouds show only with some packs.** Spectacles hides DH's clouds when the pack turns clouds off (Bliss, Photon,
    Complementary with its own clouds). With Complementary's cloud style set to vanilla, DH's clouds replace vanilla's and are drawn
    through the pack. If they clash with a pack's own clouds, turn off DH's cloud rendering in DH's settings. Far DH clouds can cover
    distant mountains that rise above cloud height; the clouds are closer, so that is correct.
  - **A beacon or other blocks you just placed do not show in the LOD yet.** DH updates a changed chunk's LOD when the chunk is
    saved or unloaded, which can take a while while DH is still generating. Moving far away or rejoining the world updates it.
  - **LOD data from DH 2.x shows as magenta/black checkers** until DH regenerates it.
  - **An occasional crash on Save and Quit** (`NoClassDefFoundError: DhApiWorldUnloadEvent` in DH's own code). Seen once, not
    reproduced. If it repeats, try Java 17 or 21.
  - **`Unable to convert config value ... for config [mapTest]`** is logged each time DH saves its settings. Harmless.
- **Bliss**
  - **Far LOD shadows look faint** with its DH shadowmap option, most likely because that mode is experimental (one shadow map
    stretched over the whole LOD range).
  - **With a small shadow distance, light shafts seem to stream out of distant LODs.** Areas outside the shadow map count as lit.
    Set Bliss's shadow distance to about your DH render distance.
  - **The "IRIS REQUIRED" label** next to its DH options is fixed text in Bliss's menu, not a detection failure.
- **Photon**
  - **The upper part of a DH beacon beam turns dark with cloud shadows on.** Photon's clouds darken the beam from about the cloud
    layer upward; raising Photon's cloud altitude moves that point up with them. Its own behaviour with DH objects.
  - **DH beacon beams glow much more strongly than vanilla beams**, with a large halo in rain. DH gives beams its "illuminated"
    material (the one used for glowstone, lanterns and lava in LODs), which Photon renders as a full-strength light source;
    Minecraft's own beam goes through Photon's dedicated beacon-beam program. Real Oculus 1.8.0 passes the same data. Photon's
    bloom, emission or fog settings can soften it.
  - **`#endif without #if`** is logged on every load. It comes from Photon's own files and does not affect rendering.

## Changes
Compared with Teqed's Oculus 1.6.9 `1.19.2` branch. All DH features of real Oculus 1.8.0 are backported.

- DH shader integration from PolarBearEs's `Oculus` fork (itself based on fangyu-mc's `oculus-dh3-compat`): `dh_terrain` and
  `dh_water` programs, the `dhDepthTex`/`dhDepthTex1` samplers, the `dh*` uniforms, the `DISTANT_HORIZONS`/`DH_BLOCK_*` macros,
  framebuffer override and DH terrain transformer, so packs like Euphoria can see LOD depth. Mixins into DH 3.3.3:
  `MixinDHApplyShader`, `MixinDHFramebufferClear`, `MixinDHOculusAccessor`, `MixinDHRenderPassState`, `MixinDHShadowPassGuard`.
- The shadow guard stops DH from rendering LODs during the shadow pass unless the pack has `dh_shadow`, which fixed light leaks and
  a large FPS cost. It also hides LODs for packs without `dh_terrain` (as real Oculus 1.8.0 treats them as incompatible), instead of
  drawing them into a buffer the pack uses for something else.
- LOD shadows (`dh_shadow`), with shadow-pass culling of LODs that cannot cast a shadow into view.
- DH generic rendering (beacon beams, DH clouds, API boxes), ported from Oculus 1.8.0.
- `program.X.enabled` values are evaluated as boolean expressions.
- Fixes for packs written for Iris 1.7+: `shaders.properties` no longer has `IS_IRIS`/`DISTANT_HORIZONS` erased or `#ifndef` lines
  broken by text replacement (this broke Bliss, Photon and parts of Euphoria), and `dhRenderDistance`/`dhNearPlane`/`dhFarPlane`
  are visible to `shaders.properties` expressions (Photon rendered black without it).
- Entity batching rejects a `null` RenderType right away, so a crash caused by another mod names that mod instead of Spectacles
  (same code as Asek3/Oculus#874 on 1.20.1).
- DH's SSAO and fog overrides under shader packs now pass Spectacles as the caller, so DH no longer logs a deprecated-API warning.
- Mekanism fixes ported from Asek3's 1.19.2 branch and Mekalus: MekaSuit and flamethrower shaders, with a corrected flame vertex format.
- Memory leak fix for Flywheel's `WorldAttached` (Create's renderer; from Mekalus). Unrelated to the Oculus Flywheel Compat mod.
- Renamed to Spectacles (display name, jar name, log/F3 prefix); mod ID, packages and the 1.8.0 version label are unchanged.
- Work in progress on the dev branch only: the Experimental page (toggleable Emberlens optimizations, CPU/GPU pass timing) and the
  `RenderTargets` depth fix from Emberlens.

<details>
<summary><b>Technical details</b> (for pack authors and the curious)</summary>

**LOD shadows.** When a pack ships a `dh_shadow` program and doesn't disable it (`dhShadow.enabled`), LODs are drawn into the
shadow map using the sun's matrices, and DH never clears the shadow map. Only Bliss does this among the packs tested (its "DH
shadowmap support" option); Complementary, Euphoria and Photon turn LOD shadows off themselves. `shadowNearPlane`/`shadowFarPlane`
(Iris 1.7+) are supported; with `-1` the shadow depth range covers the full DH render distance (same as real Oculus 1.8.0), so
distant LODs fit in the shadow map.

**Shadow-pass LOD culling** (only while a pack's `dh_shadow` is active). DH culls LOD sections against the shadow frustum, with the
same rules as Oculus 1.8.0 but in one class instead of four mixins. The frustum's far plane is extended to DH's render distance
(`(renderDistance + 512) * sqrt(2)`, read live from DH's config), so distant LOD shadows are kept; unlike 1.8.0 the vanilla near
plane is kept, so the frustum is never smaller than before and nearby shadows cannot be culled. About half the LODs are skipped when
facing the sun or looking sideways, roughly 10-15% more FPS in Bliss; facing away from a low sun nothing is culled, which is correct.
DH's "Disable Shadow Pass Frustum Culling" option turns it off. Tested with Bliss at shadow map resolution 4096, DH 128 chunks and
Minecraft render distance 4: no shadows lost, nearby and player shadows unchanged, pack switches and DH render distance changes work
in-world.

**`program.X.enabled` expressions.** Values that combine options with `&&`, `||`, `!`, parentheses or `defined()` (Bliss uses
`TRANSLUCENT_ENTITIES && IS_IRIS`) are evaluated instead of being read as one unknown option that always counts as on. Names resolve
to boolean pack options, then to defined macros such as `IS_IRIS`; anything unreadable keeps the old behaviour (on), with a warning.
Real Oculus 1.8.0 does not do this.

**DH generic objects** (beacon beams beyond your render distance, DH's own clouds, and boxes other mods add through DH's API). They
are drawn through the pack's `dh_generic` program, or its `dh_terrain` program if it has none (as in real Oculus 1.8.0), with a
shader transformer for DH's instanced boxes and the LOD matrices and lightmap, so they are lit, fogged and hidden behind LOD terrain
like the LODs themselves, and glowing ones get the pack's glow (beacon beams are emissive at night in Bliss and get Photon's bloom;
DH clouds are lit and fogged by Complementary). Unlike 1.8.0 they are left out of the LOD shadow pass. A pack's own `dh_generic`
program was tested with a modified Bliss (no released pack ships one yet); like 1.8.0, it uses the pack's `blend.dh_generic`
setting. If the program cannot be built, DH's beams and clouds are hidden while that pack is active (before, DH drew them with its
own shader into the pack's buffers, which can show garbage colours). Packs that discard LOD pixels near the player (Bliss's DH
overdraw prevention) cut the same zone out of the beam; Minecraft's own beam covers it once the chunk is loaded.

**Oculus Flywheel Compat** raised FPS with Photon by about a third around Create contraptions in testing.

</details>

### Building
Needs JDK 17 (JDK 21+ fails to compile). Put `libs/DistantHorizons-3.3.3-1.19.2-fabric-forge.jar` in `libs/` (compile-only), then `./gradlew build`.

## Credits
- **Teqed**: the [Oculus `1.19.2` branch](https://github.com/Teqed/Oculus/tree/1.19.2) this fork is based on.
- **Asek3**: [Oculus](https://github.com/Asek3/Oculus); its 1.20.1 Oculus 1.8.0 source is the reference for the DH backport, and its 1.19.2 branch the source of the Mekanism fixes.
- **Askadar**: the original DH version spoof.
- **KostromDan**: the [Mekalus](https://github.com/KostromDan/Mekalus) fork (Mekanism and Flywheel fixes).
- **PolarBearEs** and **fangyu-mc**: DH shader integration ([PolarBearEs's Oculus fork](https://github.com/PolarBearEs/Oculus/tree/1.19.2),
  built on fangyu-mc's oculus-dh3-compat, whose repository is no longer online).
- **shuttheirprayer** (work in progress, dev branch only): [Emberlens](https://github.com/shuttheirprayer/Emberlens) (1.20.1 Oculus fork, LGPL-3.0-only), source of the
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

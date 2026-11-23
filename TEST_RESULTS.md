# Test results

In-game tests of this fork (Oculus 1.6.9 base + Distant Horizons 3.3.3 backport), run on a backup copy of a modded world.

## Test setup

| Item | Value |
|---|---|
| Date | 2026-11-23 |
| Build | `1.8.0 # spoof for DH 3.3 20261123-1401` (branch `dh-3.3.3-mekanism`) |
| Minecraft / Forge | 1.19.2, Forge 43, Embeddium 0.3.18.1 |
| Minecraft render distance | 4 chunks (everything beyond is drawn by DH as LODs) |
| Distant Horizons | 3.3.3, LOD render distance 128 chunks |
| Shader pack | Bliss v2.1.2 with its DH shadowmap option on, shadow map resolution 4096 (unless noted) |
| Java | Temurin 24.0.2, ZGC, 12 GB heap |
| Hardware | AMD Ryzen 9 8940HX, NVIDIA RTX 5070 Ti Laptop GPU (driver 581.83), 2560x1411 |
| Notes | DH was generating LODs in the background during all tests, so FPS varies by a few frames. Oculus Flywheel Compat disabled. |

"Shadow sections" is DH's F3 line `Shadow VBO Render Count: [drawn/total]`. The culling A/B switch is DH's own option
"Disable Shadow Pass Frustum Culling" (Advanced > Graphics > Culling).

## Shadow-pass LOD culling

| Test | Culling on | Culling off | Result |
|---|---|---|---|
| Facing the sun, morning (time 1000), shadow distance 128 chunks | 82/181, 140-149 FPS | 181/181, 118-133 FPS | Pass: about half skipped, ~10-15% faster |
| Facing the sun, evening (time 11000), hill | 91/175, 150-152 FPS | 175/175, 132-137 FPS | Pass: ~12% faster |
| Facing away from a low sun, rooftop | 184/184, 108-117 FPS | 184/184, 106-117 FPS | Pass: nothing skipped (correct: every LOD can shadow something in view) |
| Far LOD shadows, mountain view (time 1000) | 78/184, 146-149 FPS | 184/184, 131-132 FPS | Pass: screenshots pixel-identical, no far shadows lost |

## Bliss shadow distance (culling on, hill, facing the sun)

| Bliss shadow distance | Shadow sections | FPS | Result |
|---|---|---|---|
| 128 chunks (2048 blocks) | 91/175 | 135-153 | Pass |
| 64 chunks (1024 blocks) | 91/175 | 139-150 | Pass |
| 32 chunks (512 blocks) | 91/175 | 143-148 | Pass |
| 8 chunks (128 blocks) | 91/175 | 148-149 | Pass: light shafts seem to stream out of distant LODs (outside the shadow map counts as lit); expected Bliss behaviour |

The count does not depend on shadow distance: like Oculus 1.8.0, culling does not skip LODs outside the shadow map's area.
At the mountain view, 64 and 128 chunks gave identical images.

## Shadow correctness

| Test | Result |
|---|---|
| Nearby shadows at noon, Bliss DH shadowmap on vs off (shadow distance 64 chunks) | Pass: same tree, lamp post and player shadows; softer with it on (one map over 2048 blocks) |
| Nearby shadows, low sun behind the player (time 11000) | Pass: same shadows; 139 FPS off vs 111 FPS on (cost of LOD shadows in this direction) |
| Fly ~25 blocks up so the area below is drawn as LODs, DH shadowmap on vs off | Pass: only added shadows (hills, trees, the tower), none removed; 130 FPS on vs 160 off |
| Cave check with Bliss LOD shadows on | Pass: caves stay dark, no light leak |

## Robustness

| Test | Result |
|---|---|
| Pack switch Bliss -> Photon -> Bliss in-world (twice) | Pass: culling rebinds (79/184 before and after); Photon logs LOD shadows off, Bliss logs them enabled |
| DH render distance 128 -> 64 -> 128 chunks in-world | Pass: 184 -> 142 sections (62/142 in shadow) -> 184 (80/184); no errors |
| 13 Bliss pipeline reloads (option changes) | Pass: correct log lines each time, no culling errors |
| Log check (whole session) | Pass: no errors from Oculus or DH; remaining errors come from other mods or DH's own `mapTest` config message |

## DH generic rendering (beacon beam)

Build `d2fc7b4` (dev branch). An active beacon on a diamond pyramid, viewed from 10+ chunks away so DH draws the beam. None of the
three packs ships `dh_generic`, so all use their `dh_terrain` program (logged once per pack load).

| Test | Result |
|---|---|
| Bliss vs shaders off, same spot (~160 blocks) | Pass: beam drawn through Bliss on the pyramid, same position and width as DH's own beam, lit by the pack |
| Beam behind an LOD hill (~225 blocks) | Pass: the hill hides the lower part of the beam |
| Bliss at night (time 18000) | Pass: beam stays bright (emissive) while LOD terrain is dark |
| Bliss DH shadowmap on, low sun (time 1000) | Pass: no shadow from the beam on LOD terrain |
| Complementary r5.10 + Euphoria 1.11.2 (~80 blocks) | Pass: beam drawn, shaded by the pack |
| Photon v1.3b (~80 blocks) | Pass: beam drawn with Photon's bloom glow |
| Photon at night, cloud shadows on vs off, then cloud altitude raised (~800 blocks) | Photon behaviour: with cloud shadows on the beam turns dark from about the cloud layer up; the dark part moves up with the clouds, and with them far up the beam stays bright to the cloud layer |
| Close to the beacon (within ~32 blocks) | Expected gap: Bliss's DH overdraw prevention discards LOD pixels within `far - 32` blocks (32 at render distance 4), and DH's own shader does the same without shaders. Minecraft's own beam covers that zone once the chunk is loaded |
| Freshly placed beacon/pyramid | Not shown in the LOD until DH updates the chunk (save or unload); DH behaviour, also with shaders off |
| DH clouds (DH cloud rendering on), Complementary with its cloud style set to vanilla | Pass: DH clouds drawn through the pack at cloud height over the LODs, lit by the pack (white by day, dark blue at night), fogged near the horizon; slightly see-through (DH draws them blended), so stars show through at night |
| DH clouds with Complementary's own clouds, Bliss, Photon | Pass: hidden, because the pack turns vanilla clouds off |
| Log check | Pass: "generic objects ... use dh_terrain" for Bliss (3 loads), Complementary and Photon; no build warnings or shader errors |

## Other results from the same day

| Test | Result |
|---|---|
| Oculus Flywheel Compat 1.0.3b, Photon | Pass: 67-81 -> 90-110 FPS near Create builds; shaders off unchanged (227-258) |
| Oculus Flywheel Compat, turning shaders on inside a world | Crash (access violation) in that mod's unsafe vertex reader: existing 32-byte vertex data is read with its 52-byte shader stride. Its bug. Workaround: choose shaders before joining a world |
| Pack without `dh_terrain` (Sildur's Vibrant 1.41) | Pass: LODs hidden while active; back after switching to Photon, no restart |
| Sildur's Vibrant 1.541, Complementary r5.10 + Euphoria 1.11.2, Photon v1.3b | Pass: render correctly with LODs |
| `program.X.enabled` expressions (Bliss `TRANSLUCENT_ENTITIES && IS_IRIS`) | Pass: Bliss loads, its six "defaulting to true" warnings are gone, no new warnings |
| DH config overrides with caller name | Pass: DH's deprecated-API warning is gone |

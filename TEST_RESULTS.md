# Test results

In-game tests of this fork (Oculus 1.6.9 base + Distant Horizons 3.3.3 backport), run on a backup copy of a modded world.

## Test setup

| Item | Value |
|---|---|
| Date | 2026-11-23 |
| Build | `1.8.0 # spoof for DH 3.3 20261123-1401` (branch `dh-3.3.3-mekanism`) |
| Minecraft / Forge | 1.19.2, Forge 43, Embeddium 0.3.18.1 |
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

## Other results from the same day

| Test | Result |
|---|---|
| Oculus Flywheel Compat 1.0.3b, Photon | Pass: 67-81 -> 90-110 FPS near Create builds; shaders off unchanged (227-258) |
| Oculus Flywheel Compat, turning shaders on inside a world | Crash in that mod's vertex reader (its bug). Workaround: choose shaders before joining a world |
| Pack without `dh_terrain` (Sildur's Vibrant 1.41) | Pass: LODs hidden while active; back after switching to Photon, no restart |
| Sildur's Vibrant 1.541, Complementary r5.10 + Euphoria 1.11.2, Photon v1.3b | Pass: render correctly with LODs |

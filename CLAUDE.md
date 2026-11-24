# Spectacles (Oculus 1.6.9 fork for Forge 1.19.2 + Distant Horizons 3.3.3)

Notes for future sessions. Read this first. The full day-by-day history up to 2026-11-23 is in
`claudeOld/CLAUDE-20261123-2157.md`; test numbers are in `TEST_RESULTS.md`.

## What this is
- Fork of `Teqed/Oculus` branch `1.19.2` (Oculus 1.6.9, package `net.coderbot.iris`), renamed **Spectacles**. It carries the
  Distant Horizons features of real Oculus 1.8.0 (Asek3/Oculus `1.20.1-new`), Mekanism fixes (Asek3 1.19.2 / Mekalus), PolarBearEs's
  DH integration (built on fangyu-mc's oculus-dh3-compat), and on dev the Emberlens optimizations.
- Branches: `dh-3.3.3-mekanism` = stable (Spectacles 1.0.0), `dh-3.3.3-mekanism-dev` = dev (1.1.0-dev). Stable must stay an ancestor of
  dev: commit shared docs on stable and merge stable into dev (`-s ours` when dev must keep its own version of a file).
- The user tests in game (Prism instance with a ~400-mod pack, a backup world, Java 24, RTX 5070 Ti laptop,
  Embeddium 0.3.18.1, render distance 4, DH 128 chunks) and builds with JDK 17 in IntelliJ.

## Rules (user decisions; always apply)
- **CRITICAL: at the end of every long session**, copy `CLAUDE.md` to `claudeOld/CLAUDE-YYYYMMDD-HHMM.md` (user's local time), write `CLAUDE-YYYYMMDD-HHMM-QUICK.md` (critical info only, linking to the RESULT file) and `CLAUDE-YYYYMMDD-HHMM-RESULT.md` (that session's test results), then trim the live `CLAUDE.md` back to current rules, state and pointers. Dev branch only.
- Stable moves forward only after the user has tested. Docs-only changes may go to both branches.
- Commit messages end with the session's attribution lines. Don't commit DH jars or other people's binaries. Oculus is LGPL-3.0.
- Keep for compatibility: mod ID `oculus` (`Iris.MODID`, config file names), packages `net.coderbot.iris`, mixin config names,
  `mod_label` starting with `1.8.0` (DH's version check), shader macros. Display names say Spectacles (`Iris.MODNAME`, mods.toml,
  jar `spectacles-mc1.19.2-...`, log prefix `[Spectacles/]`).
- Don't port the whole Iris 1.7 refactor (`net.irisshaders`). Port single features when a pack needs them, each with its own
  feature flag; never define `IRIS_VERSION` as 1.7.
- Experimental (Emberlens) work: every optimization defaults to off; all off = the tested DH baseline (new path behind an `if` on a
  static flag, old code untouched); correctness fixes are separate commits and always on; no Emberbium dependency; keep Oculus
  Flywheel Compat working; measure CPU and GPU time separately, in ms. Keep the original method shape even with the option
  off (same locals in scope, no extra calls of targets the method already uses; new paths go in helper methods), so other
  mods' mixins still match: see "Method shape rule" in `BACKPORT_CANDIDATES.md`.
- This sandbox cannot compile Minecraft code (no Minecraft/Forge jars, Maven blocked). The user's Gradle build or CI is the check.
- GitHub: branch pushes work from the sandbox; tag pushes are refused (403), so the user pushes tags. Leave branch housekeeping alone.

## Current state and next steps
- Stable: **Spectacles 1.0.0 released 2026-11-23** (GitHub release `v1.0.0`, tag on `1a52897`, jar built by CI). Release flow:
  the user pushes the tag, CI drafts the release with the jar, the user pastes the notes and publishes (the sandbox cannot push tags
  or edit releases).
- Dev: Experimental page (13 Emberlens toggles, CPU/GPU pass timing, `RenderTargets` depth fix) builds but is **untested in game**.
- Next: (1) user tests the Experimental page; (2) integrate Oculus Flywheel Compat as `compat/flywheel` and fix its shader-toggle
  crash; (3) batch 2: vertex writing (16-18). Plans and requirements:
  `BACKPORT_CANDIDATES.md`. Item 20 (LOD shadow cache) was dropped 2026-11-24 (few packs use LOD shadows, minimal gains);
  its design is kept in `doc/LOD_Shadow_Cache_Idea.md`.
- Lowest priority: tessellation shaders (feature flag `TESSELLATION_SHADERS`). Unplanned ideas: `IDEAS.md` (dev only).

## Build and CI
- JDK 17 (JDK 21+ fails in repackaged commons-collections). Loom `dev.architectury.loom 1.2-SNAPSHOT`, Gradle 8.1.1.
- `libs/DistantHorizons-3.3.3-1.19.2-fabric-forge.jar` (git-ignored, `compileOnly`). `mod_version` names the jar (stable `1.0.0`,
  dev `1.1.0-dev`); `mod_label` is the mods.toml version ("1.8.0 # spoof for DH 3.3").
- `.github/workflows/build.yml`: JDK 17, fetches the DH jar from Modrinth and checks its SHA-512, builds, uploads the jar on pushes to
  stable and dev; a `v*` tag drafts a release with the jar.
- A good build prints about 19 mixin warnings ("target is public", "Unable to locate ... mapping", `setActive`, `@Overwrite`): noise.

## Code map
- `compat/dh/`: `DHCompatBridge` (loads DH code only if DH is installed), `DHCompat` (event handlers, DH overrides, framebuffers,
  generic objects), `IrisLodRenderProgram` (`dh_terrain`/`dh_water`/`dh_shadow`), `IrisGenericRenderProgram` (beams/clouds;
  `dh_generic`, else `dh_terrain`), `DhShadowCullingFrustum` (LOD shadow culling against `ShadowRenderer.FRUSTUM`),
  `DhFrameBufferWrapper`. Mixins in `compat/dh/mixin/`, gated by `IrisDHCompatMixinPlugin`; `MixinDHShadowPassGuard` decides
  when DH may draw (no LODs for packs without `dh_terrain`, no LODs in the shadow pass without `dh_shadow`).
- Shader transformers for DH programs: `pipeline/transform/transformer/DHTerrainTransformer` and `DHGenericTransformer`
  (`Patch.DH`, `Patch.DH_GENERIC`).
- `shaderpack/parsing/BooleanExpression`: `program.X.enabled` expressions. `shadowNearPlane`/`shadowFarPlane` (-1 = DH distance).
- Dev only: `config/ExperimentalConfig` (options, `config/oculus-experimental.properties`), `gui/screen/ExperimentalScreen`,
  `pipeline/PassTiming` (timestamp queries + nanoTime per pass; overlay and log).

## Gotchas
- DH 3.3.3: `ClientApi.renderLods()` / `renderDeferredLodsForShaders()` draw LODs, from inside Minecraft's `renderChunkLayer`, so also
  during Oculus's shadow pass. `getTargetFramebuffer()` is lowercase-b. DH's Forge `OculusAccessor` is patched to report real state.
  DH fade and TAA are off under shader packs by DH's design.
- Every level change rebuilds the pipeline (dimension ids are compared by reference); `DHCompat.destroy()` unbinds DH overrides.
- DH's framebuffer/depth are game-wide; attach DH depth with `addExternalDepthAttachment` so `RenderTargets` never swaps it on resize.
- A pack's own `dh_generic` needs `blend.dh_generic` (e.g. `off` for Bliss); a program that fails to build hides beams/clouds.
- Faintly glowing modded blocks (Serene Shrubbery, AE2 certus buds) glow as LODs: DH behaviour; add them to DH's
  `ignoredRenderBlockCsv` (exact IDs).
- DH clouds clash with pack clouds; they are hidden when the pack turns vanilla clouds off.
- Not ours: Oculus Flywheel Compat crashes when shaders are switched on inside a world (fix planned with the integration); a one-off
  `NoClassDefFoundError: DhApiWorldUnloadEvent` on Save and Quit (seen once, never came back; suspects DH/Java 24); Photon's harmless "#endif without #if" log line;
  YUNG's "broken basalt columns" log spam while DH generates the Nether.

## Testing tips (from the user)
- A small test instance (Forge 43.5.0, Java 24 + ZGC like the main pack, only Spectacles/Embeddium/DH plus Create or Mekanism when
  needed) is planned for quick iterations; the 400-mod pack stays the final check.
- Render distance 4 with DH 128 chunks; one Spectacles jar enabled; send `latest.log` (Prism's console stops at 100,000 lines).
- LOD shadows: fly ~25 blocks up so the ground below is LODs, or put a tall LOD object between you and a low sun; compare on/off
  from the same spot after chunks settle. DH's F3 line "Shadow VBO Render Count" shows shadow culling.
- Create/Flywheel: test near moving and stationary contraptions with Photon.

## Pointers
`README.md` (users, pack table, credits) · `TEST_RESULTS.md` (all measurements) · `BACKPORT_CANDIDATES.md` (Emberlens list, Flywheel
plan, batch 2 requirements) · `doc/LOD_Shadow_Cache_Idea.md` (dropped item 20) · `IDEAS.md` (dev) · `claudeOld/`: `CLAUDE-20261123-2157-QUICK.md` (fast catch-up on the history), `-RESULT.md` (its test results),
and the full snapshot.

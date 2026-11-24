# Ideas for later

Not planned work. Things worth trying one day, written down so they don't get lost. Planned ports live in
[BACKPORT_CANDIDATES.md](BACKPORT_CANDIDATES.md).

## Modern OpenGL experiment branch (way later)

The user runs ThreatenGL, which makes Minecraft request an OpenGL 4.6 context instead of 3.2. That opens up newer GL features.
Idea: a separate branch off dev (for example `gl46-experiments`), every feature behind a capability check with the current
path as fallback, and measured with the timing overlay (CPU and GPU ms) before anything moves to dev.

Starting point (what the tree already has):
- **DSA** is already used when GL 4.5 or `ARB_direct_state_access` is present (log line "OpenGL 4.5 detected, enabling DSA").
  There may be leftover bind-to-edit code paths to convert, but it is not a new feature.
- **Compute shaders and SSBOs** are already supported *for shader packs* (`COMPUTE_SHADERS`, `SSBO` feature flags). New here would
  be Spectacles using them for its own work.

Candidates, roughly from least to most work:
1. **`GL_KHR_parallel_shader_compile`**: compile all of a pack's programs at once and link when ready, instead of one after
   another. Should cut pack load and pack switch time, which is the slowest thing users see. Self-contained, low risk, easy to
   measure (load time in the log). Good first experiment.
2. **Persistently mapped buffers** (`ARB_buffer_storage`, `GL_MAP_PERSISTENT_BIT`): stream per-frame data (uniform blocks, entity
   vertex uploads) into a buffer that stays mapped, instead of re-uploading every frame. Needs fences so the CPU never writes into
   data the GPU is still reading.
3. **SSBO + multi-draw indirect**: batch many draws into one call. Scope is limited: chunk terrain is Embeddium's job (it already
   batches draws per region) and LODs are DH's, so the realistic target is entity and block-entity rendering in Spectacles's own
   batching. Big change; only worth it if the timing overlay shows draw-call CPU cost dominates.
4. **Compute shaders for GPU offloading**: candidates are clearing or copying render targets, depth copies (`copyPreTranslucentDepth`
   and friends), or culling. Each needs a clear measured win over the current GL calls.

Constraints:
- Without ThreatenGL (or on drivers that only give 3.2 core) everything must fall back to today's path. macOS stops at GL 4.1.
- Only NVIDIA on Windows is available for testing; AMD and Intel drivers behave differently with persistent mapping and
  parallel compile.
- Must not break DH, Embeddium or Oculus Flywheel Compat (or our integrated copy of it, once ported).

## Other ideas from the 2026-11-23 brainstorm

- **Pack compatibility readout**: show on pack load which DH programs and feature flags the pack asked for and which Spectacles
  has (`dh_terrain`, `dh_shadow`, `dh_generic`, missing features), instead of only log lines.
- **DH clouds vs pack clouds**: detect a pack that draws its own clouds and offer to turn DH's cloud rendering off.
- **Shader patching in CI**: run the glsl-transformer harness (the offline check used for `dh_terrain`/`dh_generic`) over sample
  pack shaders on every push, so a broken transformer fails the build instead of the game.
- **Ortho-box culling for LOD shadows**: skip LOD sections outside the shadow map's box (helps Bliss's DH shadowmap with a short
  shadow distance only).
- **Publishing beyond GitHub** (Modrinth): allowed under the LGPL; needs a clear note that the "1.8.0" label is only for DH and
  that this is not official Oculus.
- **Newer DH versions on 1.19.2**: check how much of the integration survives if DH ships updates for 1.19.2.
- **Trim CLAUDE.md**: keep current rules up front, shorten the day-by-day history.

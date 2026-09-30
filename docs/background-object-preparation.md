# Background object preparation

## Agreed player behavior

Keep the world and movement responsive while newly received objects prepare
their graphics. Do not predict animals or download every possible resource.
Prepare the actual render-state variant once the object exists; reuse caches.
New models may appear later instead of stopping the entire picture.
Keep animal danger overlays independent of heavy model preparation.

The user approved this behavior on 2026-09-30. The personal task-router policy
applies: necessary design/review, no repeated approval cascade, no commits or
publication in this task. Preserve all existing uncommitted graphics work.

## Evidence and scope

A controlled Vulkan JFR captured parallel object-update joins of 64/92 ms,
shader compilation while integrating an animated model, then a 287 ms UI
renderer-fence wait with six driver graphics-pipeline creation samples.

First implementation removes synchronous Vulkan shader/program compilation
from draw-list integration, and pipeline creation from draw-list rendering.
This applies to scene objects generally, including world transitions.
It is not a rewrite of networking, map loading, object simulation, texture
decoding or GPU uploads; other stutter sources remain measurable separately.

## Design

- One bounded, priority background queue per Vulkan environment. Admission and
  polling never execute preparation on the caller or wait for compilation.
  World/overlay draws remain immediate, outside the ordinary-object queue.
- Deduplicate pending programs by the same shader-macro identity semantics as
  the existing program cache. Clone keys before submitting.
- Register temporarily pending draw-list slots and retry fairly across frames.
  A pending/removed slot must never be resurrected by a late completion.
  State refresh keeps old valid drawings until replacements are ready.
- Prepare pipeline variants before emitting scene draw commands. Ready
  pipelines only reach the Vulkan execution thread. Retain programs for every
  queued pipeline job; publish results safely across threads.
- Preserve immediate drawing for animal-radius overlays through a renderer-
  independent state hint. Do not turn off overlays or graphics settings.
- Existing immediate UI rendering and OpenGL behavior remain unchanged.
- Failed jobs report real errors rather than silently leaving invisible
  objects forever. Queue saturation defers/retries rather than blocking.
- Environment teardown rejects new work and drains admitted jobs before
  shader modules, pipeline cache or device destruction. No native resource
  may be destroyed while a worker uses it.
- Existing disk shader/pipeline caches remain. Pipeline-cache flags must allow
  concurrent creation; periodic cache persistence must not introduce a
  render-thread wait for background work.
- Background shader compilation uses a separate compiler instance so that
  critical world/UI variants do not wait on its compiler monitor.

## Verification

Deterministic no-GPU tests: nonblocking submission/polling, saturation,
priority/FIFO fairness, exact-once completion, failure delivery, shutdown;
pending slot add/remove/update/refresh and pipeline retention.
Run the full client suite, release-note validation and build the client.
Then test a fresh large-animal approach and world transition on the actual
Vulkan client; do not claim reduced stalls until the runtime comparison.

## Forest-streaming follow-up

A subsequent forest capture found a 101 ms UI render-tree lock wait overlapping
object shadow preparation, and a 71 ms UI minimap wait owned by the explored-map
save thread. These are additional sources, not a before/after FPS benchmark.

Solar and point-light shadow adapters now inherit only the object's scheduling
hint alongside geometry. Other main-pass system state remains stripped; each
shadow pass keeps its own target and viewport. Critical/world policy stays
immediate. Ordinary-object shadow registration uses the same bounded Vulkan
queue, retry and ready-pipeline rules as the object's main pass.

Explored-map main masks are immutable once published. Tile updates copy a grid
only when new tiles are discovered. Saving takes a shallow reference snapshot
under the shared lock and copies its masks outside it. Disk masks are OR-merged
outside the lock, then committed per grid after checking its live identity.
Concurrent exploration causes a retry instead of losing newly discovered tiles.
A clear epoch rejects publication from saves/reloads that started before clear,
including a reset between two grid commits. Session masks retain their existing
separate behavior. A disk save already in progress before clear retains the
existing file-save semantics; this is not a new persistent-clear feature.

Regressions exercise scheduling through both shadow adapters, preservation of
shadow viewports, real Vulkan deferred registration/removal, explored-tile
progress during paused bulk publication, OR union and stale-publication rejection
after clear. Runtime forest comparison on the installed follow-up build remains
necessary; no guarantee of eliminating server/map/upload waits is made.

## References

Diagnostic evidence remains in build/diagnostics/fps-stutter-20260930.
Vulkan permits concurrent pipeline creation with an ordinary internally
synchronized pipeline cache:
https://registry.khronos.org/vulkan/specs/latest/man/html/vkCreateGraphicsPipelines.html

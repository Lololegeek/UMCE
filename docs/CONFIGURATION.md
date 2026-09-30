# Configuration

UMCE reads `config/umce.properties`. The configuration model and mode-selection
policy live in the shared `core` module. A platform adapter contributes the
patches supported by its Minecraft version and translates the shared decisions
to that version's APIs and mixins.

On first launch, UMCE creates the file and adds an `optimization.patch.<id>`
entry for each patch supplied by the adapter. Patch IDs are discovered from the
adapter; `core` does not contain Fabric- or Minecraft-version-specific IDs.

## Modes

Set `optimization.mode` to one of:

| Mode | Behavior |
| --- | --- |
| `safe` | Starts without gameplay Mixins, profiler hooks, or worker threads. This is the default and preserves vanilla hot paths. |
| `auto` | Enables patches marked auto-eligible when the conservative hardware checks pass (at least 2 logical processors and 1 GiB max heap). |
| `balanced` | Enables only patches marked auto-eligible. |
| `performance` | Tries every patch unless its preference is `off`, including experimental patches that may regress a workload. |
| `memory` | Enables patches in the `MEMORY` category unless their preference is `off`. |
| `manual` | Enables only patches whose preference is `on`. |

These initial Auto checks are simple guardrails, not a machine-specific
benchmark or a promise of a speedup. Patch opt-in eligibility is declared with
the shared API descriptor; patch implementation and compatibility checks stay
in the version adapter where Minecraft internals require them.

## Patch preferences

Each patch preference is `auto`, `on`, or `off`:

```properties
optimization.mode=manual
optimization.patch.small-box-section-probe=on
optimization.patch.empty-passenger-track-distance=off
```

`on` and `off` are intended for `manual`, `auto`, `balanced`, and
`performance`; `safe` always disables gameplay patches. `auto` lets the chosen
mode decide. In `performance`, an `auto` preference tries even experimental
patches, so use `off` to keep a known-slower candidate disabled.

Commands (permission level 2):

```text
/umce mode safe|auto|balanced|performance|memory|manual
/umce patch list
/umce patch enable <patch-id>
/umce patch disable <patch-id>
/umce reload
```

Enabling or disabling a patch sets the mode to `manual` and persists that
preference. If a server started in `safe`, enabling a Mixin-backed patch takes
effect after a restart. If the server started with a gameplay Mixin, switching
back to `safe` disables its behavior immediately; restart to remove the
transformed hot-path hook itself. `/umce reload` applies config edits but cannot
add or remove Mixins in a running JVM.

## CPU and GPU settings

`cpu.workers` and `cpu.queueCapacity` configure the shared bounded CPU scheduler.
They do not move Minecraft world mutation off the server thread: game-state
changes must remain on the thread required by the platform API.

`optimization.gpu` accepts `auto`, `on`, or `off`. The current adapters do not
provide a GPU compute backend. `on` is reported as unavailable and does not
activate GPU work. The setting is reserved for a future adapter/backend and is
not a current performance optimization.

## Current Fabric 1.21.1 patches

| Patch ID | Current status |
| --- | --- |
| `small-box-section-probe` | Manual-only and default `off`. On exactly 10,000 pigs it improved aggregate P95/P99, but median MSPT improved in only 3/4 pairs and CPU rose by 4.4 percentage points vs passive; keep it opt-in. |
| `empty-passenger-track-distance` | Manual-only and default `off`. On 100 idle clients it was slower than passive in 3/4 pairs (+4.85% median MSPT); keep it off. |
| `inside-wall-loop` | Original body-box revision rejected. Corrected eye-box revision passes block parity tests and is manual-only/default `off`, with unknown root mods blocked. Screening is promising but noisy; [validation details](FABRIC_1.21.1_INSIDE_WALL_LOOP.md). |
| `poi-candidate-collection` | Experimental and default `off`; no gain in the short screen. |
| `brain-task-launch-cache` | Experimental and default `off`; lost all four paired MSPT comparisons across two screens. |
| `brain-running-task-buffer` | New implementation, default `off`, not eligible for AUTO. Reuses internal running-task snapshot storage with bounded retention. No live test or benchmark yet; unknown root mods block selection. See [implementation and pending validation](FABRIC_1.21.1_BRAIN_RUNNING_TASK_BUFFER.md). |
| `container-empty-scan` | Experimental, default `off`, excluded from AUTO. Indexed current-stack emptiness scan; unknown root mods block selection. [Implementation and pending validation](FABRIC_1.21.1_INVENTORY_SCANS.md). |
| `hopper-full-scan` | Experimental, default `off`, excluded from AUTO. Indexed hopper fullness scan with vanilla exact equality; live parity and measurements pending. |

The entity patch directly probes vanilla's existing `trackingSections` map. It
does not build or maintain a second index and adds no entity insert/remove/move
hooks, cache invalidation, locks, or `ConcurrentHashMap`. Its only algorithmic
work is bounded section-map lookups for eligible small queries; larger queries
fall back to vanilla. Passive startup omits the gameplay Mixins entirely, so it
does not pay a per-query activation check.

Patch results are workload-dependent. No current gameplay patch has met the
acceptance rule of repeatable gains without tail-latency or CPU regression.
All gameplay preferences default to `off`, including in `performance` mode; enable a
patch explicitly with `optimization.mode=manual` and its preference set to
`on`. See the [Fabric entity probe report](FABRIC_1.21.1_SMALL_BOX_SECTION_PROBE.md)
for query profiling, exact 10,000-entity results, and raw ablations.

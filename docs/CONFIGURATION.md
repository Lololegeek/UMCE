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
| `small-box-section-probe` | Manual-only and default `off`. A four-pair 2,000-entity run had a lower median MSPT in 3/4 pairs, but high pair-to-pair variation and one slower pair; keep it opt-in. |
| `empty-passenger-track-distance` | Experimental and not auto-eligible. The initial target workload regressed, so it remains available for explicit selection/testing but is not chosen by Auto or Balanced. |

The entity patch directly probes vanilla's existing `trackingSections` map. It
does not build or maintain a second index and adds no entity insert/remove/move
hooks, cache invalidation, locks, or `ConcurrentHashMap`. Its only algorithmic
work is bounded section-map lookups for eligible small queries; larger queries
fall back to vanilla. Passive startup omits the gameplay Mixins entirely, so it
does not pay a per-query activation check.

Patch results are workload-dependent. The four-pair entity comparison is
promising for median MSPT in that specific saved-pig workload, but does not
establish a repeatable gain across workloads. For production servers, leave
the entity patch off until controlled paired runs show a repeatable gain
without P95/P99 or significant CPU regressions. See the
[Fabric entity probe report](FABRIC_1.21.1_SMALL_BOX_SECTION_PROBE.md) for
query profiling and the raw ablation results.

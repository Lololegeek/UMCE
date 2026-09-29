# Brain task launch cache experiment (Fabric 1.21.1)

## What it changes

`brain-task-launch-cache` flattens the active task sets from `Brain.tasks` once, then reuses that ordered list during `Brain.startTasks`. Each tick it still checks every cached task's vanilla status and calls `Task.tryStarting(world, entity, world.getTime())` only for `STOPPED` tasks. It does not stagger tasks, skip task checks, move work to another thread, or change activity selection.

The cache is invalidated after task-list changes, activity resets/refreshes, core-activity changes, and `Brain.clear`. The list follows the iteration order of the existing priority map, activity map, and task sets. A selected Lithium installation prevents UMCE from applying this Brain Mixin because both transform the same Brain hot path.

The patch is registered as an independent manual option. It is OFF by default and is not part of AUTO. It is supported only for Fabric 1.21.1. Passive UMCE startup omits the Mixin entirely.

## Profile basis

The pre-change Spark server-thread profile (`2026-09-29-1.21.1-poi-candidate-profile-before.md`) attributed repeated work under villager `Brain.startTasks` to nested task/activity traversal. This experiment isolates only that traversal; the profile is a targeting signal, not proof that a cache will help.

## Screening result

The first quick screen used an identical saved world per run: 2,000 pigs and 500 villagers, 2 GiB fixed heap, 5 s warm-up, 8 s measured per run, two alternating cycles. Tick profiler was disabled. UMCE passive is the comparison for the patch; the no-UMCE baseline is also included.

| Condition | Median rolling MSPT | Mean P95 | Mean P99 | CPU (% one core) | Working set MiB |
|---|---:|---:|---:|---:|---:|
| No UMCE | 42.050 | 61.764 | 82.108 | 150.1 | 1634.4 |
| UMCE passive | 44.400 | 64.568 | 82.767 | 152.5 | 1518.9 |
| Cache enabled | 43.700 | 65.182 | 84.216 | 157.1 | 1544.9 |

| Cycle | Passive MSPT | Patch MSPT | Patch vs passive |
|---:|---:|---:|---:|
| 1 | 42.900 | 43.200 | +0.70% |
| 2 | 45.250 | 46.650 | +3.09% |

The first pass predates one extra cache invalidation hook at activity refresh. After rebuilding with that hook, a second independent two-cycle screen produced:

| Condition | Median rolling MSPT | Mean P95 | Mean P99 | CPU (% one core) | Working set MiB |
|---|---:|---:|---:|---:|---:|
| No UMCE | 40.600 | 56.754 | 75.669 | 143.9 | 1710.7 |
| UMCE passive | 39.400 | 55.221 | 73.434 | 122.3 | 1710.6 |
| Cache enabled | 41.300 | 54.243 | 76.510 | 115.0 | 1755.9 |

The patch lost both paired MSPT comparisons again (+0.50%, +5.60%). Across both screens it lost all four pairs. P95/P99 and CPU are mixed across these short runs, and working-set variation is noisy; allocations and GC were not measured. This is enough to reject promotion and keep the patch OFF by default. It does not establish a broad regression across all workloads.

Detailed comparison reports and sample CSVs are retained for both screens at `benchmark-results/2026-09-29-1.21.1-patch-ablation-brain-task-launch-cache-brain-task-cache-screen-r1-*` and `...brain-task-cache-screen-r2-*`.

## Reproduce

```powershell
.\tools\benchmark-fabric-stress-1.21.1.ps1 `
  -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot' `
  -PatchComparison -EnableBrainTaskLaunchCachePatch `
  -Players 0 -Entities 2000 -Villagers 500 `
  -HopperRows 0 -RedstoneClockPairs 0 `
  -MeasureSeconds 8 -WarmupSeconds 5 -Repeats 2 -QuickStartup `
  -InitialHeap 2G -MaximumHeap 2G -ResultTag brain-task-cache-screen-r2
```

The benchmark verifies `brain-task-launch-cache=enabled` in the patch run's UMCE status before recording it as enabled.

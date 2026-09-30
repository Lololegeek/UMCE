# Running-task snapshot buffer: revision and validation

Patch ID: `brain-running-task-buffer`. Status: experimental, default OFF, not eligible for AUTO. The initial implementation regressed in a two-cycle screen; the revised storage also failed its complete two-cycle screen.

## Target and evidence

The existing [AI sampling profile](../benchmark-results/2026-09-29-1.21.1-ai-hotpath-profile.md) places villager Brain processing on the busy server-thread path. Sampling alone does not identify allocation volume. Inspection of the locally mapped Minecraft 1.21.1 bytecode confirms that `Brain.updateTasks` calls `getRunningTasks` every update, and `getRunningTasks` constructs a fresh fastutil `ObjectArrayList`, scans task sets, and adds tasks with status RUNNING.

This candidate targets that short-lived list and its backing storage. It does not reuse results from previous ticks or reuse the rejected active-task launch cache. Allocation savings and performance gains are hypotheses until measured.

## Implementation

- Shared Java 8 runtime utility: `ScopedReusableList<T, L>`, independent of Minecraft, Fabric, and GPU APIs. It reuses the adapter's original list implementation rather than substituting an AbstractList. Other adapters can reuse the lease utility after implementing and validating their own hooks. The original `BoundedSnapshotBuffer` experiment remains in source.
- Fabric 1.21.1 Mixin wraps vanilla `Brain.updateTasks` and substitutes only its internal running-task snapshot.
- Snapshot contents are rebuilt before each update in the same nested map/set order, preserving duplicate occurrences and reference identity. Task status is inspected at capture time, as in vanilla.
- Vanilla `updateTasks` still performs task ticks, using its original world time and task order. A task which changes another task's status does not silently change the already captured snapshot.
- The public `Brain.getRunningTasks` method remains unmodified. External callers receive the normal fresh vanilla list.
- Recursive updates use fresh vanilla snapshots; the outer snapshot is not overwritten.
- A `finally` block clears references even when capture or task code throws. Backing storage exceeding 128 references is dropped after the update. Large active snapshots may still grow to the real task count; tasks are never truncated.
- No global cache, worker, lock, periodic scan, stale activity cache, or gameplay budget is introduced.

Retained storage creates a small persistent memory cost per participating Brain in exchange for fewer temporary allocations. Total RAM, allocation rate, GC pressure, and tick cost must be measured before promotion.

## Conservative compatibility and configuration

The startup hook is selected only when explicitly enabled. Passive/SAFE startup omits it. This first implementation permits the base Minecraft/Fabric Loader environment, Fabric API and its nested modules, and UMCE with its nested libraries. Any other root mod causes hook selection to be refused, with blocking mod IDs included in the patch evaluation. Compatibility with modpacks is unverified; there is no override to force unknown compatibility.

This restriction matters because unknown Brain hooks could retain or alter an internal snapshot. It also excludes Lithium. It is a compatibility gate, not a claim that every excluded mod is incompatible.

Before server startup, configure:

```properties
optimization.mode=manual
optimization.patch.brain-running-task-buffer=on
```

Or use `-Dumce.mode=manual -Dumce.patch.brain-running-task-buffer.enabled=true` in an isolated run. The patch can be disabled through the existing patch manager. If its Mixin was omitted at startup, enabling it requires a restart.

## Validation completed

` :runtime:test :platforms:fabric-1.21.1:test :platforms:fabric-1.21.1:remapJar ` completed successfully. Four new common-buffer tests cover snapshot ordering/identity/duplicates, source changes, nested acquisition, reference cleanup, exception cleanup, retention bounds, and ownership misuse. The generated JAR's Mixin selectors were inspected and correctly remapped to the 1.21.1 intermediary names. The benchmark script was parsed without executing it.

Live Mixin activation and timing/VM-counter measurements subsequently completed. Broad gameplay parity and modpack compatibility remain unverified. No performance recommendation follows from compilation or unit tests.

The original storage revision was subsequently loaded in a live server and screened on 2,000 pigs plus 500 villagers. It lost both paired MSPT comparisons (+3.21% median vs passive) and had a higher mean P95. This was a rejection screen, not evidence of benefit. The revised implementation retains vanilla fastutil ObjectArrayList iteration and clearing through a bounded common lease. Its first instrumented cycle completed but the next cycle was interrupted; [screening review](../benchmark-results/2026-09-30-candidate-screening-review.md) distinguishes complete and partial data. Neither revision is promoted.

The startup plugin also now uses the valid preference `off` for missing experimental-patch entries, matching the patch manager's defaults. The previous string `false` could select unnecessary hooks in a fresh `performance` configuration.

## Complete revised-storage screen

The `brain-buffer-v2-complete` batch used 2,000 pigs and 500 villagers, 12 s warm-up, 15 s measurement, two alternating cycles, and identical external VM diagnostics. Paired MSPT changed +1.08% and +5.71% against passive (median **+3.40%**, better 0/2).

| Median of per-run values | Passive | Patch |
|---|---:|---:|
| MSPT ms | 37.275 | 38.550 |
| Rolling P95 ms | 48.156 | 50.412 |
| Rolling P99 ms | 53.658 | 57.302 |
| CPU % one core | 90.711 | 91.402 |
| Working set MiB | 1890.838 | 1892.289 |
| Server-thread allocation MiB/s | 292.606 | 292.031 |

Allocation changes had opposite signs between cycles; there is no repeatable allocation gain in this screen. Keep the revised implementation experimental and OFF. The partial earlier batch is not pooled with this complete batch. See [complete timing and VM counters](../benchmark-results/2026-09-30-brain-buffer-v2-vm-comparison.md).

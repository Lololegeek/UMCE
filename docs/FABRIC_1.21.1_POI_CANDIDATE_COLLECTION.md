# Fabric 1.21.1 POI candidate collection experiment

Status: **no gain in the short screen; manual-only and OFF by default**.

## Profile attribution

The motivating profile and raw Spark capture are documented in [the POI pre-patch profile](../benchmark-results/2026-09-29-1.21.1-poi-candidate-profile-before.md). In the 2,000-pig plus 500-villager profile, a POI-search call from `FindPointOfInterestTask.method_46885` appeared under `Brain.startTasks`. The implementation retains the vanilla stream and limit, then applies the same collector supplier, accumulator, and finisher with a local sequential iterator. Parallel streams call the original `Stream.collect` path.

## Short screening

Conditions used identical generated worlds, 5 s warm-up, 8 s measure, two alternating cycles, no clients, and a fixed 2 GiB heap.

| Condition | Median rolling MSPT | Mean P95 | Mean P99 | CPU (% one core) | Working set MiB |
|---|---:|---:|---:|---:|---:|
| Baseline | 50.500 | 71.252 | 95.448 | 159.6 | 1667.2 |
| Passive UMCE | 42.600 | 57.826 | 79.458 | 132.0 | 1686.3 |
| Patch enabled | 45.500 | 58.919 | 84.303 | 141.2 | 1424.8 |

Patch MSPT was slower than passive UMCE in both cycles (+9.62%, +2.27%; median +5.94%). The screen does not justify a long benchmark. The patch remains independently selectable for future experiments but is OFF by default and never AUTO-eligible.

Saved entity data contained all 500 villagers in each condition; profession counts matched (`minecraft:none`: 500 each). This is a narrow state check, not a complete behavior-equivalence proof. No allocation-rate or GC-pause data was collected; the lower patch working set is too noisy to claim a memory gain.

See the [full ablation report](../benchmark-results/2026-09-29-1.21.1-patch-ablation-poi-candidate-collection-poi-candidate-screen-r1-comparison.md) and adjacent CSV for raw timings and per-cycle values.

## Decision

Do not enable by default. The `Stream.collect` reduction was not a useful optimization for this workload; the JIT and stream spliterator already handle the limited scan efficiently. The next candidate should address the repeated nested activity/task traversal in `Brain.startTasks`, which dominates the same profile.

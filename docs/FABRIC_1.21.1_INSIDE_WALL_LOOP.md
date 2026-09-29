# Fabric 1.21.1 inside-wall scan experiment

Status: **rejected; hook force-disabled because of a gameplay behavior mismatch**.

## Profile that motivated the experiment

The 2026-09-29 Spark profile of 2,000 pigs plus 500 villagers sampled `Entity.isInsideWall()` in the hot `LivingEntity.tickMovement` path. The 500-villager and mixed profiles, raw Spark files, capture details, and inclusive sample weights are recorded in [the profile report](../benchmark-results/2026-09-29-1.21.1-ai-hotpath-profile.md).

Minecraft 1.21.1's `BlockPos.stream(Box)` already uses one reusable `BlockPos.Mutable`, visits the same inclusive floored box, and iterates x fastest, then z, then y. The experiment replaced the Stream terminal scan with local loops, retained vanilla block suffocation and voxel-shape intersection checks, and hoisted the entity box shape conversion out of the block loop. It does not parallelize gameplay or skip any positions/checks.

## Implementation and toggle

The isolated patch id is `inside-wall-loop`. Its default config preference is `off`, and the Mixin hook is now hard-locked off after the parity failure below. A saved `on` preference or `-Dumce.patch.inside-wall-loop.enabled=true` cannot apply it. The implementation remains in source for diagnosis, but `OptimizationPatch.evaluate` rejects it.

The benchmark runner supports three-way ablation through `-PatchComparison -EnableInsideWallLoopPatch`.

## Short screening results

The second screen used the same saved workload for baseline, passive UMCE, and patch-enabled runs, with 5 seconds warm-up, 8 seconds measured per condition, 2 alternating cycles, no clients, and a 2 GiB fixed heap. It is a rejection screen, not a long-form release benchmark.

| Condition | Median rolling MSPT | Mean P95 | Mean P99 | CPU (% one core) | Working set MiB | Villagers logged suffocating in walls |
|---|---:|---:|---:|---:|---:|---:|
| Baseline | 43.600 | 67.122 | 83.701 | 173.2 | 1484.5 | 0 |
| Passive UMCE | 45.100 | 61.245 | 80.770 | 161.6 | 1597.9 | 0 |
| Patch enabled | 49.000 | 70.230 | 85.143 | 170.3 | 1461.7 | 230 / 238 |

Across the two paired cycles, patch-enabled MSPT was slower than passive UMCE in both: +5.44% and +13.94%, median +9.69%. Mean P95 and P99 were also higher. More critically, the enabled runs logged 230 and 238 villager wall-suffocation deaths while baseline and passive runs logged zero. The implementation has a behavior mismatch that remains unexplained, so performance results are secondary and the Mixin is force-disabled. The full raw output is [the comparison report](../benchmark-results/2026-09-29-1.21.1-patch-ablation-inside-wall-loop-inside-wall-screen-r2-comparison.md) and its adjacent CSV.

A separate one-cycle screen is retained in the same results folder. Do not combine it with the two-cycle result because the run settings differ.

The [JFR-labeled one-cycle screen](../benchmark-results/2026-09-29-1.21.1-patch-ablation-inside-wall-loop-inside-wall-jfr-comparison.md) is retained too; its `.jfr` output was empty, so it only adds a noisy timing sample and no allocation/GC data.

## Measurement limits

These screens collected MSPT, P50/P95/P99, process CPU, and working set. They did **not** measure allocation rate, object-allocation samples, or GC pauses. A separate JFR-labeled run has one-cycle timing output, but its recording was empty, so it is not allocation or GC evidence and is not pooled with the other screenings. See the [diagnostic note](../benchmark-results/2026-09-29-1.21.1-inside-wall-allocation-gc-diagnostic.md). The Spark sampling profile is not allocation or GC evidence. No performance claim is made for RAM or allocations; the working-set difference is noisy and is not treated as a patch effect.

Keep the hook force-disabled. Reconsider the experiment only after its vanilla behavior parity is fixed and covered by tests, followed by allocation-aware profiling and a workload that repeatedly demonstrates a net gain against passive UMCE without tail-latency or CPU regression.

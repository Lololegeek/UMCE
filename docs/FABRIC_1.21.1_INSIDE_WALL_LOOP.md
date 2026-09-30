# Fabric 1.21.1 inside-wall scan experiment

Status: **original experiment rejected; corrected revision is default OFF and manual-only, with conservative mod compatibility and validation in progress**.

## Profile that motivated the experiment

The 2026-09-29 Spark profile of 2,000 pigs plus 500 villagers sampled `Entity.isInsideWall()` in the hot `LivingEntity.tickMovement` path. The 500-villager and mixed profiles, raw Spark files, capture details, and inclusive sample weights are recorded in [the profile report](../benchmark-results/2026-09-29-1.21.1-ai-hotpath-profile.md).

Minecraft 1.21.1's `BlockPos.stream(Box)` already uses a reusable mutable position. The corrected source uses vanilla's iterable directly, preserving its exact inclusive floored boundaries and encounter order while removing the Stream terminal scan. It retains block suffocation and voxel-shape intersection checks, and constructs the query shape lazily only after a candidate suffocating block is encountered.

Bytecode review traced the original behavior divergence to its query geometry: it expanded the entity's entire body bounding box instead of using vanilla's very thin eye-centered `Box.of(getEyePos(), width * 0.8f, 1.0E-6, width * 0.8f)`. That body expansion included ground-level blocks, explaining unjustified suffocation. The source now uses the vanilla eye box. Geometry tests compare actual vanilla Stream positions, including negative/exact boundaries, and verify ground exclusion.

The new block parity tests compare results and short-circuit read order against the inspected vanilla predicate for eight real block states, four eye positions and two widths (64 combinations), plus explicit floor/eye-height solid-block checks. They use the [official Fabric Loader JUnit launcher](https://github.com/FabricMC/fabric-loader/blob/0.16.14/junit/src/main/java/net/fabricmc/loader/impl/junit/FabricLoaderLauncherSessionListener.java) in SERVER mode; a plain JUnit classloader could not initialize the mapped registries. The corrected implementation uses WrapMethod, preserving the original method when disabled and avoiding cancellation callback objects in the active path.

## Implementation and toggle

The isolated patch id is `inside-wall-loop`. Its default config preference is `off` and it is not AUTO-eligible. The corrected revision can be selected explicitly in MANUAL mode before startup, using the existing config or JVM override. Passive and SAFE startup omit the hook. Unknown root mods prevent hook selection, using the same base-environment whitelist as the running-task buffer and inventory experiments. Compatibility with modpacks is not established.

The benchmark runner supports three-way ablation through `-PatchComparison -EnableInsideWallLoopPatch`.

## Corrected revision screening

The September 30 two-cycle screening used 2,000 pigs plus 500 villagers, 12 s warm-up, 15 s measurement per condition, and identical VM-counter diagnostics in all three conditions. The patch improved paired MSPT by 25.94% and 19.29% against passive UMCE, but passive itself was 38.73% and 14.47% slower than baseline. This anomalous passive variation prevents treating the apparent 22.62% paired gain as established benefit. Patch vs baseline was only -2.44% median paired change.

All six saved worlds retained 500 villagers and had no NoAI flags. There were no logged villager suffocation deaths. Saved pig counts ranged from 1,991 to 1,999 across conditions, and the world also contains ambient animals and structures. This is a narrow parity audit, not full gameplay proof. Allocation change also varied by cycle. Detailed results, raw counters and world audits are retained under `inside-wall-v2-screen`. Four more alternating cycles on the same artifact were started for confirmation.

## Short screening results

The second screen used the same saved workload for baseline, passive UMCE, and patch-enabled runs, with 5 seconds warm-up, 8 seconds measured per condition, 2 alternating cycles, no clients, and a 2 GiB fixed heap. It is a rejection screen, not a long-form release benchmark.

| Condition | Median rolling MSPT | Mean P95 | Mean P99 | CPU (% one core) | Working set MiB | Villagers logged suffocating in walls |
|---|---:|---:|---:|---:|---:|---:|
| Baseline | 43.600 | 67.122 | 83.701 | 173.2 | 1484.5 | 0 |
| Passive UMCE | 45.100 | 61.245 | 80.770 | 161.6 | 1597.9 | 0 |
| Patch enabled | 49.000 | 70.230 | 85.143 | 170.3 | 1461.7 | 230 / 238 |

Across the two paired cycles, the original patch-enabled MSPT was slower than passive UMCE in both: +5.44% and +13.94%, median +9.69%. Mean P95 and P99 were also higher. The enabled runs logged 230 and 238 villager wall-suffocation deaths while baseline and passive runs logged zero. These timings belong to the rejected body-box revision, not the corrected source. The full raw output is [the comparison report](../benchmark-results/2026-09-29-1.21.1-patch-ablation-inside-wall-loop-inside-wall-screen-r2-comparison.md) and its adjacent CSV.

A separate one-cycle screen is retained in the same results folder. Do not combine it with the two-cycle result because the run settings differ.

The [JFR-labeled one-cycle screen](../benchmark-results/2026-09-29-1.21.1-patch-ablation-inside-wall-loop-inside-wall-jfr-comparison.md) is retained too; its `.jfr` output was empty, so it only adds a noisy timing sample and no allocation/GC data.

## Measurement limits

These screens collected MSPT, P50/P95/P99, process CPU, and working set. They did **not** measure allocation rate, object-allocation samples, or GC pauses. A separate JFR-labeled run has one-cycle timing output, but its recording was empty, so it is not allocation or GC evidence and is not pooled with the other screenings. See the [diagnostic note](../benchmark-results/2026-09-29-1.21.1-inside-wall-allocation-gc-diagnostic.md). The Spark sampling profile is not allocation or GC evidence. No performance claim is made for RAM or allocations; the working-set difference is noisy and is not treated as a patch effect.

Keep the hook force-disabled. Reconsider the experiment only after its vanilla behavior parity is fixed and covered by tests, followed by allocation-aware profiling and a workload that repeatedly demonstrates a net gain against passive UMCE without tail-latency or CPU regression.

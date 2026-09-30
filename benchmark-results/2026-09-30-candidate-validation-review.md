# Candidate validation review — September 30, 2026

## Decision

No candidate in this batch passed overall performance acceptance. Preserve the implementations and reports; keep all four candidates experimental, default OFF and excluded from AUTO. Unknown root mods block their hooks. No claim of universal optimization, zero passive overhead, reduced total RAM, or modpack compatibility follows from these runs.

| Isolated candidate | Workload | Complete paired cycles | Median paired MSPT change vs passive | Faster pairs | Decision |
|---|---|---:|---:|---:|---|
| Revised Brain running-task buffer | 2,000 pigs + 500 villagers | 2 | +3.40% | 0/2 | Reject promotion; allocation changes had mixed signs |
| Container empty scan | 1,024 hoppers | 2 screening | +7.14% | 0/2 | Reject promotion; no allocation counters in this screen |
| Hopper full scan | 1,024 hoppers | 4 | 0.00% | 0/4 | Reject promotion; CPU and P99 higher |
| Corrected inside-wall loop | 2,000 pigs + 500 villagers | 4 | +1.46% | 1/4 | Allocation reduction reproduced, overall speed acceptance failed |

Positive MSPT change means slower. Paired percentages use each cycle's patch and passive run; they are not ratios of the grouped medians below. Screens are short and do not establish statistical significance. There is large baseline/passive variation, so the passive overhead requirement remains unconfirmed.

## Complete instrumented comparisons

Each condition uses the same external VM-counter agent. Revised Brain, hopper confirmation and corrected inside-wall runs use 12 seconds warm-up and 15 seconds measurement, a fixed 2 GiB heap, saved workloads, and alternating condition order. No compiler or second benchmark ran concurrently. Values below are medians of per-run values; P95/P99 are medians of per-run rolling means. Rolling windows overlap and are not independent trials.

| Candidate / condition | MSPT ms | P95 ms | P99 ms | CPU % one core | Working set MiB | Server alloc MiB/s |
|---|---:|---:|---:|---:|---:|---:|
| Brain / baseline | 40.100 | 50.042 | 56.141 | 96.332 | 1878.539 | 299.747 |
| Brain / passive | 37.275 | 48.156 | 53.658 | 90.711 | 1890.838 | 292.606 |
| Brain / patch | 38.550 | 50.412 | 57.302 | 91.402 | 1892.289 | 292.031 |
| Hopper / baseline | 1.450 | 2.642 | 7.914 | 6.799 | 1044.779 | 6.484 |
| Hopper / passive | 1.400 | 2.814 | 7.513 | 7.728 | 1076.152 | 6.258 |
| Hopper / patch | 1.400 | 2.415 | 7.822 | 9.702 | 1040.445 | 6.205 |
| Inside-wall / baseline | 41.750 | 53.534 | 60.607 | 102.701 | 1862.053 | 285.331 |
| Inside-wall / passive | 37.050 | 48.320 | 56.449 | 91.183 | 1860.229 | 293.395 |
| Inside-wall / patch | 40.000 | 52.463 | 62.728 | 96.356 | 1883.457 | 280.063 |

Full baseline/passive/patch timing, GC counts and collection totals, allocation sub-window bounds, raw filenames, immutable artifact hashes and per-cycle comparisons:

- [Brain revised storage](2026-09-30-brain-buffer-v2-vm-comparison.md)
- [Hopper four-cycle confirmation](2026-09-30-hopper-full-vm-comparison.md)
- [Inside-wall four-cycle confirmation](2026-09-30-inside-wall-v2-validation-vm-comparison.md)
- [Inside-wall preliminary screen](2026-09-30-inside-wall-v2-vm-comparison.md): apparent -22.62% speed gain against anomalously slow passive runs; not reproduced in confirmation.
- [Original screens and interrupted data](2026-09-30-candidate-screening-review.md): preserve rejected and partial captures without pooling different revisions or incomplete cycles.

## Actual improvements and limits

The inside-wall correction restores vanilla's eye-centered box instead of the original body-expanded box which caused mass villager suffocation. It preserves vanilla coordinate iteration and creates the query VoxelShape lazily. Real vanilla block tests cover 64 result/read-order combinations and explicit floor/eye-height checks. Runtime/platform tests and remapJar passed for the benchmarked source.

All twelve confirmation world audits retain 500 villagers, with no NoAI flags. Saved pigs range from 1,993 to 1,998 across conditions. This audit does not establish full gameplay equivalence. The preliminary six-world audits are also retained.

Inside-wall server-thread allocation decreased in all four confirmation pairs (-5.812, -10.387, -22.421, -18.127 MiB/s). Median rates fell approximately 4.54%, but CPU, tails and working set increased. This is a measured allocation/speed tradeoff, not an accepted overall optimization. The Brain revision preserves vanilla fastutil storage using the common bounded lease; its small aggregate allocation decrease did not reproduce cycle by cycle.

The hopper fixture initializes only slot 0 with 64 stone; all destination chest slots are full, and hopper stacks may redistribute. It does not test every hopper slot permanently full. Broad loot/transfer/comparator parity is unverified.

VM counters cover sampled sub-windows (about 14 seconds of a 15-second measurement). All-thread counters can miss short-lived threads; GC MXBean collection time is not individual pause latency. Empty JFR captures are retained as failures, not zero allocation evidence. See [measurement definitions](../docs/BENCHMARK_METRICS.md).

The next performance change needs targeted profiling to explain the remaining activation/scan/iteration overhead. These results do not support enabling candidates together or adding parallel ticking/GPU work.

# Fabric 1.21.1: small-box entity section probe

## What it changes

The patch replaces the body of `SectionedEntityCache.forEachInBox` only when all of these conditions hold:

- Fabric adapter is running Minecraft 1.21.1;
- UMCE mode is `optimized` and patch `small-box-section-probe` is enabled;
- the query covers at most 64 section coordinates.

Vanilla scans its sorted set of tracked section positions in the query's X interval, filters Y/Z, and then reads each matching section. The patch calculates the same expanded bounds and probes the existing `trackingSections` map directly for each packed section coordinate. It keeps vanilla's packed-coordinate visitation order, skips empty/non-tracking sections, and honors early abort from the consumer. Queries over 64 candidate positions use the original vanilla method. It adds no second index or persistent cache.

The optimization is based on the pre-change Spark profile: `SectionedEntityCache.forEachInBox` appeared in 24.3% of inclusive server-thread sample weight during a 10k-entity workload. The separate hopper profile attributed about 0.6% to `HopperBlockEntity.serverTick`, so hopper polling was not selected. Raw profiles and the pre-change analysis are in [benchmark-results/2026-09-27-1.21.1-entity-profile-before.md](../benchmark-results/2026-09-27-1.21.1-entity-profile-before.md).

## Safety and controls

SAFE is the default (`umce.mode=safe`). The runtime flag stays false and the mixin returns at method entry, allowing vanilla to run unchanged. To opt in, set `-Dumce.mode=optimized -Dumce.patch.small-box-section-probe.enabled=true`. Operators can also use `/umce patch enable small-box-section-probe` and `/umce patch disable small-box-section-probe`; the toggle is independently managed through `OptimizationPatch` and `PatchEngine`.

The patch is marked medium risk and is supported only on Fabric 1.21.1. If its preconditions are not met, patch evaluation refuses to enable it.

## Validation and results

The Java test compares the candidate coordinate visitation sequence with the vanilla sorted-set sequence across ranges that cross zero and ranges on either side. The integrated server smoke run confirmed that the mixin loads and the patch can be enabled. SAFE and patch-enabled stress runs were also launched separately.

The four-cycle comparison uses the same saved 10k-pig world, no clients or villagers, no configured hoppers/redstone/TNT, a 1G initial and 4G maximum heap, 20 seconds of warmup, and 30 seconds of measurement per condition. The order alternates each cycle. See the full paired results and raw time series in [the ablation report](../benchmark-results/2026-09-27-1.21.1-patch-ablation-entity-section-probe-10k-comparison.md) and [CSV samples](../benchmark-results/2026-09-27-1.21.1-patch-ablation-entity-section-probe-10k-samples.csv).

Against diagnostics-only, patch-enabled median rolling MSPT was faster in all four pairs (−6.90%, −2.98%, −4.21%, −2.77%; median −3.60%). Mean P95 and P99 were also lower in aggregate. Working set was effectively unchanged (1331.5 MiB vs 1334.0 MiB). Process CPU averaged 116.1% of one core with the patch vs 113.2% diagnostics-only; treat this as a measured increase, not a CPU reduction. This is one isolated entity-heavy workload, not evidence for all server workloads.

Allocation data is unavailable. On this Windows/JDK setup, HotSpot reported an active JFR recording but every `jcmd JFR.dump` completed with 0 bytes written. No allocation improvement is claimed. Revisit this measure with a working allocation sampler before making conclusions about allocation rate.

Build and tests were run with:

```powershell
.\gradlew.bat :platforms:fabric-1.21.1:build
```

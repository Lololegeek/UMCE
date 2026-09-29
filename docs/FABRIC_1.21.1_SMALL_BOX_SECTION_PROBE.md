# Fabric 1.21.1: small-box entity section probe

## Implementation and controls

The patch replaces `SectionedEntityCache.forEachInBox` only when the startup
configuration selects `small-box-section-probe` and a query spans at most 64
section coordinates. It probes Minecraft's existing `trackingSections` map in
the same packed-coordinate order as vanilla and preserves empty-section checks
and consumer early-abort behavior. Larger queries call vanilla directly.

There is no UMCE-owned spatial index. The patch does not add index builds,
maintenance, entity insert/remove/move callbacks, cache invalidation, locks,
or a `ConcurrentHashMap`. On an accelerated query it replaces vanilla's
section scan; it does not run the vanilla scan and the probe twice.

`optimization.patch.small-box-section-probe=off` is the default. The patch is
manual-only and requires a restart to add or remove its Mixin. For a controlled
test, use `optimization.mode=manual` with the preference set to `on`, or start
with `-Dumce.mode=optimized
-Dumce.patch.small-box-section-probe.enabled=true`. Keep it off for normal
servers until the target workload has repeatable gains without tail-latency or
CPU regressions.

## Passive startup

`-Dumce.passive=true` prevents all UMCE gameplay and diagnostic Mixins from
being applied during Mixin startup. In this mode the Fabric adapter does not
start the tick profiler, entity-query profiler, or a worker pool, and it does
not select patches. Normal SAFE configuration also omits gameplay Mixins.
The adapter still loads its configuration and registers its command/lifecycle
callbacks; those callbacks do not scan entities or execute per tick in passive
mode.

A separate four-pair SAFE-mode comparison with 100 idle clients had a paired
median MSPT change of -3.71%, but pair-to-pair SD was 30.59 percentage points.
That result is too noisy to establish a network overhead or gain; see the
[100-client report](../benchmark-results/2026-09-29-1.21.1-stress-passive-network-100-validation-rerun-comparison.md).

## Four-pair ablation: exactly 10,000 entities

The test used exactly 10,000 saved pigs, no clients, villagers, hoppers,
redstone, or TNT, a 1G initial / 4G maximum heap, 10 seconds warmup and 10
seconds measured per condition. Four paired cycles alternated the order. Tick
profiling was disabled. This is a short, single-workload comparison, not a
general server claim.

| Condition | Median rolling MSPT | Mean P95 | Mean P99 | CPU (% of one core) | Working set |
|---|---:|---:|---:|---:|---:|
| Vanilla baseline | 78.650 ms | 106.194 ms | 126.317 ms | 127.6% | 1320.2 MiB |
| UMCE passive | 78.800 ms | 104.150 ms | 119.462 ms | 128.6% | 1333.2 MiB |
| Entity patch | 78.300 ms | 96.944 ms | 109.897 ms | 133.0% | 1335.0 MiB |

Across four paired medians, passive UMCE differed from baseline by +0.51%
(sample SD 7.93 percentage points). That is consistent with no clear MSPT
overhead at this sample size; CPU was 0.6 points higher. This does not prove
zero overhead on other workloads.

The patch improved median MSPT in three of four pairs. Its median paired
change was -6.40%, but the paired SD was 10.39 points and one pair was 7.91%
slower. Aggregate P95/P99 were lower than passive UMCE, while CPU was 4.4
percentage points higher and working set was 1.8 MiB higher. This does not
meet the CPU and repeatability requirements, so the patch remains default-off
and manual-only.

A follow-up candidate that fell back to vanilla whenever the total tracked
section count was no larger than the probe volume was tested separately. It
made this 10,000-pig case slower in three of four pairs (+3.02% median vs
passive), with worse P95/P99 and CPU. That guard was rejected and is not in the
shipping code; its [ablation report](../benchmark-results/2026-09-29-1.21.1-patch-ablation-small-box-section-probe-adaptive-exact10k-entity-validation-comparison.md)
is retained for comparison.

Full results and raw samples: [four-pair 10,000-entity ablation report](../benchmark-results/2026-09-29-1.21.1-patch-ablation-small-box-section-probe-exact10k-entity-validation-comparison.md)
and [sample CSV](../benchmark-results/2026-09-29-1.21.1-patch-ablation-small-box-section-probe-exact10k-entity-validation-samples.csv).

A separate 11,000-pig run produced only a -0.91% median MSPT change vs passive
(3/4 pairs faster, SD 3.10 points), with P95/P99 slightly worse and CPU nearly
unchanged. It supports keeping the patch opt-in rather than claiming a stable
general gain. See the [11,000-entity report](../benchmark-results/2026-09-28-1.21.1-patch-ablation-entity-section-probe-passive-entity-10k-validation-comparison.md).

## Query-level diagnostic profile

The opt-in diagnostic wrapper measured baseline vanilla queries at 823 ns
inclusive mean and the accelerated patch queries at 782 ns, about 5.0% lower
for this query method. The baseline run counted 668,499 queries and about
53 allocated bytes per query; the patch run counted 665,771 accelerated
queries and about 76 allocated bytes per query. The patch made 1,079,158
direct section probes (about 1.62 per query) and had no fallback queries.
Inclusive query time per server tick was 3.075 ms for the vanilla diagnostic
run and 2.928 ms for the patch diagnostic run.

These are separate short instrumented runs. Allocation samples include work
performed by the query consumer and are not a controlled allocation delta;
they do not establish that the patch itself allocates an extra 23 bytes. The
diagnostic Mixin and allocation sampling add overhead, so these numbers explain
the hot path but are not used as performance benchmark results.

| Instrumented condition | Queries | Mean inclusive time | Bytes/query | Exclusive query time/tick |
|---|---:|---:|---:|---:|
| Vanilla query | 668,499 | 823 ns | 53 B | 3.075 ms |
| Accelerated query | 665,771 | 782 ns | 76 B | 2.928 ms |

The measured UMCE index-specific costs are all zero because no custom index
exists: builds 0, maintenance updates 0, inserts/removes/moves 0, cache
invalidations 0, locks 0. No `ConcurrentHashMap`, collection copy, or periodic
index reconstruction is present. Mixin selection ensures an accelerated call
replaces the vanilla method body; larger or unsupported queries fall back to
the original method.

Profile comparisons: [vanilla profile](../benchmark-results/2026-09-28-1.21.1-stress-entity-profile-vanilla-remapped-2000-v2-comparison.md)
and [patch profile](../benchmark-results/2026-09-28-1.21.1-stress-entity-profile-patch-remapped-2000-v1-comparison.md).

The 100-client passenger-tracking candidate was also retested across four
paired cycles. It was slower than passive UMCE in three pairs (+4.85% median)
and remains default-off; see [its four-pair ablation report](../benchmark-results/2026-09-29-1.21.1-patch-ablation-empty-passenger-track-distance-passenger-network-100-validation-comparison.md).
An earlier two-pair screen looked faster in both pairs, but that signal did not
hold up in validation; its [screen report](../benchmark-results/2026-09-29-1.21.1-patch-ablation-empty-passenger-track-distance-passenger-network-100-screen-comparison.md)
is retained to show why the follow-up was needed.

The previous 10k-entity profile identified `SectionedEntityCache.forEachInBox`
as a hot path, but it is not proof of a win from this probe. Older benchmark
claims were made before passive startup could omit the Mixin and are superseded
by the ablation above.

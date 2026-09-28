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

## Four-pair ablation: 2,000 entities

The test used 2,025 saved pigs, no clients, villagers, hoppers, redstone, or
TNT, a 1G initial / 2G maximum heap, 2 seconds warmup and 5 seconds measured
per condition. Four paired cycles alternated the order. Tick profiling was
disabled. This is a short, single-workload comparison, not a general server
claim.

| Condition | Median rolling MSPT | Mean P95 | Mean P99 | CPU (% of one core) | Working set |
|---|---:|---:|---:|---:|---:|
| Vanilla baseline | 26.700 ms | 53.138 ms | 127.406 ms | 78.2% | 1179.8 MiB |
| UMCE passive | 25.900 ms | 56.263 ms | 119.152 ms | 80.2% | 1179.5 MiB |
| Entity patch | 24.850 ms | 52.853 ms | 121.148 ms | 76.4% | 1188.1 MiB |

Across four paired medians, passive UMCE differed from baseline by -1.78%
(sample SD 5.66 percentage points). That is consistent with no clear MSPT
overhead at this sample size; CPU was 2.0 points higher and working set was
essentially unchanged. This does not prove zero overhead on other workloads.

The patch was faster than passive UMCE in three of four pairs. Its median
paired change was -4.65%, but the paired SD was 10.43 points and one pair was
10.85% slower. Aggregate P95/P99 were below baseline, while patch P99 was
slightly above passive UMCE; CPU was lower in this run and working set was
8.3 MiB higher than baseline. This is not enough evidence to call the gain
reproducible across workloads. The patch therefore remains default-off and
manual-only.

Full results and raw samples: [four-pair ablation report](../benchmark-results/2026-09-28-1.21.1-patch-ablation-entity-section-probe-passive-entity-2000-remapped-4pairs-comparison.md)
and [sample CSV](../benchmark-results/2026-09-28-1.21.1-patch-ablation-entity-section-probe-passive-entity-2000-remapped-4pairs-samples.csv).

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

The previous 10k-entity profile identified `SectionedEntityCache.forEachInBox`
as a hot path, but it is not proof of a win from this probe. Older benchmark
claims were made before passive startup could omit the Mixin and are superseded
by the ablation above.

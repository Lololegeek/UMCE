# UMCE server comparison

Captured: 2026-09-26 14:51:20Z

Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Java 25; fixed seed and a pristine identical save per run. The only mod difference is UMCE. Each run warms for 20 s and measures for 30 s; order alternates across 3 repeats. No player is connected, so this is an idle spawn-chunk server benchmark, not a simulated gameplay workload or a claim of optimization gains.

| Condition | Queries (ticks) | Mean tick time ms | Mean P50 ms | Mean P95 ms | Mean P99 ms | Mean process CPU % | Mean working set MiB |
|---|---:|---:|---:|---:|---:|---:|---:|
| without-umce | 21 (2100 ticks) | 0.157 | 0.114 | 0.205 | 3.486 | 0.65 | 1288.7 |
| with-umce | 21 (2100 ticks) | 0.148 | 0.114 | 0.195 | 2.586 | 0.64 | 1295.2 |

Change with UMCE: mean tick time -6.1%, process CPU -1.3%, working set +6.4 MiB.

Minecraft rounds each `/tick query` metric to 0.1 ms per 100-tick window. Differences below that display resolution are measurement noise, not evidence of an optimization.

Raw tick query replies, timestamps, process CPU time, and process working-set readings are in the CSV. The command output format is retained verbatim so the aggregate parser can be audited.

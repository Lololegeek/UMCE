# Timing and VM counters

Tag: `hopper-full-validation`; complete paired cycles: 4.
UMCE SHA-256: `02fb03fda1d3693ae50aaa2ea9b82bf1992c399d9c7f215e5ae78d35ed135c73`

Values are medians of per-run values. P95/P99 columns are medians of per-run rolling means.

| Condition | MSPT ms | P95 ms | P99 ms | CPU % one core | Working set MiB | Server alloc MiB/s | GC collections | GC collection ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| without-umce | 1.450 | 2.642 | 7.914 | 6.799 | 1044.779 | 6.484 | 0.000 | 0.000 |
| umce-passive | 1.400 | 2.814 | 7.513 | 7.728 | 1076.152 | 6.258 | 0.000 | 0.000 |
| patch-enabled | 1.400 | 2.415 | 7.822 | 9.702 | 1040.445 | 6.205 | 0.000 | 0.000 |

## Paired changes against passive

| Cycle | MSPT change % | Server allocation change MiB/s |
|---:|---:|---:|
| 1 | +0.000 | -0.125 |
| 2 | +0.000 | +0.230 |
| 3 | +0.000 | +0.003 |
| 4 | +7.143 | -0.343 |

Rolling tick windows overlap. Runs and paired cycles are the units of comparison. VM counters cover explicit sub-windows and monitoring is active in all conditions. Short screens do not establish significance or general compatibility.

GC collection time is an MXBean total, not individual pause latency. Full counter sub-window bounds and raw capture filenames are recorded in the adjacent JSON.

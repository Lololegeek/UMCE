# Timing and VM counters

Tag: `brain-buffer-v2-complete`; complete paired cycles: 2.
UMCE SHA-256: `02fb03fda1d3693ae50aaa2ea9b82bf1992c399d9c7f215e5ae78d35ed135c73`

Values are medians of per-run values. P95/P99 columns are medians of per-run rolling means.

| Condition | MSPT ms | P95 ms | P99 ms | CPU % one core | Working set MiB | Server alloc MiB/s | GC collections | GC collection ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| without-umce | 40.100 | 50.042 | 56.141 | 96.332 | 1878.539 | 299.747 | 3.500 | 25.000 |
| umce-passive | 37.275 | 48.156 | 53.658 | 90.711 | 1890.838 | 292.606 | 4.000 | 27.500 |
| patch-enabled | 38.550 | 50.412 | 57.302 | 91.402 | 1892.289 | 292.031 | 4.500 | 29.500 |

## Paired changes against passive

| Cycle | MSPT change % | Server allocation change MiB/s |
|---:|---:|---:|
| 1 | +1.084 | +6.517 |
| 2 | +5.710 | -7.668 |

Rolling tick windows overlap. Runs and paired cycles are the units of comparison. VM counters cover explicit sub-windows and monitoring is active in all conditions. Short screens do not establish significance or general compatibility.

GC collection time is an MXBean total, not individual pause latency. Full counter sub-window bounds and raw capture filenames are recorded in the adjacent JSON.

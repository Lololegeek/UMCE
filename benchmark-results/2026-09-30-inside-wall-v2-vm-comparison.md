# Timing and VM counters

Tag: `inside-wall-v2-screen`; complete paired cycles: 2.
UMCE SHA-256: `39c20b645f44b34b53c5ca93f952ed3428aea9634239bb7d6df9aa3819410f5c`

Values are medians of per-run values. P95/P99 columns are medians of per-run rolling means.

| Condition | MSPT ms | P95 ms | P99 ms | CPU % one core | Working set MiB | Server alloc MiB/s | GC collections | GC collection ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| without-umce | 39.775 | 52.767 | 62.693 | 92.760 | 1889.598 | 290.471 | 4.500 | 30.500 |
| umce-passive | 50.400 | 64.778 | 73.170 | 123.013 | 1869.309 | 295.248 | 5.500 | 37.000 |
| patch-enabled | 38.825 | 51.171 | 58.265 | 95.578 | 1884.980 | 292.797 | 5.000 | 34.500 |

## Paired changes against passive

| Cycle | MSPT change % | Server allocation change MiB/s |
|---:|---:|---:|
| 1 | -25.943 | +6.403 |
| 2 | -19.290 | -11.306 |

Rolling tick windows overlap. Runs and paired cycles are the units of comparison. VM counters cover explicit sub-windows and monitoring is active in all conditions. Short screens do not establish significance or general compatibility.

GC collection time is an MXBean total, not individual pause latency. Full counter sub-window bounds and raw capture filenames are recorded in the adjacent JSON.

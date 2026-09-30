# Timing and VM counters

Tag: `inside-wall-v2-validation`; complete paired cycles: 4.
UMCE SHA-256: `39c20b645f44b34b53c5ca93f952ed3428aea9634239bb7d6df9aa3819410f5c`

Values are medians of per-run values. P95/P99 columns are medians of per-run rolling means.

| Condition | MSPT ms | P95 ms | P99 ms | CPU % one core | Working set MiB | Server alloc MiB/s | GC collections | GC collection ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| without-umce | 41.750 | 53.534 | 60.607 | 102.701 | 1862.053 | 285.331 | 4.000 | 32.500 |
| umce-passive | 37.050 | 48.320 | 56.449 | 91.183 | 1860.229 | 293.395 | 5.500 | 36.500 |
| patch-enabled | 40.000 | 52.463 | 62.728 | 96.356 | 1883.457 | 280.063 | 4.500 | 30.000 |

## Paired changes against passive

| Cycle | MSPT change % | Server allocation change MiB/s |
|---:|---:|---:|
| 1 | -3.252 | -5.812 |
| 2 | +2.439 | -10.387 |
| 3 | +14.919 | -22.421 |
| 4 | +0.476 | -18.127 |

Rolling tick windows overlap. Runs and paired cycles are the units of comparison. VM counters cover explicit sub-windows and monitoring is active in all conditions. Short screens do not establish significance or general compatibility.

GC collection time is an MXBean total, not individual pause latency. Full counter sub-window bounds and raw capture filenames are recorded in the adjacent JSON.

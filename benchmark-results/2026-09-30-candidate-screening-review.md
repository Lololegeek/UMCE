# Candidate screenings and diagnostic failures

These uninstrumented screenings were captured on September 29 before interruption. Each used two alternating three-condition cycles, 8 s warm-up and 12 s measurement per run, fixed 2 GiB heap, no tick profiler, and artifact SHA-256 `6181ee353c3d79aca0a796e4aedb26e41e15f1d06a1c855ab91eac390bf59223`.

| Candidate | Workload | Median paired MSPT change vs passive | Better MSPT pairs | CPU passive -> patch (% one core) | Mean P99 passive -> patch (ms) | Decision |
|---|---|---:|---:|---:|---:|---|
| Brain running-task buffer v1 | 2,000 pigs + 500 villagers | +3.21% | 0/2 | 127.5 -> 126.2 | 74.992 -> 70.747 | Reject promotion; revise scratch list implementation |
| Container empty scan | 1,024 hoppers, blocked destinations | +7.14% | 0/2 | 14.4 -> 19.3 | 7.704 -> 7.520 | No reproducible benefit; keep default OFF |
| Hopper full scan | 1,024 hoppers, blocked destinations | -7.50% | 2/2 | 13.4 -> 16.2 | 6.207 -> 6.887 | CPU and tail regressions prevent promotion; investigate with VM counters |

Positive MSPT changes are slower. Hopper workloads were around 1.1-1.7 ms and vanilla tick-query output is quantized to 0.1 ms. Percentage changes therefore amplify tiny absolute differences. These overlapping rolling windows are not independent observations; the paired cycles are the comparison unit. No result establishes significance, zero overhead, modpack compatibility, or a general server speedup.

Detailed comparison reports and CSVs are retained with tags `brain-buffer-screen-1`, `container-screen-1`, and `hopper-screen-1`.

The `hopper-jfr-1` and `hopper-jfr-2` diagnostic attempts stopped after baseline because recording output was empty. The latter's `jcmd` response explicitly said `0 bytes written`. Their checkpoint CSVs are incomplete diagnostics, not paired results; allocations and GC cannot be inferred from them.

A subsequent `brain-buffer-v2-metrics` batch completed cycle 1 but was interrupted during cycle 2. Its checkpoint CSV and the three completed cycle-1 VM captures are preserved separately. A fresh tagged batch is required for validation. The revised Brain code uses the same fastutil list implementation as vanilla through a scoped common reuse helper; its effect is still subject to the new measurements.

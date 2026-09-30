# Server optimization coverage

This is an implementation inventory, not a list of validated gains. No current gameplay patch has passed all acceptance gates across the required workloads for repeatable MSPT/P95/P99 gains without CPU, behavior, or memory regressions. All default to OFF. The rejected suffocation experiment has been corrected and remains manual-only after failing four-cycle performance acceptance.

| System | Current implementation | Remaining work |
|---|---|---|
| Entity lookup / spatial indexing | Small-box section probe over vanilla's index, opt-in | Reproducibility, CPU and tail-latency acceptance |
| Entity ticking / activation / AI | Brain launch-list experiment and reusable running-task snapshot | Revised snapshot screen regressed; broader parity remains unverified; broader ticking and activation are not implemented |
| Villagers / POI | Experimental POI collector | No demonstrated screen gain; POI indexing and villagers remain open |
| Hoppers / inventories / block entities | Independent indexed empty/full scan candidates | Screens and hopper confirmation failed acceptance; full loot/transfer/comparator parity pending; event-driven polling is not implemented |
| Player tracking / networking | Empty-passenger tracking-distance candidate | Negative screen retained; packet batching, serialization and compression are not implemented |
| Collisions / VoxelShape | Corrected eye-centered suffocation scan; real-block parity tests pass; default OFF and manual-only | No broad compatibility or accepted repeatable gain yet; four-cycle confirmation failed speed/CPU/tail acceptance |
| Pathfinding | No new patch | Profile search-local allocations and immutable data reuse first |
| Redstone / scheduled ticks / fluids | No patch | Ordering and gameplay parity require targeted profiles and tests |
| Chunks / loading / unloading / worldgen / lighting | No gameplay patch | Identify bounded snapshot work with precise mutation ownership |
| Saving / serialization / I/O / storage / RegionFiles / NBT | No gameplay patch | Profile buffer allocations and I/O; preserve crash consistency and snapshot lifetime |
| Recipes / selectors / commands / portals | No patch | Identify workload-specific costs before choosing algorithms |
| Startup / shutdown | Existing lifecycle/configuration infrastructure | No new measured runtime optimization |
| Memory / allocations / GC | Common bounded snapshot buffer and indexed scan helper | Inside-wall allocations fell in all four pairs, with worse timing/CPU; no accepted total RAM benefit |
| Multithreading / specialized pools | Existing bounded scheduler infrastructure | No claim of off-thread Minecraft ticking or unknown-mod thread safety |
| GPU / SIMD | No Minecraft compute backend | Real CPU/transfer/compute timings and a correct CPU fallback are prerequisites |
| Versions / loaders | Active hooks above are Fabric 1.21.1 only; common helpers use Java 8 | Other adapters require implementation and independent validation; a version inventory is not runtime support |

The [September 30 validation batch](../benchmark-results/2026-09-30-candidate-validation-review.md) isolated the running-task buffer, container/hopper scans and corrected suffocation loop. No candidate passed overall acceptance. Existing rejected reports and experiments remain available; no candidate is promoted on expectations alone.

# Server optimization coverage

This is an implementation inventory, not a list of validated gains. No current gameplay patch has passed all acceptance gates for repeatable MSPT/P95/P99 gains without CPU, behavior, or memory regressions. All default to OFF; one rejected suffocation experiment is hard-disabled.

| System | Current implementation | Remaining work |
|---|---|---|
| Entity lookup / spatial indexing | Small-box section probe over vanilla's index, opt-in | Reproducibility, CPU and tail-latency acceptance |
| Entity ticking / activation / AI | Brain launch-list experiment and reusable running-task snapshot | Snapshot live parity and measurements; broader ticking and activation are not implemented |
| Villagers / POI | Experimental POI collector | No demonstrated screen gain; POI indexing and villagers remain open |
| Hoppers / inventories / block entities | Independent indexed empty/full scan candidates | Live loot/transfer/comparator parity and allocation/CPU screening; event-driven polling is not implemented |
| Player tracking / networking | Empty-passenger tracking-distance candidate | Negative screen retained; packet batching, serialization and compression are not implemented |
| Collisions / VoxelShape | Behavior-divergent suffocation experiment locked OFF | A behavior-equivalent collision/shape optimization is still needed |
| Pathfinding | No new patch | Profile search-local allocations and immutable data reuse first |
| Redstone / scheduled ticks / fluids | No patch | Ordering and gameplay parity require targeted profiles and tests |
| Chunks / loading / unloading / worldgen / lighting | No gameplay patch | Identify bounded snapshot work with precise mutation ownership |
| Saving / serialization / I/O / storage / RegionFiles / NBT | No gameplay patch | Profile buffer allocations and I/O; preserve crash consistency and snapshot lifetime |
| Recipes / selectors / commands / portals | No patch | Identify workload-specific costs before choosing algorithms |
| Startup / shutdown | Existing lifecycle/configuration infrastructure | No new measured runtime optimization |
| Memory / allocations / GC | Common bounded snapshot buffer and indexed scan helper | Only the running-task and inventory adapters currently use these candidates; total RAM/GC benefits are unmeasured |
| Multithreading / specialized pools | Existing bounded scheduler infrastructure | No claim of off-thread Minecraft ticking or unknown-mod thread safety |
| GPU / SIMD | No Minecraft compute backend | Real CPU/transfer/compute timings and a correct CPU fallback are prerequisites |
| Versions / loaders | Active hooks above are Fabric 1.21.1 only; common helpers use Java 8 | Other adapters require implementation and independent validation; a version inventory is not runtime support |

The current pre-benchmark batch is `brain-running-task-buffer`, `container-empty-scan`, and `hopper-full-scan`. Each should be isolated against passive UMCE, then checked in combination only if independently promising. Existing rejected reports and experiments remain available; no candidate is promoted on expectations alone.

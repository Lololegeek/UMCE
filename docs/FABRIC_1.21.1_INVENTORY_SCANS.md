# Inventory scan candidates: screened, default OFF

Two independent patches are implemented for Fabric 1.21.1. Both default to OFF and are excluded from AUTO. Neither has demonstrated a repeatable overall performance improvement.

| Patch | Changes | Vanilla details preserved |
|---|---|---|
| `container-empty-scan` | Indexes the current array-backed stack list in `LockableContainerBlockEntity.isEmpty` | Original slot order and first nonempty-slot exit; `LootableContainerBlockEntity.isEmpty` still generates loot before invoking its superclass |
| `hopper-full-scan` | Indexes the current hopper stack list in private `HopperBlockEntity.isFull` | Empty stacks are not full; count must equal stack max exactly; overfull stacks are not silently reclassified |

The common Java 8 utility `IndexedListScan` contains the ordered short-circuit loop. The platform predicates remain in the adapter because they depend on Minecraft ItemStack APIs. Neither patch remembers full/empty results across ticks. Every scan reads current stacks, so item-count mutations need no cache invalidation. Destination-full checks, sided slots, transfer order, cooldowns, dirty marking, entity inventory selection, redstone behavior, and loot generation are left to the original game code.

## Evidence reviewed before implementation

The existing hopper-only Java-sampler capture was re-read locally; no new server or benchmark was started. It contains 1,201 recorded ticks and server-thread inclusive weight 17,440. Individual visible stack nodes have weights 84 for hopper `serverTick`/`insertAndExtract`, and 8 for local `isFull` and inherited container `isEmpty` frames. These nested weights must not be added, and they are not millisecond durations. The profile shows small candidate costs, not a demonstrated major bottleneck or allocation saving.

Capture SHA-256: `d542f9b1522d2c57eb7072693b804f350f71b487c20b9c6250e79de061848737`.
The capture is preserved in Git as `benchmark-results/2026-09-27-1.21.1-hoppers-only-before.sparkprofile` at commit `3a1dd16` (blob `5bbd58237d05de7013b8500625c5d18e272c354e`). Its pre-existing working-tree deletion was not restored. The matching local source is under `build/benchmark-1.21.1/runs/with-umce-prepatch-hopper-isolated-r1/config/spark/`.

Minecraft 1.21.1 bytecode was inspected for HopperBlockEntity, LockableContainerBlockEntity, and LootableContainerBlockEntity. It confirms iterator-based scans and superclass loot ordering. Vanilla already caches available slot-index arrays below size 54; no duplicate slot-index cache was added.

## Safety and availability

SAFE/passive startup omits both Mixins. Each experimental option is independently registered through `OptimizationPatch`. Unknown root mods block startup hook selection, using the same conservative base-environment policy as the running-task buffer. Only base Minecraft/Fabric Loader, Fabric API with its nested modules, and UMCE with its nested libraries are permitted initially. This does not claim modpack compatibility.

If a stack list is a subclass of DefaultedList, its scan falls back to vanilla. No workers, caches, GPU work, locks, scans of the world, or persistent per-inventory state are added.

Set `optimization.mode=manual` and enable only the candidate being tested before startup:

```properties
optimization.patch.container-empty-scan=on
optimization.patch.hopper-full-scan=off
```

Swap ON/OFF to isolate the other candidate. Both defaults are OFF, including in the performance profile unless manually changed.

## Completed and pending checks

Compilation, runtime/platform unit tests, and Fabric remapJar passed. Three new common-scanner tests cover order, short-circuit behavior without requesting an iterator, empty lists, in-place changes between calls, and the exact-equality predicate contract. Remapped Mixin selectors were inspected in the generated JAR.

Live Mixin activation was checked in the benchmark harness. Full loot/transfer/comparator parity and modpack compatibility remain unverified.

## Screen and confirmation

The two-cycle `container-screen-1` screen regressed paired MSPT by a median **+7.14%** (better 0/2); CPU increased from 14.4% to 19.3% one core. No VM allocation counters were collected. Keep OFF.

The early hopper screen appeared faster (-7.50%, better 2/2), with worse CPU and P99, so it required confirmation. The four-cycle `hopper-full-validation` batch used 1,024 hoppers, 12 s warm-up, 15 s measurement and identical VM diagnostics. Its median paired MSPT change was **0.00%**, with no faster pair (0%, 0%, 0%, +7.14%).

| Median of per-run values | Passive | Hopper patch |
|---|---:|---:|
| MSPT ms | 1.400 | 1.400 |
| Rolling P95 ms | 2.814 | 2.415 |
| Rolling P99 ms | 7.513 | 7.822 |
| CPU % one core | 7.728 | 9.702 |
| Working set MiB | 1076.152 | 1040.445 |
| Server-thread allocation MiB/s | 6.258 | 6.205 |

Allocation changes have mixed signs; no GC collection occurred in the sampled counter sub-windows. Working-set differences do not establish RAM savings. The generator fills only hopper slot 0 with 64 stone initially; destination chests have all 27 slots full. Hopper contents can redistribute during runs. This tests the generated farm, not a fixture with every hopper slot permanently full.

Keep both patches experimental, default OFF, excluded from AUTO. The [complete hopper timing and VM report](../benchmark-results/2026-09-30-hopper-full-vm-comparison.md) and [original screening review](../benchmark-results/2026-09-30-candidate-screening-review.md) preserve rejected data. Further optimization should target a demonstrable hot path rather than promoting these scan substitutions.

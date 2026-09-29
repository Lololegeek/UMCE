# Inventory scan candidates: compiled, awaiting live validation

Two independent patches are implemented for Fabric 1.21.1. Both default to OFF and are excluded from AUTO. They are candidates for reducing iterator allocation, not measured performance improvements.

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

Live Mixin loading, loot/transfer/comparator parity, performance, allocations, and GC have not been validated. These patches remain experimental. Their added activation checks and predicate calls may outweigh iterator savings; the next step is an isolated screen, followed by rejection or further validation based on measured results.

## Prepared commands: not executed

```powershell
.\tools\benchmark-fabric-stress-1.21.1.ps1 `
  -PatchComparison -InventoryScanPatch container-empty-scan `
  -Players 0 -Entities 0 -Villagers 0 -HopperRows 64 -RedstoneClockPairs 0 `
  -WarmupSeconds 8 -MeasureSeconds 12 -Repeats 2 -QuickStartup `
  -InitialHeap 2G -MaximumHeap 2G -ResultTag container-empty-screen
```

Repeat separately with `-InventoryScanPatch hopper-full-scan` and a different ResultTag. The script requires one candidate per comparison, records the selected inventory patch in CSV metadata, and aborts if status does not show it enabled. Baseline and passive runs select neither patch. A timing screen alone does not validate loot, transfers, allocations, or GC; those need separate live checks.

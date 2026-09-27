# Rejected candidate: empty-passenger tracking distance fast path

## Profile that motivated the patch

A Spark Java-sampler profile of the current adapter used 50 moving clients and 10,062 pigs, with no villagers or configured hopper rows. In its roughly 32-second sample window, `ServerChunkLoadingManager.EntityTracker.updateTrackedStatus` had 25,844 inclusive sample units, and `getMaxTrackDistance` had 18,092. The window covered only 18 server ticks under this severe load; treat it as hotspot discovery, not a stable performance baseline. Spark also timed out while gathering world statistics, but completed and saved the local profile.

## Candidate idea

Vanilla `getMaxTrackDistance` starts with the track distance for the tracked entity, traverses its passenger tree to find a larger passenger track distance, then applies `adjustTrackingDistance`. The patch checks `entity.hasPassengers()` first. When there are no passengers, it returns `adjustTrackingDistance(maxDistance)` directly. When passengers exist, the injection leaves vanilla's entire passenger traversal and result calculation intact.

The proposed fast path would skip only the empty passenger-tree traversal and preserve `adjustTrackingDistance(maxDistance)`. Passenger-bearing entities would retain vanilla's full logic.

This patch skips empty passenger-tree traversal; it does not alter tracking radius, entity visibility, passenger handling, packet contents, or update cadence. It makes no allocation or GPU acceleration claim.

## Evaluation and decision

The implementation was rejected after a four-cycle alternating comparison with 10 players and 10,074 pigs. MSPT was slower in all four pairs: median +6.60% versus diagnostics-only. P95, P99, CPU, and working set also increased in aggregate. Full results and raw samples are in the [patch ablation report](../benchmark-results/2026-09-27-1.21.1-patch-ablation-empty-passenger-track-distance-playertracking-10p-comparison.md) and [CSV samples](../benchmark-results/2026-09-27-1.21.1-patch-ablation-empty-passenger-track-distance-playertracking-10p-samples.csv).

The code and toggle were removed. The profile identified a real hotspot, but this fast path did not improve end-to-end server performance. Do not restore it without changing the approach and measuring a new candidate.

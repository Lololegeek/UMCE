# UMCE

<p align="center"><img src="assets/umce-icon.jpg" alt="UMCE logo" width="220"></p>

**Universal Minecraft Compute & Optimization Engine** is an open-source,
server-first project for measuring Minecraft server workloads and developing
safe, reversible optimizations across Minecraft generations and platforms.

UMCE prioritizes correctness, compatibility, and stability before performance.
An optimization is not enabled just because it is expected to be faster.

## Project status

Current release target: **0.1.0-alpha.2**.

The active server adapter currently targets Fabric on Minecraft 1.21.1 and
provides tick diagnostics and experimental gameplay patches, all disabled by
default. The new [running-task buffer](docs/FABRIC_1.21.1_BRAIN_RUNNING_TASK_BUFFER.md)
has been built and benchmarked, but did not pass performance acceptance. The
[September 30 validation report](benchmark-results/2026-09-30-candidate-validation-review.md)
records the latest three-condition comparisons; none of these candidates is promoted. UMCE's
target is every stable Java release from 1.6.4 onward, including future release
lines, with loader- and generation-specific adapters. The official version
inventory is refreshed from Mojang metadata; inventory entries do not imply
runtime support. Each version/loader combination remains planned until its
adapter and optimizations are implemented and validated.

Current [optimization coverage](docs/SERVER_OPTIMIZATION_COVERAGE.md) and
[inventory scan candidates](docs/FABRIC_1.21.1_INVENTORY_SCANS.md) distinguish
implemented experiments from work still pending.

## Principles

- Unknown code is not assumed to be thread-safe.
- Unknown compatibility is treated conservatively.
- Optional hardware capabilities are reported as unavailable when they cannot
  be detected reliably.
- Benchmarks record measurements and workload details; they do not invent
  performance gains.
- Optimizations can be isolated, disabled, and given a safe fallback.

## Build

The Fabric 1.21.1 build needs JDK 25 to run Gradle with Loom 1.18.2, plus JDK
21 installed for the Minecraft toolchain. The shared API, runtime, and core
artifacts target Java 8 bytecode.

## First server adapter

UMCE currently has one runtime adapter: a Fabric server artifact for exactly
Minecraft 1.21.1. It records tick durations and exposes a permission-gated
`/umce status` command. No gameplay optimization is enabled. The paired stress
harness and latest three-pair measurements are in
[`benchmark-results/2026-09-26-1.21.1-stress-runtime-split-3pairs-comparison.md`](benchmark-results/2026-09-26-1.21.1-stress-runtime-split-3pairs-comparison.md).
The first local Spark stress profile and its mapped server-thread hot paths are
summarized in
[`benchmark-results/2026-09-26-1.21.1-spark-stress-profile.md`](benchmark-results/2026-09-26-1.21.1-spark-stress-profile.md).
The harness uses real protocol clients and identical saved worlds. Create is
not included because its 1.21.1 release targets NeoForge, while this adapter
targets Fabric. Optimization support and test status are tracked separately
for each Minecraft version and loader; the CLI lists the official stable
release inventory without labeling unimplemented targets as supported.

## License

UMCE is licensed under **GPL-3.0-only**. See [LICENSE](LICENSE). The CLI uses
Gson under Apache-2.0; its license and attribution are recorded in
[`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md).

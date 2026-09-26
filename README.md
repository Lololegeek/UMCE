# UMCE

<p align="center"><img src="assets/umce-icon.jpg" alt="UMCE logo" width="220"></p>

**Universal Minecraft Compute & Optimization Engine** is an open-source,
server-first project for measuring Minecraft server workloads and developing
safe, reversible optimizations across Minecraft generations and platforms.

UMCE prioritizes correctness, compatibility, and stability before performance.
An optimization is not enabled just because it is expected to be faster.

## Project status

Current release target: **0.1.0-alpha.2**.

The supported Minecraft version ceiling is **1.21.1**. The active server
adapter targets Fabric on Minecraft 1.21.1 and provides tick diagnostics; it
does not enable gameplay optimizations. Runtime support for other versions,
loaders, or optimizations will be reported only after their implementation and
behavior have been validated.

## Principles

- Unknown code is not assumed to be thread-safe.
- Unknown compatibility is treated conservatively.
- Optional hardware capabilities are reported as unavailable when they cannot
  be detected reliably.
- Benchmarks record measurements and workload details; they do not invent
  performance gains.
- Optimizations can be isolated, disabled, and given a safe fallback.

## Build

Requires JDK 21 or newer for the Fabric 1.21.1 build. The shared API/core
artifacts still target Java 8 bytecode.

## First server adapter

UMCE currently has one runtime adapter: a Fabric server artifact for exactly
Minecraft 1.21.1. It records tick durations and exposes a permission-gated
`/umce status` command. No gameplay optimization is enabled. The paired stress
harness and measurements are in
[`benchmark-results/2026-09-26-1.21.1-stress-comparison.md`](benchmark-results/2026-09-26-1.21.1-stress-comparison.md).
The harness uses real protocol clients and identical saved worlds. Create is
not included because its 1.21.1 release targets NeoForge, while this adapter
targets Fabric.

## License

MIT. See [LICENSE](LICENSE).

# UMCE

**Universal Minecraft Compute & Optimization Engine** is an open-source,
server-first project for measuring Minecraft server workloads and developing
safe, reversible optimizations across Minecraft generations and platforms.

UMCE prioritizes correctness, compatibility, and stability before performance.
An optimization is not enabled just because it is expected to be faster.

## Project status

UMCE is at its foundation stage. The repository is being built as a modular
Java project. Runtime support for a Minecraft version, loader, or optimization
will be reported only after its adapter and behavior have been implemented and
validated. The long-term target is every stable Java Edition release from 1.6.4
through 26.3, with future releases added from official version metadata.

## Principles

- Unknown code is not assumed to be thread-safe.
- Unknown compatibility is treated conservatively.
- Optional hardware capabilities are reported as unavailable when they cannot
  be detected reliably.
- Benchmarks record measurements and workload details; they do not invent
  performance gains.
- Optimizations can be isolated, disabled, and given a safe fallback.

## Build

Requirements and exact commands will be documented alongside the Gradle
wrapper and the first executable modules.

## License

MIT. See [LICENSE](LICENSE).


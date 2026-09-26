# UMCE

**Universal Minecraft Compute & Optimization Engine** is an open-source,
server-first project for measuring Minecraft server workloads and developing
safe, reversible optimizations across Minecraft generations and platforms.

UMCE prioritizes correctness, compatibility, and stability before performance.
An optimization is not enabled just because it is expected to be faster.

## Project status

Current release target: **0.1.0-alpha.1**.

UMCE has a modular Java foundation and one smoke-tested server adapter: Fabric
for Minecraft 26.3. The adapter reports host details and tick timings; it does
not enable gameplay optimizations. Runtime support for other versions, loaders,
or optimizations will be reported only after their implementation and behavior
have been validated. The long-term target is every stable Java Edition release
from 1.6.4 through 26.3, with future releases added from official metadata.

## Principles

- Unknown code is not assumed to be thread-safe.
- Unknown compatibility is treated conservatively.
- Optional hardware capabilities are reported as unavailable when they cannot
  be detected reliably.
- Benchmarks record measurements and workload details; they do not invent
  performance gains.
- Optimizations can be isolated, disabled, and given a safe fallback.

## Build

Requires JDK 25 or newer for the Fabric 26.3 build plugin. The shared API/core
artifacts still target Java 8 bytecode.

## First server adapter

UMCE currently has one runtime adapter: a Fabric server artifact for exactly
Minecraft 26.3. It reports host details, records tick durations, and provides a
permission-gated `/umce status`, `/umce hardware`, `/umce profile`,
`/umce compat`, `/umce mods`, `/umce memory`, `/umce gpu`, `/umce workers`,
and `/umce reload` command set. `/umce help` is available to all operators.
The mod list is inventory only; it does not claim mod compatibility. The GPU
backend and server scheduler are not attached yet. No gameplay optimization is
enabled.
Run the real server smoke test with
[`tools/test-server-26.3-fabric.ps1`](tools/test-server-26.3-fabric.ps1).

## License

MIT. See [LICENSE](LICENSE).

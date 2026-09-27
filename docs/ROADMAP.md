# UMCE roadmap

This roadmap describes implementation stages, not current support claims. The
target covers every stable Minecraft Java release from 1.6.4 onward and future
releases. Runtime support remains explicit per version, loader, and adapter
generation.

## Foundations

- [x] Modular Gradle build and public API
- [x] Official Minecraft release metadata registry
- [x] Platform adapter SPI and capability status model
- [x] Hardware and JVM diagnostics
- [x] Conservative compatibility and reversible patch lifecycle
- [x] Configuration, profiling, benchmark schema, and bounded scheduler
- [x] CLI for diagnosis, metadata refresh, and controlled benchmarks

## First platform integration

- [x] Select Fabric server and Minecraft 1.21.1
- [x] Implement lifecycle, tick sampling, and diagnostics hooks
- [x] Package the adapter and automate a dedicated-server smoke run
- [x] Keep the platform and game-version scope explicit
- [x] Add an opt-in entity-section probe patch for Fabric 1.21.1, measured on a 10k-entity workload

## Expansion

- Add optimization modules only after representative correctness tests and
  repeatable workload measurements
- Add loader/version adapters as isolated artifacts
- Add per-loader adapter metadata and compatibility state without inferring
  support from another loader's status
- Grow the stable-version and loader matrix from metadata and verified
  integrations; do not infer support from a version being listed
- Explore asynchronous and GPU work only when safety and end-to-end benefit are
  demonstrated

The Fabric adapter currently registers one opt-in gameplay patch, scoped to
Minecraft 1.21.1. The empty-passenger entity-tracking fast path was profiled
and then removed after it regressed all four test pairs. The CPU scheduler exists in
the platform-neutral core but is not wired to Fabric gameplay tasks. GPU
compute remains unprobed and unused; no safe vanilla workload has yet shown an
end-to-end GPU benefit.

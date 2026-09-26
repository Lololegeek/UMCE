# UMCE roadmap

This roadmap describes implementation stages, not current support claims.

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

## Expansion

- Add optimization modules only after representative correctness tests and
  repeatable workload measurements
- Add loader/version adapters as isolated artifacts
- Grow the stable-version and loader matrix from metadata and verified
  integrations; do not infer support from a version being listed
- Explore asynchronous and GPU work only when safety and end-to-end benefit are
  demonstrated

No gameplay optimization has been implemented or benchmarked yet. The Fabric
adapter validates server integration and tick observation only.

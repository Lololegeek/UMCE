# UMCE roadmap

This roadmap describes implementation stages, not current support claims.

## Foundations

- Modular Gradle build and public API
- Official Minecraft release metadata registry
- Platform adapter SPI and capability status model
- Hardware and JVM diagnostics
- Conservative compatibility and patch lifecycle
- Configuration, profiling, benchmark schema, and bounded scheduler
- CLI for diagnosis, metadata refresh, and controlled benchmarks

## First platform integration

- Select one server platform and one supported Minecraft version
- Implement real lifecycle, tick sampling, and diagnostics hooks
- Package an installable artifact and automate a dedicated-server smoke run
- Keep the platform and game-version scope explicit

## Expansion

- Add optimization modules only after representative correctness tests and
  repeatable workload measurements
- Add loader/version adapters as isolated artifacts
- Grow the stable-version and loader matrix from metadata and verified
  integrations; do not infer support from a version being listed
- Explore asynchronous and GPU work only when safety and end-to-end benefit are
  demonstrated


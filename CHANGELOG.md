# Changelog

## 0.1.0-alpha.2

- Target the server adapter at Minecraft 1.21.1 with Java 21.
- Add a paired 1.21.1 Fabric stress harness with real protocol clients and a
  saved, verifiable entity workload.
- Keep all gameplay optimizations disabled.

## 0.1.0-alpha.1

- Add a Fabric server adapter for Minecraft 26.3.
- Record server tick timings and expose permission-gated status, hardware,
  profiling, compatibility, mod inventory, memory, GPU, worker, and config
  reload commands.
- Add a CLI for hardware reports, Minecraft release metadata, compatibility
  foundations, and local Java microbenchmarks.
- Keep optimization patches disabled. No gameplay speedup is implemented or
  claimed in this alpha.
- Require Java 25 for the Minecraft 26.3 Fabric adapter.

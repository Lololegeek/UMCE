# UMCE

UMCE is a server-first Minecraft Java framework for diagnostics, tick profiling,
compatibility tracking, and carefully validated optimizations.

## Alpha support

This alpha targets Minecraft Java Edition 26.3 with Fabric Loader 0.19.5 and
Fabric API 0.161.0+26.3. It requires Java 25 or newer. This exact server setup
has been smoke-tested; other Minecraft versions and loaders are not supported
yet.

## Included

- Rolling server tick-duration samples and percentile reports.
- Host, JVM, heap, and loaded-mod inventory commands.
- A local CLI for hardware reports, official Minecraft release metadata, and
  reproducible local Java microbenchmarks.
- Conservative compatibility and reversible patch foundations.

Install the JAR in the Fabric server's `mods` directory alongside Fabric API.
Use `/umce help` for commands. Administrative diagnostics and config reload
commands require operator permission.

## Not implemented in this alpha

UMCE does not yet change Minecraft gameplay behavior or claim performance
improvements. GPU compute, server scheduler integration, dashboard, and
per-mod compatibility results are not available yet. Unknown compatibility
keeps optimization patches disabled.

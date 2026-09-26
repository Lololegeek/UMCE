# UMCE

UMCE is a server-first Minecraft Java framework for diagnostics, tick profiling,
compatibility tracking, and carefully validated optimizations.

UMCE is licensed under GPL-3.0-only. The CLI uses Gson under Apache-2.0; see
the repository's third-party notices. The server adapter contains the shared
API and a small Java 8 runtime module; it does not package the CLI core or Gson.

## Alpha support

This alpha targets Minecraft Java Edition 1.21.1 with Fabric Loader 0.16.14
and Fabric API 0.116.17+1.21.1. It requires Java 21 or newer. This exact server
setup has been smoke-tested; other Minecraft versions and loaders are not
supported yet.

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

UMCE currently records diagnostics and tick timings. It does not change
Minecraft gameplay behavior or claim performance improvements. Create is not
included: the 1.21.1 release targets NeoForge, while this adapter targets
Fabric. Unknown compatibility keeps optimization patches disabled.

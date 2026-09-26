# Initial architecture

The repository uses three Java 8 bytecode modules so the platform-neutral API
and core can be reused by platform artifacts. The build uses the Gradle wrapper;
individual adapters select the JDK and bytecode level required by their
Minecraft release.

- `api`: stable-facing value types and SPIs; no Minecraft implementation
  dependency.
- `runtime`: small Java 8 server runtime shared by platform adapters. It holds
  the tick profiler currently used by the Fabric adapter.
- `core`: metadata, compatibility, diagnostics, configuration, benchmarking,
  and scheduling services.
- `cli`: operator-facing diagnostics and explicit measurement commands.
- `platforms/fabric-1.21.1`: server-only Fabric adapter for Minecraft 1.21.1.
  It packages only `api` and `runtime`, records tick durations, and exposes a
  permission-gated diagnostics command. It targets Java 21 and does not alter
  gameplay behavior.
- `platforms/*`: future loader-specific adapters, separately packaged and
  dependent on the API and only the shared runtime pieces they use.

Minecraft release metadata is inventory, not proof of compatibility. Loader
and optimization support must come from a verified adapter catalog. Fields
which Mojang does not publish in its version metadata remain explicitly
unknown until a trustworthy source is added. The current catalog has one
verified target, Fabric Loader 0.16.14 on Minecraft 1.21.1; all other
combinations remain unknown or planned.

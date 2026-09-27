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
  It packages `api`, `runtime`, and `core`, records tick durations, and exposes
  permission-gated diagnostics and reversible patch commands. The current
  gameplay patch is opt-in. It targets
  Java 21 bytecode while its Gradle build requires Java 25.
- `platforms/*`: future loader-specific adapters, separately packaged and
  dependent on the API and only the shared runtime pieces they use.

The bounded CPU scheduler is currently a core capability with no Fabric
gameplay caller. GPU compute is not detected or used. World mutation,
collision resolution, entity tracking state, and vanilla chunk-generation
callbacks remain on Minecraft's owning threads unless an end-to-end tested
loader/version integration proves a safe split.

Minecraft release metadata is inventory, not proof of compatibility. Loader
and optimization support must come from a verified adapter catalog. Fields
which Mojang does not publish in its version metadata remain explicitly
unknown until a trustworthy source is added. The current catalog has one
verified target, Fabric Loader 0.16.14 on Minecraft 1.21.1; all other
combinations remain unknown or planned.

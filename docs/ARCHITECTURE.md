# Initial architecture

The repository starts as three Java 8 bytecode modules so the platform-neutral
API and core can be loaded by a wide range of server runtimes. The build itself
uses the Gradle wrapper and the installed JDK; a platform adapter may require a
newer toolchain and will be isolated from these modules.

- `api`: stable-facing value types and SPIs; no Minecraft implementation
  dependency.
- `core`: metadata, compatibility, diagnostics, configuration, profiling,
  benchmarking, and scheduling services.
- `cli`: operator-facing diagnostics and explicit measurement commands.
- `platforms/*` (future): loader-specific, separately packaged integration
  artifacts depending on the API and core.

Minecraft release metadata is inventory, not proof of compatibility. Loader
and optimization support must come from a verified adapter catalog. Fields
which Mojang does not publish in its version metadata remain explicitly
unknown until a trustworthy source is added.


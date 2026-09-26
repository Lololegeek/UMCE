# Getting started

## Build

Install a JDK 17 or newer and run:

```powershell
./gradlew.bat build
```

The shared API and core emit Java 8 bytecode so they can be reused by
platform-specific artifacts. This does not mean that one artifact supports all
Minecraft runtimes or that an adapter exists for every listed version.

## Commands

```powershell
./gradlew.bat :cli:run --args="doctor"
./gradlew.bat :cli:run --args="versions --details 26.3"
./gradlew.bat :cli:run --args="compat"
./gradlew.bat :cli:run --args="benchmark --operations 1000000 --json build/benchmark.json"
```

`versions` reads Mojang's live version manifest. It can list every entry tagged
as a stable release and fetch one version's launcher metadata on demand. The
launcher metadata does not contain protocol/data versions or a loader matrix;
UMCE leaves those fields unknown rather than filling them from guesses.

`doctor` reports facts visible to the current JVM. GPU compute detection and
Minecraft server integration are not implemented yet. The benchmark command
measures a local integer workload and records the raw sample checksums and
host details. It is not a Minecraft benchmark and does not claim a speedup.


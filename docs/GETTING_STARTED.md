# Getting started

## Build

Install JDK 25 and JDK 21. Gradle and Fabric Loom run on JDK 25; the Fabric
1.21.1 adapter compiles with the JDK 21 toolchain. Set `JAVA_HOME` to JDK 25,
then run:

```powershell
./gradlew.bat build
```

The shared API, runtime, and core emit Java 8 bytecode. The current Minecraft
runtime adapter supports exactly 1.21.1; the release catalog lists broader
version inventory without implying support for unimplemented combinations.

## Commands

```powershell
./gradlew.bat :cli:run --args="doctor"
./gradlew.bat :cli:run --args="versions --details 1.21.1"
./gradlew.bat :cli:run --args="compat"
./gradlew.bat :cli:run --args="benchmark --operations 1000000 --json build/benchmark.json"
```

`versions` reads Mojang's live version manifest. It can list every entry tagged
as a stable release and fetch one version's launcher metadata on demand. The
launcher metadata does not contain protocol/data versions or a loader matrix;
UMCE leaves those fields unknown rather than filling them from guesses. A
separate checked-in catalog reports the adapter and loader combinations that
have actually been verified.

`doctor` reports facts visible to the current JVM. GPU compute probing is not
implemented yet. The benchmark command measures a local integer workload and
records the raw sample checksums and host details. It is not a Minecraft
benchmark and does not claim a speedup.

## Minecraft 1.21.1 Fabric adapter

The server artifact targets Minecraft 1.21.1 with Fabric Loader 0.16.14 and
Fabric API 0.116.17+1.21.1. It records server tick durations and exposes a
permission-gated `/umce status` command. It does not change tick behavior or
enable an optimization patch.

Run the paired server stress workload from PowerShell:

```powershell
./tools/benchmark-fabric-stress-1.21.1.ps1 -Players 100 -Entities 10000 -Villagers 500 -MeasureSeconds 60 -Repeats 3
```

It compares identical saved worlds with and without UMCE, uses Mineflayer TCP
clients, and writes a Markdown report plus raw sample CSV under
`benchmark-results/`. It validates the saved pig and villager counts before
starting the comparison. Create is omitted because the 1.21.1 release targets
NeoForge, while this adapter targets Fabric.

To capture a separate local Spark CPU profile of the same UMCE workload without
mixing it into the paired benchmark measurements, run:

```powershell
./tools/benchmark-fabric-stress-1.21.1.ps1 -ProfileOnly -ResultTag profiling -Players 100 -Entities 10000 -Villagers 500 -MeasureSeconds 60
```

It obtains a Fabric 1.21.1 Spark release from Modrinth, verifies its SHA-512,
then starts a server-thread profile after the 20-second warm-up. Spark saves the
profile locally in the isolated run directory; the command does not upload it.
Sampling adds overhead, so use it to identify candidate hot paths, not as a
performance result.

# Getting started

## Build

Install a JDK 25 or newer and run:

```powershell
./gradlew.bat build
```

The shared API and core emit Java 8 bytecode so they can be reused by
platform-specific artifacts. The current Fabric 26.3 build plugin itself needs
a Java 25 Gradle runtime. The bytecode target does not mean that one artifact
supports all Minecraft runtimes or that an adapter exists for every listed
version.

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
UMCE leaves those fields unknown rather than filling them from guesses. A
separate checked-in catalog reports the adapter and loader combinations that
have actually been verified.

`doctor` reports facts visible to the current JVM. GPU compute probing is not
implemented yet. The benchmark command measures a local integer workload and
records the raw sample checksums and host details. It is not a Minecraft
benchmark and does not claim a speedup.

## Minecraft 26.3 Fabric adapter

The first server artifact targets exactly Minecraft 26.3 with Fabric Loader
0.19.5 and Fabric API 0.161.0+26.3. It registers a permission-gated
`/umce status`, `/umce hardware`, `/umce profile`, `/umce compat`, `/umce mods`,
`/umce memory`, `/umce gpu`, `/umce workers`, and `/umce reload` command set;
`/umce help` is public. The mods command lists loaded mod IDs and versions,
while the compatibility command explicitly leaves the mod/patch matrix unknown.
Memory reports JVM heap use. GPU reports that no backend is installed, and
workers reports configured limits while the server scheduler is not attached.
Reload validates and swaps the in-memory configuration snapshot. GPU and
dashboard settings do not start backends yet. The adapter samples server tick
durations and does not change Minecraft tick behavior or enable an optimization
patch.

Run the dedicated smoke scenario from PowerShell with a Java 25 JDK:

```powershell
./tools/test-server-26.3-fabric.ps1 -JavaHome $env:JAVA_HOME
```

The script uses a loopback-only, offline-mode server in the ignored
`platforms/fabric-26.3/build/server-run` directory. It writes the test EULA,
waits for Fabric and UMCE command registration, queries every registered
command, edits the isolated test profile and verifies `/umce reload` sees it,
then sends `stop` and checks that tick samples were recorded. Its log is written
to `build/test-server-26.3-fabric.log`.

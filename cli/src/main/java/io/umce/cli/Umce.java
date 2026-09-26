package io.umce.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonNull;
import com.google.gson.GsonBuilder;
import io.umce.api.compat.CompatibilityStatus;
import io.umce.api.version.MinecraftRelease;
import io.umce.core.benchmark.BenchmarkResult;
import io.umce.core.benchmark.BenchmarkRunner;
import io.umce.core.benchmark.BenchmarkTask;
import io.umce.core.hardware.HardwareDetector;
import io.umce.core.hardware.HardwareProfile;
import io.umce.core.version.MinecraftVersionRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Umce {
    private Umce() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0]) || "--help".equals(args[0])) {
            printHelp();
            return;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "doctor":
                doctor();
                return;
            case "versions":
                versions(args);
                return;
            case "compat":
                if (args.length != 1) throw new IllegalArgumentException("Usage: umce compat");
                System.out.println("Compatibility: " + CompatibilityStatus.UNKNOWN);
                System.out.println("No Minecraft server is attached to this CLI invocation.");
                System.out.println("Unknown compatibility keeps optimization patches disabled.");
                return;
            case "benchmark":
                benchmark(args);
                return;
            default:
                throw new IllegalArgumentException("Unknown command: " + args[0] + " (run 'umce help')");
        }
    }

    private static void doctor() {
        HardwareProfile hardware = new HardwareDetector().detect();
        System.out.println("UMCE Doctor - local JVM and host report");
        System.out.println("OS: " + hardware.getOperatingSystem() + " / " + hardware.getArchitecture());
        System.out.println("JVM: " + hardware.getJvmName() + " " + hardware.getJvmVersion());
        System.out.println("Logical processors: " + hardware.getLogicalProcessors());
        System.out.println("Maximum heap: " + hardware.getMaxHeapBytes() + " bytes");
        System.out.println("Physical memory: " + (hardware.getPhysicalMemoryBytes().isPresent()
                ? hardware.getPhysicalMemoryBytes().getAsLong() + " bytes" : "unknown"));
        System.out.println("CPU model: " + (hardware.getCpuModelName().isPresent()
                ? hardware.getCpuModelName().get() : "unknown"));
        System.out.println("Garbage collectors: " + hardware.getGarbageCollectors());
        System.out.println("GPU compute: " + hardware.getGpuComputeProbe());
        System.out.println("Minecraft platform: not attached");
        System.out.println("Compatibility: UNKNOWN; optimizations remain disabled");
    }

    private static void versions(String[] args) throws Exception {
        MinecraftVersionRegistry registry = new MinecraftVersionRegistry();
        java.util.List<MinecraftRelease> releases = registry.refresh();
        System.out.println("Official stable Minecraft Java releases: " + releases.size());
        int verifiedTargets = 0;
        if (!releases.isEmpty()) {
            MinecraftRelease latest = releases.get(0);
            System.out.println("Newest release in manifest: " + latest.getId()
                    + " (" + latest.getReleaseDate().get() + ")");
        }
        System.out.println("1.6.4 indexed: " + contains(releases, "1.6.4"));
        System.out.println("1.21.1 target indexed: " + contains(releases, "1.21.1"));
        for (MinecraftRelease release : releases) {
            if (!release.getAdapterId().isPresent()) continue;
            verifiedTargets++;
            System.out.println("Verified adapter: Minecraft " + release.getId() + " / "
                    + formatLoaders(release) + " / " + release.getAdapterId().get()
                    + " / test " + release.getTestStatus());
        }
        System.out.println("Verified adapter targets: " + verifiedTargets
                + ". Other version/loader combinations remain PLANNED.");

        if (args.length == 3 && "--details".equals(args[1])) {
            MinecraftRelease details = registry.getDetails(args[2]);
            System.out.println("Version: " + details.getId());
            System.out.println("Required Java: " + (details.getRequiredJavaVersion().isPresent()
                    ? details.getRequiredJavaVersion().getAsInt() : "not published in this metadata"));
            System.out.println("Server jar: " + (details.getServerJar().isPresent()
                    ? details.getServerJar().get() : "not listed"));
            System.out.println("Adapter: " + details.getAdapterId().orElse("none verified"));
            System.out.println("Loaders: " + formatLoaders(details));
            System.out.println("Optimization support: " + details.getOptimizationSupport());
            System.out.println("Compatibility: " + details.getCompatibilityStatus());
            System.out.println("Adapter test: " + details.getTestStatus());
            System.out.println("Protocol version: not published in this metadata");
            System.out.println("Data version: not published in this metadata");
        } else if (args.length == 2 && "--list".equals(args[1])) {
            for (MinecraftRelease release : releases) System.out.println(release.getId());
        } else if (args.length != 1) {
            throw new IllegalArgumentException("Usage: umce versions [--list | --details <version>]");
        }
    }

    private static boolean contains(java.util.List<MinecraftRelease> releases, String version) {
        for (MinecraftRelease release : releases) if (version.equals(release.getId())) return true;
        return false;
    }

    private static String formatLoaders(MinecraftRelease release) {
        if (release.getSupportedLoaders().isEmpty()) return "none verified";
        java.util.List<String> values = new java.util.ArrayList<String>();
        for (io.umce.api.platform.LoaderId loader : release.getSupportedLoaders()) {
            java.util.Set<String> versions = release.getLoaderVersions().get(loader);
            values.add(loader.getId() + (versions == null || versions.isEmpty() ? "" : " " + versions));
        }
        return String.join(", ", values);
    }

    private static void benchmark(String[] args) throws Exception {
        int operations = 1_000_000;
        Path output = null;
        for (int index = 1; index < args.length; index++) {
            if ("--operations".equals(args[index]) && index + 1 < args.length) {
                operations = Integer.parseInt(args[++index]);
            } else if ("--json".equals(args[index]) && index + 1 < args.length) {
                output = Paths.get(args[++index]).toAbsolutePath();
            } else {
                throw new IllegalArgumentException("Usage: umce benchmark [--operations <count>] [--json <file>]");
            }
        }

        BenchmarkTask task = new BenchmarkTask() {
            @Override
            public long run(int count) {
                long state = 0x4d4345554d43454cL;
                for (int i = 0; i < count; i++) {
                    state ^= state << 13;
                    state ^= state >>> 7;
                    state ^= state << 17;
                    state += i * 0x9e3779b97f4a7c15L;
                }
                return state;
            }
        };
        BenchmarkResult result = new BenchmarkRunner().run("local-integer-mix", operations, 3, 7, task);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(toJson(result));
        if (output == null) {
            System.out.println(json);
        } else {
            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(output, json.getBytes(StandardCharsets.UTF_8));
            System.out.println("Benchmark record written: " + output);
            System.out.println("Observed median: " + result.getMedianOperationsPerSecond() + " operations/s");
            System.out.println("This is a local Java workload, not a Minecraft-server performance claim.");
        }
    }

    private static JsonObject toJson(BenchmarkResult result) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", result.getSchema());
        root.addProperty("scenario", result.getScenario());
        root.addProperty("startedAtEpochMillis", result.getStartedAtEpochMillis());
        root.addProperty("operationsPerSample", result.getOperationsPerSample());
        root.addProperty("warmupIterations", result.getWarmupIterations());
        root.addProperty("medianElapsedNanos", result.getMedianElapsedNanos());
        root.addProperty("p95ElapsedNanos", result.getP95ElapsedNanos());
        root.addProperty("medianOperationsPerSecond", result.getMedianOperationsPerSecond());

        HardwareProfile hardware = result.getHardware();
        JsonObject host = new JsonObject();
        host.addProperty("operatingSystem", hardware.getOperatingSystem());
        host.addProperty("architecture", hardware.getArchitecture());
        host.addProperty("jvmName", hardware.getJvmName());
        host.addProperty("jvmVersion", hardware.getJvmVersion());
        host.addProperty("logicalProcessors", hardware.getLogicalProcessors());
        host.addProperty("maxHeapBytes", hardware.getMaxHeapBytes());
        host.add("physicalMemoryBytes", hardware.getPhysicalMemoryBytes().isPresent()
                ? new com.google.gson.JsonPrimitive(hardware.getPhysicalMemoryBytes().getAsLong()) : JsonNull.INSTANCE);
        host.add("cpuModelName", hardware.getCpuModelName().isPresent()
                ? new com.google.gson.JsonPrimitive(hardware.getCpuModelName().get()) : JsonNull.INSTANCE);
        host.addProperty("gpuComputeProbe", hardware.getGpuComputeProbe());
        JsonArray collectors = new JsonArray();
        for (String collector : hardware.getGarbageCollectors()) collectors.add(collector);
        host.add("garbageCollectors", collectors);
        root.add("hardware", host);

        JsonArray samples = new JsonArray();
        for (io.umce.core.benchmark.BenchmarkSample sample : result.getSamples()) {
            JsonObject jsonSample = new JsonObject();
            jsonSample.addProperty("elapsedNanos", sample.getElapsedNanos());
            jsonSample.addProperty("resultChecksum", sample.getResultChecksum());
            samples.add(jsonSample);
        }
        root.add("samples", samples);
        return root;
    }

    private static void printHelp() {
        System.out.println("UMCE foundation CLI");
        System.out.println("  umce doctor");
        System.out.println("  umce versions [--list | --details <version>]");
        System.out.println("  umce compat");
        System.out.println("  umce benchmark [--operations <count>] [--json <file>]");
    }
}

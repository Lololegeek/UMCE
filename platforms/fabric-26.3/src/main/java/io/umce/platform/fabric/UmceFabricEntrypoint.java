package io.umce.platform.fabric;

import io.umce.api.version.MinecraftRelease;
import io.umce.core.config.ConfigStore;
import io.umce.core.config.UmceConfig;
import io.umce.core.hardware.HardwareDetector;
import io.umce.core.hardware.HardwareProfile;
import io.umce.core.profiler.ProfileSnapshot;
import io.umce.core.profiler.TickProfiler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class UmceFabricEntrypoint implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("UMCE");
    private static final TickProfiler TICK_PROFILER = new TickProfiler(3_600);
    private static volatile long tickStartNanos;
    private static volatile PlatformAdapterReference adapterReference;
    private static volatile HardwareProfile activeHardware;
    private static volatile ConfigStore activeConfigStore;
    private static volatile UmceConfig activeConfig;

    @Override
    public void onInitialize() {
        String loaderVersion = FabricLoader.getInstance().getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        LOGGER.info("UMCE Fabric adapter loaded for Minecraft 26.3 (Fabric Loader {})", loaderVersion);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> onServerStarted(server, loaderVersion));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> logFinalProfile());
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStartNanos = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long started = tickStartNanos;
            tickStartNanos = 0L;
            if (started > 0L) TICK_PROFILER.recordTick(Math.max(0L, System.nanoTime() - started));
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("umce")
                    .then(Commands.literal("help")
                            .executes(context -> sendHelp(context.getSource())))
                    .then(adminCommand("status", UmceFabricEntrypoint::sendStatus))
                    .then(adminCommand("hardware", UmceFabricEntrypoint::sendHardware))
                    .then(adminCommand("profile", UmceFabricEntrypoint::sendProfile))
                    .then(adminCommand("compat", UmceFabricEntrypoint::sendCompatibility))
                    .then(adminCommand("mods", UmceFabricEntrypoint::sendMods))
                    .then(adminCommand("reload", UmceFabricEntrypoint::reloadConfig)));
            LOGGER.info("UMCE admin commands registered: /umce status, hardware, profile, compat, mods, reload");
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> adminCommand(
            String name, java.util.function.ToIntFunction<CommandSourceStack> action) {
        return Commands.literal(name)
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .executes(context -> action.applyAsInt(context.getSource()));
    }

    private static void onServerStarted(MinecraftServer server, String loaderVersion) {
        FabricPlatformAdapter adapter = new FabricPlatformAdapter(server, loaderVersion);
        adapterReference = new PlatformAdapterReference(adapter);
        HardwareProfile hardware = new HardwareDetector().detect();
        activeHardware = hardware;
        LOGGER.info("UMCE adapter active: Minecraft {}, Fabric Loader {}", adapter.getMinecraftRelease().getId(), loaderVersion);
        LOGGER.info("UMCE host: {} {}, {} logical processors, max heap {} bytes, GC {}",
                hardware.getOperatingSystem(), hardware.getArchitecture(), hardware.getLogicalProcessors(),
                hardware.getMaxHeapBytes(), hardware.getGarbageCollectors());
        LOGGER.info("UMCE GPU compute: {}; no GPU work is enabled", hardware.getGpuComputeProbe());

        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("umce.properties");
        try {
            activeConfigStore = new ConfigStore(configFile, hardware.getLogicalProcessors());
            UmceConfig config = activeConfigStore.loadOrCreate();
            activeConfig = config;
            LOGGER.info("UMCE configuration loaded: profile={}, CPU workers={}, GPU={}, dashboard={}",
                    config.getProfile(), config.getCpuWorkers(), config.isGpuEnabled(), config.isDashboardEnabled());
        } catch (IOException exception) {
            LOGGER.error("UMCE configuration is invalid; the diagnostics adapter remains active with optimizations disabled", exception);
        }
    }

    private static int sendStatus(CommandSourceStack source) {
        PlatformAdapterReference current = adapterReference;
        if (current == null) {
            source.sendSuccess(() -> Component.literal("UMCE adapter is waiting for server startup"), false);
            return 0;
        }
        MinecraftRelease release = current.adapter.getMinecraftRelease();
        ProfileSnapshot profile = TICK_PROFILER.snapshot();
        UmceConfig config = activeConfig;
        String message = String.format(java.util.Locale.ROOT,
                "UMCE | MC %s | %s %s | profile %s | compatibility %s | tick samples %d | mean %.3f ms | p95 %.3f ms | p99 %.3f ms",
                release.getId(), current.adapter.getPlatformId(), current.adapter.getLoaderVersion(),
                config == null ? "unavailable" : config.getProfile(), release.getCompatibilityStatus(),
                profile.getSampleCount(), profile.getMeanMilliseconds(),
                profile.getP95Milliseconds(), profile.getP99Milliseconds());
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "UMCE commands: /umce status, /umce hardware, /umce profile, /umce compat, /umce mods, /umce reload"), false);
        return 1;
    }

    private static int sendHardware(CommandSourceStack source) {
        HardwareProfile hardware = activeHardware;
        if (hardware == null) {
            source.sendSuccess(() -> Component.literal("UMCE hardware report is waiting for server startup"), false);
            return 0;
        }
        String memory = hardware.getPhysicalMemoryBytes().isPresent()
                ? String.format(java.util.Locale.ROOT, "%.1f GiB", hardware.getPhysicalMemoryBytes().getAsLong()
                        / (1024.0 * 1024.0 * 1024.0)) : "unknown";
        String message = String.format(java.util.Locale.ROOT,
                "UMCE hardware | %s %s | CPU %s | logical processors %d | physical memory %s | JVM %s %s | GPU %s",
                hardware.getOperatingSystem(), hardware.getArchitecture(),
                hardware.getCpuModelName().orElse("unknown"), hardware.getLogicalProcessors(), memory,
                hardware.getJvmName(), hardware.getJvmVersion(), hardware.getGpuComputeProbe());
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int sendProfile(CommandSourceStack source) {
        ProfileSnapshot profile = TICK_PROFILER.snapshot();
        String message = String.format(java.util.Locale.ROOT,
                "UMCE tick profile | samples %d | mean %.3f ms | p50 %.3f ms | p95 %.3f ms | p99 %.3f ms | max %.3f ms",
                profile.getSampleCount(), profile.getMeanMilliseconds(), profile.getP50Milliseconds(),
                profile.getP95Milliseconds(), profile.getP99Milliseconds(), profile.getMaxMilliseconds());
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int sendCompatibility(CommandSourceStack source) {
        PlatformAdapterReference current = adapterReference;
        if (current == null) {
            source.sendSuccess(() -> Component.literal("UMCE compatibility UNKNOWN; no server adapter is active"), false);
            return 0;
        }
        MinecraftRelease release = current.adapter.getMinecraftRelease();
        int loadedMods = FabricLoader.getInstance().getAllMods().size();
        String message = String.format(java.util.Locale.ROOT,
                "UMCE compatibility | Minecraft %s | %s %s | adapter %s | loaded mods %d | mod/patch matrix UNKNOWN | optimization patches remain disabled",
                release.getId(), current.adapter.getPlatformId(), current.adapter.getLoaderVersion(),
                release.getCompatibilityStatus(), loadedMods);
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int sendMods(CommandSourceStack source) {
        java.util.List<String> modules = new java.util.ArrayList<String>();
        for (net.fabricmc.loader.api.ModContainer container : FabricLoader.getInstance().getAllMods()) {
            modules.add(container.getMetadata().getId() + " "
                    + container.getMetadata().getVersion().getFriendlyString());
        }
        java.util.Collections.sort(modules);
        int displayed = Math.min(12, modules.size());
        StringBuilder summary = new StringBuilder("UMCE mods | loaded ").append(modules.size()).append(" | ");
        for (int index = 0; index < displayed; index++) {
            if (index > 0) summary.append(", ");
            summary.append(modules.get(index));
        }
        if (modules.size() > displayed) summary.append(" | ").append(modules.size() - displayed).append(" omitted");
        summary.append(" | compatibility entries are not available for these mods");
        source.sendSuccess(() -> Component.literal(summary.toString()), false);
        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        ConfigStore store = activeConfigStore;
        if (store == null) {
            source.sendFailure(Component.literal("UMCE configuration is not available yet"));
            return 0;
        }
        try {
            UmceConfig reloaded = store.reload();
            activeConfig = reloaded;
            source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                    "UMCE configuration reloaded | profile %s | CPU workers %d | GPU %s | dashboard %s",
                    reloaded.getProfile(), reloaded.getCpuWorkers(), reloaded.isGpuEnabled(),
                    reloaded.isDashboardEnabled())), false);
            LOGGER.info("UMCE configuration reloaded: profile={}, CPU workers={}, GPU={}, dashboard={}",
                    reloaded.getProfile(), reloaded.getCpuWorkers(), reloaded.isGpuEnabled(),
                    reloaded.isDashboardEnabled());
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal("UMCE configuration reload failed: " + exception.getMessage()));
            LOGGER.warn("UMCE configuration reload rejected; previous configuration remains active", exception);
            return 0;
        }
    }

    private static void logFinalProfile() {
        ProfileSnapshot profile = TICK_PROFILER.snapshot();
        LOGGER.info("UMCE tick profile: samples={}, mean={} ms, p50={} ms, p95={} ms, p99={} ms, max={} ms",
                profile.getSampleCount(), profile.getMeanMilliseconds(), profile.getP50Milliseconds(),
                profile.getP95Milliseconds(), profile.getP99Milliseconds(), profile.getMaxMilliseconds());
    }

    private static final class PlatformAdapterReference {
        private final FabricPlatformAdapter adapter;
        private PlatformAdapterReference(FabricPlatformAdapter adapter) { this.adapter = adapter; }
    }
}

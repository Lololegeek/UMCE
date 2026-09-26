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
                    .then(Commands.literal("status")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                            .executes(context -> sendStatus(context.getSource()))));
            LOGGER.info("UMCE admin command registered: /umce status");
        });
    }

    private static void onServerStarted(MinecraftServer server, String loaderVersion) {
        FabricPlatformAdapter adapter = new FabricPlatformAdapter(server, loaderVersion);
        adapterReference = new PlatformAdapterReference(adapter);
        HardwareProfile hardware = new HardwareDetector().detect();
        LOGGER.info("UMCE adapter active: Minecraft {}, Fabric Loader {}", adapter.getMinecraftRelease().getId(), loaderVersion);
        LOGGER.info("UMCE host: {} {}, {} logical processors, max heap {} bytes, GC {}",
                hardware.getOperatingSystem(), hardware.getArchitecture(), hardware.getLogicalProcessors(),
                hardware.getMaxHeapBytes(), hardware.getGarbageCollectors());
        LOGGER.info("UMCE GPU compute: {}; no GPU work is enabled", hardware.getGpuComputeProbe());

        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("umce.properties");
        try {
            UmceConfig config = new ConfigStore(configFile, hardware.getLogicalProcessors()).loadOrCreate();
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
        String message = String.format(java.util.Locale.ROOT,
                "UMCE | MC %s | %s %s | compatibility %s | tick samples %d | mean %.3f ms | p95 %.3f ms | p99 %.3f ms",
                release.getId(), current.adapter.getPlatformId(), current.adapter.getLoaderVersion(),
                release.getCompatibilityStatus(), profile.getSampleCount(), profile.getMeanMilliseconds(),
                profile.getP95Milliseconds(), profile.getP99Milliseconds());
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
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

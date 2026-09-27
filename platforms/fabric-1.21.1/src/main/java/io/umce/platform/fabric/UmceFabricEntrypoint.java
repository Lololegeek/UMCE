package io.umce.platform.fabric;

import io.umce.api.version.MinecraftRelease;
import io.umce.core.profiler.ProfileSnapshot;
import io.umce.core.profiler.TickProfiler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UmceFabricEntrypoint implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("UMCE");
    private static final boolean TICK_PROFILING_ENABLED = Boolean.getBoolean("umce.tickProfiler.enabled");
    private static final TickProfiler TICK_PROFILER = new TickProfiler(3_600);
    private static volatile long tickStartNanos;

    @Override
    public void onInitialize() {
        String loaderVersion = FabricLoader.getInstance().getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        LOGGER.info("UMCE Fabric adapter loaded for Minecraft 1.21.1 (Fabric Loader {})", loaderVersion);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> logServerStarted(server, loaderVersion));
        if (TICK_PROFILING_ENABLED) {
            LOGGER.info("UMCE tick profiler enabled by -Dumce.tickProfiler.enabled=true");
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> logFinalProfile());
            ServerTickEvents.START_SERVER_TICK.register(server -> tickStartNanos = System.nanoTime());
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                long started = tickStartNanos;
                tickStartNanos = 0L;
                if (started > 0L) TICK_PROFILER.recordTick(Math.max(0L, System.nanoTime() - started));
            });
        } else {
            LOGGER.info("UMCE tick profiler disabled; enable with -Dumce.tickProfiler.enabled=true");
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("umce")
                        .then(CommandManager.literal("status")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> sendStatus(context.getSource().getServer(),
                                        context.getSource().getPlayer())))));
    }

    private static void logServerStarted(MinecraftServer server, String loaderVersion) {
        MinecraftRelease release = MinecraftRelease.builder("1.21.1")
                .requiredJavaVersion(21)
                .adapterId("fabric-1.21.1")
                .build();
        LOGGER.info("UMCE adapter active: Minecraft {}, Fabric Loader {}, {} online player(s)",
                release.getId(), loaderVersion, server.getPlayerManager().getCurrentPlayerCount());
    }

    private static int sendStatus(MinecraftServer server, ServerPlayerEntity player) {
        if (!TICK_PROFILING_ENABLED) {
            String message = "UMCE | MC 1.21.1 | Fabric | online players "
                    + server.getPlayerManager().getCurrentPlayerCount()
                    + " | tick profiler disabled (set -Dumce.tickProfiler.enabled=true to enable)";
            if (player != null) player.sendMessage(Text.literal(message), false);
            else LOGGER.info(message);
            return 1;
        }
        ProfileSnapshot profile = TICK_PROFILER.snapshot();
        String message = String.format(java.util.Locale.ROOT,
                "UMCE | MC 1.21.1 | Fabric | online players %d | tick samples %d | mean %.3f ms | p95 %.3f ms | p99 %.3f ms",
                server.getPlayerManager().getCurrentPlayerCount(), profile.getSampleCount(), profile.getMeanMilliseconds(),
                profile.getP95Milliseconds(), profile.getP99Milliseconds());
        if (player != null) player.sendMessage(Text.literal(message), false);
        else LOGGER.info(message);
        return 1;
    }

    private static void logFinalProfile() {
        ProfileSnapshot profile = TICK_PROFILER.snapshot();
        LOGGER.info("UMCE tick profile: samples={}, mean={} ms, p50={} ms, p95={} ms, p99={} ms, max={} ms",
                profile.getSampleCount(), profile.getMeanMilliseconds(), profile.getP50Milliseconds(),
                profile.getP95Milliseconds(), profile.getP99Milliseconds(), profile.getMaxMilliseconds());
    }
}

package io.umce.platform.fabric;

import io.umce.api.version.MinecraftRelease;
import io.umce.core.patch.PatchOperationResult;
import io.umce.core.patch.PatchState;
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
import com.mojang.brigadier.arguments.StringArgumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UmceFabricEntrypoint implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("UMCE");
    private static final boolean TICK_PROFILING_ENABLED = Boolean.getBoolean("umce.tickProfiler.enabled");
    private static final TickProfiler TICK_PROFILER = new TickProfiler(3_600);
    private static final FabricPlatformAdapter PLATFORM_ADAPTER = new FabricPlatformAdapter();
    private static FabricPatchManager PATCH_MANAGER;
    private static volatile long tickStartNanos;

    @Override
    public void onInitialize() {
        String loaderVersion = FabricLoader.getInstance().getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        LOGGER.info("UMCE Fabric adapter loaded for Minecraft 1.21.1 (Fabric Loader {})", loaderVersion);

        PATCH_MANAGER = new FabricPatchManager(PLATFORM_ADAPTER);
        LOGGER.info("UMCE optimization mode: {}", PATCH_MANAGER.getMode());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> logServerStarted(server, loaderVersion));
        ServerLifecycleEvents.SERVER_STARTED.register(PLATFORM_ADAPTER::setServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (TICK_PROFILING_ENABLED) logFinalProfile();
            if (PATCH_MANAGER != null) PATCH_MANAGER.close();
            PLATFORM_ADAPTER.setServer(null);
        });
        if (TICK_PROFILING_ENABLED) {
            LOGGER.info("UMCE tick profiler enabled by -Dumce.tickProfiler.enabled=true");
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
                                        context.getSource().getPlayer())))
                        .then(CommandManager.literal("patch")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.literal("enable")
                                        .then(CommandManager.argument("id", StringArgumentType.word())
                                                .executes(context -> changePatch(
                                                        StringArgumentType.getString(context, "id"), true,
                                                        context.getSource().getPlayer()))))
                                .then(CommandManager.literal("disable")
                                        .then(CommandManager.argument("id", StringArgumentType.word())
                                                .executes(context -> changePatch(
                                                        StringArgumentType.getString(context, "id"), false,
                                                        context.getSource().getPlayer())))))));
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
        String patchState = PATCH_MANAGER != null && PATCH_MANAGER.isSmallBoxSectionProbeEnabled()
                ? "patch small-box-section-probe enabled" : "patches disabled";
        if (!TICK_PROFILING_ENABLED) {
            String message = "UMCE | MC 1.21.1 | Fabric | online players "
                    + server.getPlayerManager().getCurrentPlayerCount()
                    + " | mode " + (PATCH_MANAGER == null ? "safe" : PATCH_MANAGER.getMode())
                    + " | tick profiler disabled | " + patchState;
            if (player != null) player.sendMessage(Text.literal(message), false);
            else LOGGER.info(message);
            return 1;
        }
        ProfileSnapshot profile = TICK_PROFILER.snapshot();
        String message = String.format(java.util.Locale.ROOT,
                "UMCE | MC 1.21.1 | Fabric | online players %d | tick samples %d | mean %.3f ms | p95 %.3f ms | p99 %.3f ms | mode %s | %s",
                server.getPlayerManager().getCurrentPlayerCount(), profile.getSampleCount(), profile.getMeanMilliseconds(),
                profile.getP95Milliseconds(), profile.getP99Milliseconds(),
                PATCH_MANAGER == null ? "safe" : PATCH_MANAGER.getMode(), patchState);
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

    private static int changePatch(String patchId, boolean enable, ServerPlayerEntity player) {
        try {
            PatchOperationResult result = enable
                    ? PATCH_MANAGER.enable(patchId) : PATCH_MANAGER.disable(patchId);
            String message = "UMCE patch " + patchId + " " + result.getState().name().toLowerCase(java.util.Locale.ROOT)
                    + ": " + result.getEvaluation().getReason();
            if (player != null) player.sendMessage(Text.literal(message), false);
            else LOGGER.info(message);
            return result.getState() == PatchState.FAILED ? 0 : 1;
        } catch (IllegalArgumentException exception) {
            String message = "UMCE patch error: " + exception.getMessage();
            if (player != null) player.sendMessage(Text.literal(message), false);
            else LOGGER.warn(message);
            return 0;
        }
    }
}

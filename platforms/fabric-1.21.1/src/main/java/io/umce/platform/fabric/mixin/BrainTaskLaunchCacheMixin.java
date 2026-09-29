package io.umce.platform.fabric.mixin;

import io.umce.platform.fabric.optimization.BrainTaskLaunchCachePatchRuntime;
import io.umce.platform.fabric.optimization.BrainTaskListCache;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(Brain.class)
public abstract class BrainTaskLaunchCacheMixin<E extends LivingEntity> {
    @Shadow @Final private Map<Integer, Map<Activity, Set<Task<? super E>>>> tasks;
    @Shadow @Final private Set<Activity> possibleActivities;

    @Unique private BrainTaskListCache<Activity, Task<? super E>> umce$activeTaskCache;

    @Inject(method = "startTasks", at = @At("HEAD"), cancellable = true)
    private void umce$startCachedActiveTasks(ServerWorld world, E entity, CallbackInfo callback) {
        if (!BrainTaskLaunchCachePatchRuntime.isEnabled()) return;
        BrainTaskListCache<Activity, Task<? super E>> cache = umce$activeTaskCache;
        if (cache == null) {
            cache = new BrainTaskListCache<Activity, Task<? super E>>();
            umce$activeTaskCache = cache;
        }
        List<Task<? super E>> activeTasks = cache.get(tasks, possibleActivities);
        long time = world.getTime();
        for (int index = 0; index < activeTasks.size(); index++) {
            Task<? super E> task = activeTasks.get(index);
            if (task.getStatus() == MultiTickTask.Status.STOPPED) {
                task.tryStarting(world, entity, time);
            }
        }
        callback.cancel();
    }

    @Inject(method = {
            "setTaskList",
            "resetPossibleActivities",
            "refreshActivities",
            "setCoreActivities",
            "clear"
    }, at = @At("TAIL"))
    private void umce$invalidateActiveTaskCache(CallbackInfo callback) {
        if (umce$activeTaskCache != null) umce$activeTaskCache.invalidate();
    }
}

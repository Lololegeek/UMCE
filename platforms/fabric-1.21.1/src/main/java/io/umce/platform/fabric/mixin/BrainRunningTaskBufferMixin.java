package io.umce.platform.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.umce.platform.fabric.optimization.BrainRunningTaskBufferPatchRuntime;
import io.umce.runtime.collection.BoundedSnapshotBuffer;
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
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reuses only the non-escaping snapshot inside vanilla updateTasks, never public snapshots. */
@Mixin(Brain.class)
public abstract class BrainRunningTaskBufferMixin<E extends LivingEntity> {
    @Shadow @Final private Map<Integer, Map<Activity, Set<Task<? super E>>>> tasks;
    @Unique private BoundedSnapshotBuffer<Task<? super E>> umce$runningSnapshot;
    @Unique private int umce$updateDepth;

    @WrapMethod(method = "updateTasks")
    private void umce$scopeRunningSnapshot(ServerWorld world, E entity, Operation<Void> original) {
        if (!BrainRunningTaskBufferPatchRuntime.isEnabled()) {
            original.call(world, entity);
            return;
        }
        boolean ownsBuffer = false;
        umce$updateDepth++;
        try {
            if (umce$updateDepth == 1) {
                if (umce$runningSnapshot == null) umce$runningSnapshot = new BoundedSnapshotBuffer<>(128);
                ownsBuffer = umce$runningSnapshot.tryAcquire();
            }
            original.call(world, entity);
        } finally {
            if (ownsBuffer) umce$runningSnapshot.release();
            umce$updateDepth--;
        }
    }

    @Redirect(method = "updateTasks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/ai/brain/Brain;getRunningTasks()Ljava/util/List;"))
    private List<Task<? super E>> umce$captureRunningSnapshot(Brain<E> brain) {
        if (!BrainRunningTaskBufferPatchRuntime.isEnabled() || umce$updateDepth != 1
                || umce$runningSnapshot == null) {
            return brain.getRunningTasks();
        }
        // Preserve vanilla's snapshot-before-tick semantics, order, and duplicate occurrences.
        for (Map<Activity, Set<Task<? super E>>> byActivity : tasks.values()) {
            for (Set<Task<? super E>> activityTasks : byActivity.values()) {
                for (Task<? super E> task : activityTasks) {
                    if (task.getStatus() == MultiTickTask.Status.RUNNING) umce$runningSnapshot.add(task);
                }
            }
        }
        return umce$runningSnapshot;
    }
}

package io.umce.platform.fabric.mixin;

import io.umce.platform.fabric.optimization.InventoryScanPatchRuntime;
import io.umce.runtime.collection.IndexedListScan;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.Predicate;

@Mixin(LockableContainerBlockEntity.class)
public abstract class ContainerEmptyScanMixin {
    @Shadow protected abstract DefaultedList<ItemStack> getHeldStacks();
    @Unique private static final Predicate<ItemStack> UMCE_EMPTY_STACK = ItemStack::isEmpty;

    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private void umce$scanEmptySlots(CallbackInfoReturnable<Boolean> callback) {
        if (!InventoryScanPatchRuntime.isContainerEmptyEnabled()) return;
        DefaultedList<ItemStack> stacks = getHeldStacks();
        if (stacks.getClass() != DefaultedList.class) return;
        callback.setReturnValue(IndexedListScan.allMatch(stacks, UMCE_EMPTY_STACK));
    }
}

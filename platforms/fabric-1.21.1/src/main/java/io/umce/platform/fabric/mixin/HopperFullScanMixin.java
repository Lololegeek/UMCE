package io.umce.platform.fabric.mixin;

import io.umce.platform.fabric.optimization.InventoryScanPatchRuntime;
import io.umce.runtime.collection.IndexedListScan;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.Predicate;

@Mixin(HopperBlockEntity.class)
public abstract class HopperFullScanMixin {
    @Shadow private DefaultedList<ItemStack> inventory;
    // Vanilla's local isFull uses equality, unlike the separate destination-full check.
    @Unique private static final Predicate<ItemStack> UMCE_FULL_STACK =
            stack -> !stack.isEmpty() && stack.getCount() == stack.getMaxCount();

    @Inject(method = "isFull", at = @At("HEAD"), cancellable = true)
    private void umce$scanFullSlots(CallbackInfoReturnable<Boolean> callback) {
        if (!InventoryScanPatchRuntime.isHopperFullEnabled() || inventory.getClass() != DefaultedList.class) return;
        callback.setReturnValue(IndexedListScan.allMatch(inventory, UMCE_FULL_STACK));
    }
}

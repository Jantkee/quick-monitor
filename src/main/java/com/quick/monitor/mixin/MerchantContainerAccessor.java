package com.quick.monitor.mixin;

import net.minecraft.world.inventory.MerchantContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取目标当前选中的交易索引。 */
@Mixin(MerchantContainer.class)
public interface MerchantContainerAccessor {
    @Accessor("selectionHint")
    int monitor$selectionHint();
}

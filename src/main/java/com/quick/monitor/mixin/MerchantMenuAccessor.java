package com.quick.monitor.mixin;

import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取目标村民菜单的交易容器。 */
@Mixin(MerchantMenu.class)
public interface MerchantMenuAccessor {
    @Accessor("tradeContainer")
    MerchantContainer monitor$tradeContainer();
}

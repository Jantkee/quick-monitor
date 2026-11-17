package com.quick.monitor.mixin;

import com.quick.monitor.MonitorService;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 监管界面只读：所有点击取消并回发快照。 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void monitor$readOnly(int slotId, int button, net.minecraft.world.inventory.ContainerInput input,
                                  Player player, CallbackInfo ci) {
        if (MonitorService.isObserver((AbstractContainerMenu) (Object) this)) {
            ci.cancel();
            ((AbstractContainerMenu) (Object) this).sendAllDataToRemote();
        }
    }
}

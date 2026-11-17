package com.quick.monitor.mixin;

import com.quick.monitor.MonitorService;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 监管菜单不按距离关闭：观察者身体可能离目标很远。 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerStillValidMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean monitor$observerStillValid(AbstractContainerMenu menu, Player player) {
        return MonitorService.isObserver(menu) || menu.stillValid(player);
    }
}

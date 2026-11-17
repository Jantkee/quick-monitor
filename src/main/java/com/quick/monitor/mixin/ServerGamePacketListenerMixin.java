package com.quick.monitor.mixin;

import com.quick.monitor.MonitorExitPrompt;
import com.quick.monitor.MonitorService;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 旁观者点击监管界面时原版会直接丢弃，这里改为取消并回发；Shift 弹出退出选择框。 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void monitor$spectatorClicks(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (!player.isSpectator()) {
            return;
        }
        var menu = player.containerMenu;
        if (menu != null && menu.containerId == packet.containerId()
                && MonitorService.isObserver(menu)) {
            ci.cancel();
            menu.sendAllDataToRemote();
        }
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"))
    private void monitor$inventoryClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (packet.containerId() == 0) {
            MonitorService.onInventoryClick(player);
        }
    }

    @Inject(method = "handlePlayerInput", at = @At("HEAD"))
    private void monitor$shiftExit(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        if (packet.input().shift() && MonitorService.isWatching(player)) {
            MonitorExitPrompt.prompt(player);
        }
    }
}

package com.quick.monitor.mixin;

import java.util.OptionalInt;

import com.quick.monitor.MonitorService;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 记录玩家打开界面时用的 MenuProvider，供监管重放。 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMenuMixin {
    @Inject(method = "openMenu", at = @At("HEAD"))
    private void monitor$captureProvider(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        MonitorService.capture((ServerPlayer) (Object) this, provider);
    }
}

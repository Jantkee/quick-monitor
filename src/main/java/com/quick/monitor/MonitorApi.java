package com.quick.monitor;

import net.minecraft.server.level.ServerPlayer;

/**
 * 给主 mod（soudache）反射调用的稳定入口。
 *
 * <p>只暴露这三个方法；签名变更会破坏主面板按钮，改之前先同步主 mod。
 */
public final class MonitorApi {
    private MonitorApi() {
    }

    public static boolean start(ServerPlayer admin, ServerPlayer target) {
        return MonitorService.start(admin, target);
    }

    public static boolean stop(ServerPlayer admin) {
        return MonitorService.stop(admin, "api", true);
    }

    public static boolean isWatching(ServerPlayer admin) {
        return MonitorService.isWatching(admin);
    }
}

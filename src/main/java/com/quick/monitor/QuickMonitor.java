package com.quick.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * quick-monitor：管理员旁观监管。
 *
 * <p>独立 mod：只装它时用 {@code /monitor <玩家>} / {@code /monitor stop}；
 * 装了 soudache 时，主面板通过反射调用 {@link MonitorApi} 显示“监管该玩家”按钮。
 */
public final class QuickMonitor implements ModInitializer {
    public static final String MOD_ID = "quick-monitor";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        MonitorService.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                dispatcher.register(Commands.literal("monitor")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("stop")
                                .executes(context -> promptExit(context.getSource()))
                                .then(Commands.literal("restore")
                                        .executes(context -> exit(context.getSource(),
                                                MonitorService.ExitMode.RESTORE)))
                                .then(Commands.literal("spectate")
                                        .executes(context -> exit(context.getSource(),
                                                MonitorService.ExitMode.SPECTATE))))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) {
                                        context.getSource().sendFailure(Component.literal("只能由玩家使用"));
                                        return 0;
                                    }
                                    ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                    if (target == player) {
                                        player.sendSystemMessage(Component.literal("不能监管自己"));
                                        return 0;
                                    }
                                    return MonitorService.start(player, target) ? 1 : 0;
                                }))));
        LOG.info("quick-monitor ready");
    }

    /** 弹退出方式选择框；不再直接退出。 */
    private static int promptExit(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只能由玩家使用"));
            return 0;
        }
        if (!MonitorService.isWatching(player)) {
            player.sendSystemMessage(Component.literal("当前没有在监管"));
            return 0;
        }
        MonitorExitPrompt.prompt(player);
        return 1;
    }

    /** 按指定方式退出监管。 */
    private static int exit(CommandSourceStack source, MonitorService.ExitMode mode) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只能由玩家使用"));
            return 0;
        }
        if (!MonitorService.stop(player, "command", true, mode)) {
            player.sendSystemMessage(Component.literal("当前没有在监管"));
            return 0;
        }
        return 1;
    }
}

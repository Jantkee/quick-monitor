package com.quick.monitor;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

/**
 * 退出监管时的二选一对话框。
 *
 * <p>两个按钮分别执行 {@code monitor stop restore} 与 {@code monitor stop spectate}，
 * 由 {@link QuickMonitor} 注册的指令完成真正的收尾，这样对话本身不持有服务端状态。
 */
public final class MonitorExitPrompt {
    private MonitorExitPrompt() {
    }

    public static void prompt(ServerPlayer admin) {
        admin.openDialog(Holder.direct(build()));
    }

    static MultiActionDialog build() {
        List<DialogBody> body = List.of(new PlainMessage(Component.literal(
                "退出监管：可以还原开始前的游戏模式和位置，也可以留在旁观继续观察目标。"), 220));
        CommonDialogData common = new CommonDialogData(
                Component.literal("退出监管"),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                body,
                List.of());
        ActionButton restore = new ActionButton(
                new CommonButtonData(Component.literal("还原模式与位置"),
                        Optional.of(Component.literal("回到开始监管前的位置和游戏模式")), 120),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("monitor stop restore"))));
        ActionButton spectate = new ActionButton(
                new CommonButtonData(Component.literal("留在旁观"),
                        Optional.of(Component.literal("保持旁观，留在目标附近继续看")), 120),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("monitor stop spectate"))));
        return new MultiActionDialog(common, List.of(restore, spectate), Optional.empty(), 2);
    }
}

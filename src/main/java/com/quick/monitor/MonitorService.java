package com.quick.monitor;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * 监管核心：旁观镜头 + 管理员物品栏替换为目标副本 + 目标容器界面的只读镜像。
 *
 * <p>管理员全程旁观，原版会拦掉世界/容器交互；这里额外提供按 E 查看替换后的
 * 物品栏、目标容器的同类型只读镜像、actionbar 手持物品名。
 */
public final class MonitorService {
    /** 退出监管时的收尾方式。 */
    public enum ExitMode {
        /** 还原管理员原来的游戏模式与位置。 */
        RESTORE,
        /** 保持旁观，留在当前所在的位置继续观察。 */
        SPECTATE
    }

    private static final Map<UUID, MenuProvider> PROVIDERS = new HashMap<>();
    private static final Map<UUID, Watch> WATCHES = new HashMap<>();
    private static final Map<UUID, ItemStack[]> SAVED = new HashMap<>();
    private static final Set<AbstractContainerMenu> OBSERVERS =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private static final int[] CAMERA_RESEND = {0, 5, 10, 20, 40, 80, 160};

    private static final class Watch {
        ServerPlayer admin;
        ServerPlayer target;
        MenuProvider provider;
        AbstractContainerMenu targetMenu;
        AbstractContainerMenu observer;
        SimpleContainer snapshot;
        ItemStack lastCarried = ItemStack.EMPTY;
        ItemStack lastHeld = ItemStack.EMPTY;
        int lastHealth;
        int lastArmor;
        long startTick;
        int cameraIndex;
        long nextCameraTick;
        boolean inventoryView;
        SimpleContainer inventorySnapshot;
        double inventoryOpenX;
        double inventoryOpenY;
        double inventoryOpenZ;
        long lastInventoryClick;
        long inventoryCooldownUntil;

        // 开始监管前的状态，退出时按 ExitMode 决定是否还原
        GameType savedGameType;
        ServerLevel savedLevel;
        double savedX;
        double savedY;
        double savedZ;
        float savedYaw;
        float savedPitch;
    }

    private MonitorService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(MonitorService::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                stop(handler.getPlayer(), "disconnect", false));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                restoreLeftover(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPING.register(MonitorService::restoreAll);
    }

    public static void capture(ServerPlayer player, MenuProvider provider) {
        if (provider != null) {
            PROVIDERS.put(player.getUUID(), provider);
        }
    }

    public static boolean isObserver(AbstractContainerMenu menu) {
        return OBSERVERS.contains(menu);
    }

    public static boolean isWatching(ServerPlayer admin) {
        return WATCHES.containsKey(admin.getUUID());
    }

    /** 开始监管；失败时返回 false 并给管理员发原因。 */
    public static boolean start(ServerPlayer admin, ServerPlayer target) {
        if (admin == target) {
            admin.sendSystemMessage(Component.literal("不能监管自己"));
            return false;
        }
        if (target.hasDisconnected()) {
            admin.sendSystemMessage(Component.literal("目标不在线"));
            return false;
        }
        stop(admin, "restart", false);
        admin.closeContainer();

        // 先记下管理员原来的状态，退出时可能要用它还原
        GameType savedGameType = admin.gameMode();
        ServerLevel savedLevel = (ServerLevel) admin.level();
        double savedX = admin.getX();
        double savedY = admin.getY();
        double savedZ = admin.getZ();
        float savedYaw = admin.getYRot();
        float savedPitch = admin.getXRot();

        admin.setGameMode(GameType.SPECTATOR);
        // 先贴近目标，保证客户端已跟踪该实体，再发镜头包；否则客户端会静默忽略
        admin.teleportTo((net.minecraft.server.level.ServerLevel) target.level(),
                target.getX(), target.getY(), target.getZ(),
                java.util.Set.of(), target.getYRot(), target.getXRot(), false);
        admin.setCamera(target);
        admin.connection.send(new ClientboundSetCameraPacket(target));

        Watch watch = new Watch();
        watch.savedGameType = savedGameType;
        watch.savedLevel = savedLevel;
        watch.savedX = savedX;
        watch.savedY = savedY;
        watch.savedZ = savedZ;
        watch.savedYaw = savedYaw;
        watch.savedPitch = savedPitch;
        watch.admin = admin;
        watch.target = target;
        watch.startTick = admin.level().getServer().getTickCount();
        watch.cameraIndex = 1;
        watch.nextCameraTick = watch.startTick + CAMERA_RESEND[1];
        watch.admin = admin;
        watch.target = target;
        watch.provider = PROVIDERS.get(target.getUUID());
        watch.targetMenu = target.containerMenu;
        WATCHES.put(admin.getUUID(), watch);

        ItemStack[] saved = snapshotAdmin(admin);
        SAVED.put(admin.getUUID(), saved);
        MonitorInventoryStore.save(admin.level().getServer(), admin, saved);
        applyTarget(admin, target);

        if (target.containerMenu != target.inventoryMenu) {
            open(admin, watch);
        }
        admin.sendSystemMessage(Component.literal("正在监管 " + target.getGameProfile().name()
                + "；按 E 查看其物品栏，按 Shift 或执行 /monitor stop 退出"));
        MonitorAudit.record("monitor_start", admin, target.getGameProfile().name(), "command");
        return true;
    }

    /** 退出监管，默认还原管理员原来的模式与位置；返回是否真的在监管。 */
    public static boolean stop(ServerPlayer admin, String reason, boolean notify) {
        return stop(admin, reason, notify, ExitMode.RESTORE);
    }

    /** 退出监管并按 mode 收尾；返回是否真的在监管。 */
    public static boolean stop(ServerPlayer admin, String reason, boolean notify, ExitMode mode) {
        Watch watch = WATCHES.remove(admin.getUUID());
        if (watch == null) {
            restoreInventory(admin);
            return false;
        }
        closeObserver(watch);
        restoreInventory(admin);
        if (admin.getCamera() != admin) {
            admin.setCamera(admin);
        }
        applyExit(admin, watch, mode);
        if (notify) {
            admin.sendSystemMessage(Component.literal(mode == ExitMode.RESTORE
                    ? "已退出监管，已还原模式与位置"
                    : "已退出监管，留在旁观继续观察"));
        }
        MonitorAudit.record("monitor_stop", admin, watch.target.getGameProfile().name(),
                reason + ":" + mode.name().toLowerCase(Locale.ROOT));
        return true;
    }

    /** 按退出方式收尾：还原模式与位置，或保持旁观留在原地。 */
    private static void applyExit(ServerPlayer admin, Watch watch, ExitMode mode) {
        if (mode == ExitMode.SPECTATE) {
            admin.setGameMode(GameType.SPECTATOR);
            return;
        }
        admin.setGameMode(watch.savedGameType == null ? GameType.SPECTATOR : watch.savedGameType);
        // 断线的玩家已经不在世界里，传送没有意义；模式仍要还原，否则会写进玩家数据
        if (watch.savedLevel != null && !admin.hasDisconnected()) {
            admin.teleportTo(watch.savedLevel, watch.savedX, watch.savedY, watch.savedZ,
                    java.util.Set.of(), watch.savedYaw, watch.savedPitch, false);
            admin.resetFallDistance();
        }
    }

    // ---------- 物品栏替换 ----------

    private static ItemStack[] snapshotAdmin(ServerPlayer admin) {
        int size = admin.getInventory().getContainerSize();
        ItemStack[] saved = new ItemStack[size + 4];
        for (int i = 0; i < size; i++) {
            saved[i] = admin.getInventory().getItem(i).copy();
        }
        for (int i = 0; i < 4; i++) {
            saved[size + i] = admin.inventoryMenu.getSlot(1 + i).getItem().copy();
        }
        return saved;
    }

    private static void applyTarget(ServerPlayer admin, ServerPlayer target) {
        int n = Math.min(admin.getInventory().getContainerSize(),
                target.getInventory().getContainerSize());
        for (int i = 0; i < n; i++) {
            admin.getInventory().setItem(i, target.getInventory().getItem(i).copy());
        }
        for (int i = 0; i < 4; i++) {
            admin.inventoryMenu.getSlot(1 + i).set(
                    target.inventoryMenu.getSlot(1 + i).getItem().copy());
        }
        admin.getInventory().setChanged();
        admin.inventoryMenu.sendAllDataToRemote();
    }

    private static void applySaved(ServerPlayer admin, ItemStack[] saved) {
        int size = admin.getInventory().getContainerSize();
        for (int i = 0; i < size && i < saved.length; i++) {
            admin.getInventory().setItem(i, saved[i] == null ? ItemStack.EMPTY : saved[i]);
        }
        for (int i = 0; i < 4 && size + i < saved.length; i++) {
            admin.inventoryMenu.getSlot(1 + i).set(
                    saved[size + i] == null ? ItemStack.EMPTY : saved[size + i]);
        }
        admin.getInventory().setChanged();
        admin.inventoryMenu.sendAllDataToRemote();
    }

    private static void restoreInventory(ServerPlayer admin) {
        ItemStack[] saved = SAVED.remove(admin.getUUID());
        if (saved == null) {
            saved = MonitorInventoryStore.load(admin.level().getServer(), admin.getUUID());
        }
        if (saved == null) {
            return;
        }
        applySaved(admin, saved);
        MonitorInventoryStore.remove(admin.getUUID());
    }

    private static void restoreLeftover(ServerPlayer player) {
        if (!MonitorInventoryStore.has(player.getUUID())) {
            return;
        }
        ItemStack[] saved = MonitorInventoryStore.load(player.level().getServer(), player.getUUID());
        if (saved != null) {
            applySaved(player, saved);
            player.sendSystemMessage(Component.literal("检测到上次监管未正常退出，已恢复你的物品栏"));
        }
        MonitorInventoryStore.remove(player.getUUID());
    }

    private static void restoreAll(MinecraftServer server) {
        for (UUID id : List.copyOf(WATCHES.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                stop(player, "server_stop", false, ExitMode.RESTORE);
            } else {
                WATCHES.remove(id);
                SAVED.remove(id);
            }
        }
    }

    // ---------- 容器镜像 ----------

    /** 镜像窗口标题：统一成「类别｜玩家名（只读）」并标金色。 */
    private static Component viewTitle(String kind, ServerPlayer target) {
        return Component.literal(kind + "｜" + target.getGameProfile().name() + "（只读）")
                .withStyle(ChatFormatting.GOLD);
    }

    private static MenuType<ChestMenu> menuTypeFor(int rows) {
        return switch (rows) {
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    private static void open(ServerPlayer admin, Watch watch) {
        watch.provider = PROVIDERS.get(watch.target.getUUID());
        admin.openMenu(new SimpleMenuProvider((id, inventory, player) -> {
            AbstractContainerMenu menu = create(watch, id);
            watch.observer = menu;
            OBSERVERS.add(menu);
            return menu;
        }, viewTitle("目标容器", watch.target)));
        if (watch.observer == null) {
            admin.sendSystemMessage(Component.literal("目标界面无法复制，请按 E 查看其物品栏"));
            return;
        }
        refresh(watch);
        syncMerchant(watch);
    }

    private static AbstractContainerMenu create(Watch watch, int id) {
        AbstractContainerMenu menu = null;
        if (watch.target.containerMenu instanceof MerchantMenu targetMerchant) {
            MirrorMerchant merchant = new MirrorMerchant();
            merchant.offers = targetMerchant.getOffers().copy();
            merchant.xp = targetMerchant.getTraderXp();
            MerchantMenu mirror = new MerchantMenu(id, watch.admin.getInventory(), merchant);
            mirror.setMerchantLevel(targetMerchant.getTraderLevel());
            mirror.setCanRestock(targetMerchant.canRestock());
            mirror.setShowProgressBar(targetMerchant.showProgressBar());
            menu = mirror;
        }
        if (menu == null && watch.provider != null) {
            try {
                menu = watch.provider.createMenu(id, watch.admin.getInventory(), watch.admin);
            } catch (Throwable ignored) {
                menu = null;
            }
        }
        if (menu == null) {
            int sourceSize = watch.target.containerMenu == null ? 0 : watch.target.containerMenu.slots.size();
            int rows = Math.min(6, Math.max(3, (sourceSize + 8) / 9));
            SimpleContainer container = new SimpleContainer(rows * 9);
            menu = new ChestMenu(menuTypeFor(rows), id, watch.admin.getInventory(), container, rows);
        }
        watch.snapshot = new SimpleContainer(menu.slots.size());
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot old = menu.getSlot(i);
            menu.slots.set(i, new Slot(watch.snapshot, i, old.x, old.y));
        }
        watch.targetMenu = watch.target.containerMenu;
        return menu;
    }

    private static void closeObserver(Watch watch) {
        AbstractContainerMenu menu = watch.observer;
        watch.observer = null;
        if (menu != null) {
            OBSERVERS.remove(menu);
            menu.setCarried(ItemStack.EMPTY);
            if (watch.admin.containerMenu == menu) {
                watch.admin.closeContainer();
            }
        }
    }

    private static void refresh(Watch watch) {
        if (watch.observer == null || watch.snapshot == null) {
            return;
        }
        AbstractContainerMenu source = watch.target.containerMenu;
        int sourceSize = source == null ? 0 : source.slots.size();
        int size = watch.observer.slots.size();
        for (int i = 0; i < size; i++) {
            ItemStack stack = i < sourceSize ? source.getSlot(i).getItem().copy() : ItemStack.EMPTY;
            watch.snapshot.setItem(i, stack);
        }
        ItemStack carried = source == null ? ItemStack.EMPTY : source.getCarried().copy();
        if (!ItemStack.matches(carried, watch.lastCarried)) {
            watch.observer.setCarried(carried);
            watch.lastCarried = carried;
        }
        watch.observer.sendAllDataToRemote();
    }

    /** 村民交易列表由单独的数据包发送，观察者菜单需要补发。 */
    private static void syncMerchant(Watch watch) {
        if (!(watch.target.containerMenu instanceof MerchantMenu target)
                || !(watch.observer instanceof MerchantMenu observer)) {
            return;
        }
        MerchantOffers base = target.getOffers();
        MerchantOffers display = new MerchantOffers();
        try {
            MerchantContainer container = ((com.quick.monitor.mixin.MerchantMenuAccessor) target)
                    .monitor$tradeContainer();
            int hint = ((com.quick.monitor.mixin.MerchantContainerAccessor) container)
                    .monitor$selectionHint();
            if (hint >= 0 && hint < base.size()) {
                display.add(base.get(hint));
            }
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < base.size(); i++) {
            if (display.isEmpty() || display.get(0) != base.get(i)) {
                display.add(base.get(i));
            }
        }
        if (display.isEmpty()) {
            display = base.copy();
        }
        observer.setOffers(display);
        observer.setMerchantLevel(target.getTraderLevel());
        observer.setCanRestock(target.canRestock());
        observer.setShowProgressBar(target.showProgressBar());
        watch.admin.connection.send(new ClientboundMerchantOffersPacket(
                observer.containerId, observer.getOffers(),
                observer.getTraderLevel(), target.getTraderXp(),
                observer.showProgressBar(), observer.canRestock()));
    }

    /** 不共享目标的村民，避免 observer 菜单抢走 trading player。 */
    private static final class MirrorMerchant implements Merchant {
        MerchantOffers offers = new MerchantOffers();
        int xp;

        @Override
        public void setTradingPlayer(Player player) {
        }

        @Override
        public Player getTradingPlayer() {
            return null;
        }

        @Override
        public MerchantOffers getOffers() {
            return offers;
        }

        @Override
        public void overrideOffers(MerchantOffers offers) {
            this.offers = offers;
        }

        @Override
        public void notifyTrade(MerchantOffer offer) {
        }

        @Override
        public void notifyTradeUpdated(ItemStack stack) {
        }

        @Override
        public int getVillagerXp() {
            return xp;
        }

        @Override
        public void overrideXp(int xp) {
            this.xp = xp;
        }

        @Override
        public boolean showProgressBar() {
            return true;
        }

        @Override
        public SoundEvent getNotifyTradeSound() {
            return SoundEvents.VILLAGER_YES;
        }

        @Override
        public boolean isClientSide() {
            return false;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    private static void sendActionBar(Watch watch, boolean force) {
        ServerPlayer target = watch.target;
        ItemStack held = target.getMainHandItem();
        int health = (int) Math.ceil(target.getHealth());
        int armor = target.getArmorValue();
        boolean changed = health != watch.lastHealth || armor != watch.lastArmor
                || !ItemStack.matches(held, watch.lastHeld);
        if (!force && !changed) {
            return;
        }
        Component name = held.isEmpty() ? Component.literal("空手") : held.getHoverName();
        watch.admin.connection.send(new ClientboundSetActionBarTextPacket(
                Component.literal("血量 " + health + "/" + (int) target.getMaxHealth()
                        + " · 护甲 " + armor + " · 手持 ").append(name)));
        watch.lastHeld = held.copy();
        watch.lastHealth = health;
        watch.lastArmor = armor;
    }

    /** 目标在物品栏界面里点击（containerId=0）时触发自动背包快照。 */
    public static void onInventoryClick(ServerPlayer target) {
        if (WATCHES.isEmpty()) {
            return;
        }
        long now = target.level().getServer().getTickCount();
        for (Watch watch : WATCHES.values()) {
            if (watch.target != target) {
                continue;
            }
            watch.lastInventoryClick = now;
            if (watch.inventoryView || watch.observer != null) {
                return;
            }
            if (now < watch.inventoryCooldownUntil) {
                return;
            }
            if (watch.admin.containerMenu != watch.admin.inventoryMenu) {
                return;
            }
            openInventoryView(watch);
            return;
        }
    }

    private static void openInventoryView(Watch watch) {
        // 45 个容器格 + 36 个玩家格；布局照原版背包：主背包 3 行、护甲/副手 1 行、快捷栏 1 行
        SimpleContainer snapshot = new SimpleContainer(45 + 36);
        watch.inventorySnapshot = snapshot;
        watch.inventoryView = true;
        watch.inventoryOpenX = watch.target.getX();
        watch.inventoryOpenY = watch.target.getY();
        watch.inventoryOpenZ = watch.target.getZ();
        watch.admin.openMenu(new SimpleMenuProvider((id, inventory, player) -> {
            ChestMenu menu = new ChestMenu(MenuType.GENERIC_9x5, id, watch.admin.getInventory(), snapshot, 5);
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot old = menu.getSlot(i);
                menu.slots.set(i, new Slot(snapshot, i, old.x, old.y));
            }
            watch.observer = menu;
            OBSERVERS.add(menu);
            return menu;
        }, viewTitle("目标背包", watch.target)));
        refreshInventoryView(watch);
    }

    private static void refreshInventoryView(Watch watch) {
        if (!watch.inventoryView || watch.inventorySnapshot == null) {
            return;
        }
        net.minecraft.world.entity.player.Inventory inv = watch.target.getInventory();
        for (int i = 0; i < 45; i++) {
            watch.inventorySnapshot.setItem(i, ItemStack.EMPTY);
        }
        // 主背包 3 行（0-26）
        for (int i = 0; i < 27; i++) {
            watch.inventorySnapshot.setItem(i, inv.getItem(9 + i).copy());
        }
        // 倒数第二行（27-35）：护甲 4 格 + 副手 1 格，其余留空
        for (int i = 0; i < 4; i++) {
            watch.inventorySnapshot.setItem(27 + i, inv.getItem(36 + i).copy());
        }
        watch.inventorySnapshot.setItem(31, inv.getItem(40).copy());
        // 最后一行（36-44）：快捷栏独占
        for (int i = 0; i < 9; i++) {
            watch.inventorySnapshot.setItem(36 + i, inv.getItem(i).copy());
        }
        if (watch.observer != null) {
            watch.observer.sendAllDataToRemote();
        }
    }

    private static void closeInventoryView(Watch watch) {
        AbstractContainerMenu menu = watch.observer;
        watch.observer = null;
        watch.inventoryView = false;
        watch.inventorySnapshot = null;
        watch.inventoryCooldownUntil = watch.admin.level().getServer().getTickCount() + 40;
        if (menu != null) {
            OBSERVERS.remove(menu);
            menu.setCarried(ItemStack.EMPTY);
            if (watch.admin.containerMenu == menu) {
                watch.admin.closeContainer();
            }
        }
    }

    /** 客户端能接受镜头包的大致条件：同维度、附近、目标区块已加载。 */
    private static boolean canSendCamera(ServerPlayer admin, ServerPlayer target) {
        if (admin.level() != target.level()) {
            return false;
        }
        if (admin.distanceToSqr(target) > 64 * 64) {
            return false;
        }
        return ((net.minecraft.server.level.ServerLevel) target.level())
                .isLoaded(target.blockPosition());
    }

    private static void tick(MinecraftServer server) {
        if (WATCHES.isEmpty() || server.getTickCount() % 5 != 0) {
            return;
        }
        boolean barTick = server.getTickCount() % 20 == 0;
        for (Map.Entry<UUID, Watch> entry : List.copyOf(WATCHES.entrySet())) {
            Watch watch = entry.getValue();
            if (watch.admin.hasDisconnected()) {
                WATCHES.remove(entry.getKey());
                continue;
            }
            if (watch.target.hasDisconnected()) {
                stop(watch.admin, "target_offline", true);
                continue;
            }
            if (watch.target.isDeadOrDying()) {
                stop(watch.admin, "death", true);
                continue;
            }
            // 镜头：先满足可发送条件，再按退避时间点重发；之后每 5 秒保底一次
            if (server.getTickCount() >= watch.nextCameraTick && canSendCamera(watch.admin, watch.target)) {
                watch.admin.connection.send(new ClientboundSetCameraPacket(watch.target));
                if (watch.cameraIndex < CAMERA_RESEND.length) {
                    watch.nextCameraTick = watch.startTick + CAMERA_RESEND[watch.cameraIndex];
                    watch.cameraIndex++;
                } else {
                    watch.nextCameraTick = server.getTickCount() + 100;
                }
            }
            if (watch.admin.getCamera() != watch.target) {
                watch.admin.setCamera(watch.target);
            }
            double distSq = watch.admin.distanceToSqr(watch.target);
            if (distSq > 32 * 32 || (server.getTickCount() % 100 == 0 && distSq > 16)) {
                watch.admin.teleportTo((net.minecraft.server.level.ServerLevel) watch.target.level(),
                        watch.target.getX(), watch.target.getY(), watch.target.getZ(),
                        java.util.Set.of(), watch.target.getYRot(), watch.target.getXRot(), false);
                watch.admin.connection.send(new ClientboundSetCameraPacket(watch.target));
            }
            AbstractContainerMenu current = watch.target.containerMenu;
            if (watch.inventoryView) {
                if (current != watch.target.inventoryMenu) {
                    closeInventoryView(watch);
                } else {
                    boolean idle = server.getTickCount() - watch.lastInventoryClick > 100;
                    boolean moved = watch.target.distanceToSqr(watch.inventoryOpenX,
                            watch.inventoryOpenY, watch.inventoryOpenZ) > 4;
                    if (watch.admin.containerMenu != watch.observer || idle || moved) {
                        closeInventoryView(watch);
                    } else {
                        refreshInventoryView(watch);
                    }
                    sendActionBar(watch, barTick);
                    continue;
                }
            }
            if (current == watch.target.inventoryMenu) {
                if (watch.observer != null) {
                    closeObserver(watch);
                    watch.admin.sendSystemMessage(Component.literal("目标已关闭界面，按 E 查看其物品栏"));
                }
                watch.targetMenu = current;
                applyTarget(watch.admin, watch.target);
                sendActionBar(watch, barTick);
                continue;
            }
            if (watch.observer == null || current != watch.targetMenu) {
                closeObserver(watch);
                open(watch.admin, watch);
            }
            refresh(watch);
            applyTarget(watch.admin, watch.target);
            if (server.getTickCount() % 20 == 0) {
                syncMerchant(watch);
            }
            sendActionBar(watch, barTick);
        }
    }
}

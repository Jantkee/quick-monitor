package com.quick.monitor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 监管期间管理员物品栏的磁盘快照。
 *
 * <p>路径 config/quick-monitor/watch-inventory.json。
 */
public final class MonitorInventoryStore {
    private static final Path DIR = Path.of("config", "quick-monitor");
    private static final Path FILE = DIR.resolve("watch-inventory.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private MonitorInventoryStore() {
    }

    public static void save(MinecraftServer server, ServerPlayer player, ItemStack[] items) {
        JsonObject root = read(FILE);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess(server));
        JsonArray array = new JsonArray();
        for (ItemStack stack : items) {
            array.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).getOrThrow());
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("name", player.getGameProfile().name());
        entry.add("items", array);
        root.add(player.getUUID().toString(), entry);
        write(FILE, root);
    }

    public static ItemStack[] load(MinecraftServer server, UUID id) {
        JsonObject root = read(FILE);
        JsonObject entry = root.has(id.toString()) ? root.getAsJsonObject(id.toString()) : null;
        if (entry == null || !entry.has("items")) {
            return null;
        }
        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess(server));
            JsonArray array = entry.getAsJsonArray("items");
            ItemStack[] items = new ItemStack[array.size()];
            for (int i = 0; i < array.size(); i++) {
                items[i] = ItemStack.OPTIONAL_CODEC.parse(ops, array.get(i)).getOrThrow();
            }
            return items;
        } catch (Exception e) {
            QuickMonitor.LOG.warn("could not load watch inventory for {}", id, e);
            return null;
        }
    }

    public static boolean has(UUID id) {
        return read(FILE).has(id.toString());
    }

    public static void remove(UUID id) {
        JsonObject root = read(FILE);
        if (root.remove(id.toString()) != null) {
            write(FILE, root);
        }
    }

    private static RegistryAccess registryAccess(MinecraftServer server) {
        return server.registryAccess();
    }

    private static JsonObject read(Path path) {
        try {
            if (!Files.exists(path)) {
                return new JsonObject();
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
            }
        } catch (Exception e) {
            QuickMonitor.LOG.warn("could not read {}", path, e);
            return new JsonObject();
        }
    }

    private static void write(Path path, JsonObject root) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            QuickMonitor.LOG.warn("could not write {}", path, e);
        }
    }
}

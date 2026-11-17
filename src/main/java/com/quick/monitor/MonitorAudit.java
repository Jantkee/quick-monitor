package com.quick.monitor;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.server.level.ServerPlayer;

/** 监管审计：config/quick-monitor/audit-YYYY-MM-DD.jsonl。 */
public final class MonitorAudit {
    private static final Path DIR = Path.of("config", "quick-monitor");
    private static final Gson GSON = new GsonBuilder().create();

    private MonitorAudit() {
    }

    public static void record(String event, ServerPlayer admin, String target, String reason) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        entry.put("event", event);
        entry.put("admin", admin.getGameProfile().name());
        entry.put("admin_uuid", admin.getUUID().toString());
        entry.put("target", target);
        if (reason != null) {
            entry.put("reason", reason);
        }
        try {
            Files.createDirectories(DIR);
            Path file = DIR.resolve("audit-" + LocalDate.now() + ".jsonl");
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
                writer.write(GSON.toJson(entry));
                writer.write("\n");
            }
        } catch (IOException e) {
            QuickMonitor.LOG.warn("could not write audit", e);
        }
    }
}

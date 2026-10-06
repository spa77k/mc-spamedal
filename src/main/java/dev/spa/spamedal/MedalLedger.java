package dev.spa.spamedal;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 発行と回収の記録。
 *
 * 1件ずつの取引は medals.log に1行1件のJSON（JSON Lines）で追記する。
 * 累計は stats.yml に1spaメダル換算の枚数で持ち、「発行 − 回収 = 出回っている枚数」を出す。
 * 出回っている枚数より多くのメダルが戻ってきたら、どこかで増殖している合図になる。
 */
final class MedalLedger {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final JavaPlugin plugin;
    private final Path logFile;
    private final File statsFile;
    private long issued;
    private long redeemed;

    MedalLedger(JavaPlugin plugin) {
        this.plugin = plugin;
        Path folder = plugin.getDataFolder().toPath();
        try {
            Files.createDirectories(folder);
        } catch (IOException exception) {
            plugin.getLogger().warning("記録用のフォルダを作れませんでした: " + exception.getMessage());
        }
        this.logFile = folder.resolve("medals.log");
        this.statsFile = folder.resolve("stats.yml").toFile();
        YamlConfiguration stats = YamlConfiguration.loadConfiguration(statsFile);
        this.issued = stats.getLong("issued", 0);
        this.redeemed = stats.getLong("redeemed", 0);
    }

    long issued() {
        return issued;
    }

    long redeemed() {
        return redeemed;
    }

    /** 出回っている枚数（1spaメダル換算）。 */
    long outstanding() {
        return issued - redeemed;
    }

    void recordBuy(UUID uuid, String name, MedalType type, int count, double money) {
        issued += (long) type.value() * count;
        saveStats();
        log("buy", uuid, name, type, count, money);
    }

    void recordRedeem(UUID uuid, String name, MedalType type, int count, double money) {
        redeemed += (long) type.value() * count;
        saveStats();
        log("redeem", uuid, name, type, count, money);
        if (outstanding() < 0) {
            plugin.getLogger().warning("発行した枚数より多くのspaメダルが戻ってきました。増殖の疑いがあります: "
                    + "発行 " + issued + "、回収 " + redeemed + "（最後に戻した人: " + name + "）");
        }
    }

    /** 崩す・束ねるは総額が変わらないので、累計には入れず記録だけ残す。 */
    void recordSplit(UUID uuid, String name, MedalType from, int count) {
        log("split", uuid, name, from, count, 0);
    }

    void recordMerge(UUID uuid, String name, MedalType to, int count) {
        log("merge", uuid, name, to, count, 0);
    }

    private void saveStats() {
        YamlConfiguration stats = new YamlConfiguration();
        stats.set("issued", issued);
        stats.set("redeemed", redeemed);
        try {
            stats.save(statsFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("stats.yml を保存できませんでした: " + exception.getMessage());
        }
    }

    private void log(String action, UUID uuid, String name, MedalType type, int count, double money) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("at", ZonedDateTime.now(ZONE).format(TIMESTAMP));
        fields.put("action", action);
        fields.put("player", name);
        fields.put("uuid", uuid.toString());
        fields.put("medal", type.value());
        fields.put("count", count);
        fields.put("money", money);
        fields.put("issued", issued);
        fields.put("redeemed", redeemed);
        // 非同期にすると、停止の直前の取引がスケジューラごと捨てられて記録から漏れる。
        // 1行の追記なので、その場で書く。
        try {
            Files.writeString(logFile, toJson(fields) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            plugin.getLogger().warning("記録の書き込みに失敗しました: " + exception.getMessage());
        }
    }

    private static String toJson(Map<String, Object> fields) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number) {
                builder.append(value);
            } else {
                builder.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        return builder.append('}').toString();
    }

    private static String escape(String raw) {
        StringBuilder builder = new StringBuilder(raw.length() + 8);
        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }
}

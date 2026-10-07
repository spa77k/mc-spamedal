package dev.spa.spamedal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * config.yml の lottery 節。
 *
 * 売上のうち pool-rate だけを賞金プールに積み、残りは消す。消えたメダルはスパコインに戻らないので、
 * 宝くじはスパコインを吸い取る役目も持つ。
 */
record LotteryConfig(int ticketPrice, BigDecimal poolRate, List<Prize> prizes,
                     DayOfWeek drawDay, LocalTime drawTime, ZoneId zone, int claimDraws) {

    /** 等級。share はプールのうち、この等級の当選者全員に配る割合。 */
    record Prize(String name, int winners, BigDecimal share) {
    }

    private static final List<Prize> DEFAULT_PRIZES = List.of(
            new Prize("1等", 1, new BigDecimal("0.6")),
            new Prize("2等", 3, new BigDecimal("0.25")),
            new Prize("3等", 10, new BigDecimal("0.15")));

    /** MedalConfig.load のあとに呼ぶ。config.yml の読み直しはそちらで済んでいる。 */
    static LotteryConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        int ticketPrice = config.getInt("lottery.ticket-price", 10);
        if (ticketPrice <= 0) {
            plugin.getLogger().warning("lottery.ticket-price が0以下のため 10 を使います。");
            ticketPrice = 10;
        }
        BigDecimal poolRate = BigDecimal.valueOf(config.getDouble("lottery.pool-rate", 0.5));
        if (poolRate.signum() < 0 || poolRate.compareTo(BigDecimal.ONE) > 0) {
            // 1を超えると、売上より多く払い戻してメダルが増えてしまう。
            plugin.getLogger().warning("lottery.pool-rate は0〜1で指定してください。0.5 を使います。");
            poolRate = new BigDecimal("0.5");
        }

        List<Prize> prizes = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map<?, ?> entry : config.getMapList("lottery.prizes")) {
            Object name = entry.get("name");
            Object winners = entry.get("winners");
            Object share = entry.get("share");
            if (name == null || !(winners instanceof Number count) || !(share instanceof Number rate)
                    || count.intValue() <= 0 || rate.doubleValue() <= 0) {
                plugin.getLogger().warning("lottery.prizes に読めない等級があるため飛ばします: " + entry);
                continue;
            }
            Prize prize = new Prize(String.valueOf(name), count.intValue(), BigDecimal.valueOf(rate.doubleValue()));
            prizes.add(prize);
            total = total.add(prize.share());
        }
        if (prizes.isEmpty() || total.compareTo(BigDecimal.ONE) > 0) {
            plugin.getLogger().warning("lottery.prizes が空か、share の合計が1を超えています。既定の等級を使います。");
            prizes = DEFAULT_PRIZES;
        }

        DayOfWeek day;
        LocalTime time;
        ZoneId zone;
        try {
            day = DayOfWeek.valueOf(config.getString("lottery.draw.day", "SUNDAY").toUpperCase(Locale.ROOT));
            time = LocalTime.parse(config.getString("lottery.draw.time", "21:00"));
            zone = ZoneId.of(config.getString("lottery.draw.zone", "Asia/Tokyo"));
        } catch (IllegalArgumentException | DateTimeException exception) {
            plugin.getLogger().warning("lottery.draw が読めないため、毎週日曜21:00（日本時間）にします: "
                    + exception.getMessage());
            day = DayOfWeek.SUNDAY;
            time = LocalTime.of(21, 0);
            zone = ZoneId.of("Asia/Tokyo");
        }

        int claimDraws = config.getInt("lottery.claim-draws", 4);
        if (claimDraws <= 0) {
            plugin.getLogger().warning("lottery.claim-draws が0以下のため 4 を使います。");
            claimDraws = 4;
        }
        return new LotteryConfig(ticketPrice, poolRate, List.copyOf(prizes), day, time, zone, claimDraws);
    }

    /** 1枚売れるごとに賞金プールへ積む枚数。端数は切り捨て、そのぶんは消える。 */
    long poolPerTicket() {
        return BigDecimal.valueOf(ticketPrice).multiply(poolRate).setScale(0, RoundingMode.DOWN).longValue();
    }

    /** この等級の当選者1人あたりの当選金。 */
    long prizeEach(Prize prize, long pool) {
        return BigDecimal.valueOf(pool).multiply(prize.share())
                .divide(BigDecimal.valueOf(prize.winners()), 0, RoundingMode.DOWN).longValue();
    }

    /** after より後の、最初の抽選日時（エポックミリ秒）。 */
    long nextDrawAfter(long after) {
        ZonedDateTime now = java.time.Instant.ofEpochMilli(after).atZone(zone);
        ZonedDateTime candidate = now.with(TemporalAdjusters.nextOrSame(drawDay)).with(drawTime)
                .withSecond(0).withNano(0);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate.toInstant().toEpochMilli();
    }

    /** 「10/11(日) 21:00」のような表示。 */
    String formatTime(long epochMillis) {
        ZonedDateTime time = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone);
        String week = "月火水木金土日".substring(time.getDayOfWeek().getValue() - 1, time.getDayOfWeek().getValue());
        return String.format("%d/%d(%s) %02d:%02d", time.getMonthValue(), time.getDayOfMonth(), week,
                time.getHour(), time.getMinute());
    }

    /** 「50%」のような割合の表示。 */
    static String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }
}

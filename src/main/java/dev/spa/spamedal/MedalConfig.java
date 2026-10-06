package dev.spa.spamedal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** config.yml の値。金額は小数の誤差を避けるため BigDecimal で計算し、0.01S単位に切り捨てる。 */
record MedalConfig(BigDecimal unitPrice, BigDecimal redeemRate, String symbol, boolean suffix) {

    static MedalConfig load(JavaPlugin plugin) {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        BigDecimal unitPrice = BigDecimal.valueOf(config.getDouble("unit-price", 2.5));
        BigDecimal redeemRate = BigDecimal.valueOf(config.getDouble("redeem-rate", 0.8));
        if (unitPrice.signum() <= 0) {
            plugin.getLogger().warning("unit-price が0以下のため 2.5 を使います。");
            unitPrice = new BigDecimal("2.5");
        }
        if (redeemRate.signum() < 0 || redeemRate.compareTo(BigDecimal.ONE) > 0) {
            // 1を超えると、買ってすぐ戻すだけでスパコインが増えてしまう。
            plugin.getLogger().warning("redeem-rate は0〜1で指定してください。0.8 を使います。");
            redeemRate = new BigDecimal("0.8");
        }
        return new MedalConfig(unitPrice, redeemRate,
                config.getString("currency.symbol", "S"), config.getBoolean("currency.suffix", true));
    }

    /** count 枚を買うときの値段。 */
    double buyPrice(MedalType type, int count) {
        return unitPrice.multiply(BigDecimal.valueOf((long) type.value() * count))
                .setScale(2, RoundingMode.DOWN).doubleValue();
    }

    /** count 枚を戻したときに受け取る額。 */
    double redeemPrice(MedalType type, int count) {
        return unitPrice.multiply(redeemRate).multiply(BigDecimal.valueOf((long) type.value() * count))
                .setScale(2, RoundingMode.DOWN).doubleValue();
    }

    String formatMoney(double amount) {
        String number = BigDecimal.valueOf(amount).setScale(2, RoundingMode.DOWN)
                .stripTrailingZeros().toPlainString();
        return suffix ? number + symbol : symbol + number;
    }

    /** 「2割引き」のような戻し率の説明。 */
    String redeemDiscount() {
        BigDecimal percent = BigDecimal.ONE.subtract(redeemRate).multiply(BigDecimal.valueOf(100))
                .stripTrailingZeros();
        return percent.toPlainString() + "%引き";
    }
}

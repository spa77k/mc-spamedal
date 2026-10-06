package dev.spa.spamedal;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Vault 経由でサーバーの経済プラグイン（EssentialsX）を使う。 */
final class EconomyService {

    private final Economy economy;

    private EconomyService(Economy economy) {
        this.economy = economy;
    }

    /** 経済プラグインが登録されていなければ null を返す。 */
    static EconomyService hook(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> provider =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : new EconomyService(provider.getProvider());
    }

    Wallet wallet(OfflinePlayer player) {
        return new Wallet() {
            @Override
            public double balance() {
                return economy.getBalance(player);
            }

            @Override
            public boolean withdraw(double amount) {
                return economy.withdrawPlayer(player, amount).transactionSuccess();
            }

            @Override
            public boolean deposit(double amount) {
                return economy.depositPlayer(player, amount).transactionSuccess();
            }
        };
    }
}

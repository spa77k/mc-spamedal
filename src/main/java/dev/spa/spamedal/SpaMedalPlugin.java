package dev.spa.spamedal;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** スパコインと交換できる物理通貨「スパメダル」と、その両替所。両替所ではスパくじも売る。 */
public final class SpaMedalPlugin extends JavaPlugin {

    private MedalItems medalItems;
    private MedalLedger ledger;
    private MedalExchange exchange;
    private Lottery lottery;
    private EconomyService economy;
    private ExchangeGui gui;
    private MedalNpc npc;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.economy = EconomyService.hook(this);
        if (economy == null) {
            getLogger().severe("Vault に経済プラグインが登録されていません。SpaMedal を無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.medalItems = new MedalItems(this);
        this.ledger = new MedalLedger(this);
        this.exchange = new MedalExchange(medalItems, ledger, MedalConfig.load(this));
        LotteryTickets tickets = new LotteryTickets(this);
        this.lottery = new Lottery(this, medalItems, tickets, exchange, ledger, LotteryConfig.load(this));
        this.gui = new ExchangeGui(this);
        this.npc = new MedalNpc(this);

        getServer().getPluginManager().registerEvents(new ExchangeListener(this), this);
        getServer().getPluginManager().registerEvents(new MedalGuardListener(medalItems, tickets), this);
        getServer().getPluginManager().registerEvents(npc, this);
        // 抽選日時を過ぎたかを1分ごとに見る。停止中に日時を過ぎていたら、起動して最初の確認で抽選する。
        getServer().getScheduler().runTaskTimer(this, lottery::tick, 20L * 10, 20L * 60);

        MedalCommand command = new MedalCommand(this);
        for (String name : new String[]{"medal", "medalnpc", "lottery"}) {
            PluginCommand registered = getCommand(name);
            if (registered == null) {
                getLogger().warning("コマンド " + name + " が plugin.yml にありません。");
                continue;
            }
            registered.setExecutor(command);
            registered.setTabCompleter(command);
        }
        getLogger().info("発行 " + ledger.issued() + " 枚、回収 " + ledger.redeemed() + " 枚（1スパメダル換算）を読み込みました。");
    }

    void reloadMedalConfig() {
        exchange.setConfig(MedalConfig.load(this));
        lottery.setConfig(LotteryConfig.load(this));
    }

    Lottery lottery() {
        return lottery;
    }

    MedalItems medalItems() {
        return medalItems;
    }

    MedalLedger ledger() {
        return ledger;
    }

    MedalExchange exchange() {
        return exchange;
    }

    EconomyService economy() {
        return economy;
    }

    ExchangeGui gui() {
        return gui;
    }

    MedalNpc npc() {
        return npc;
    }
}

package dev.spa.spamedal;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /medal（発行状況・再読み込み）と /medalnpc（両替所の設置）。 */
final class MedalCommand implements CommandExecutor, TabCompleter {

    /** ロビーの案内係・表彰台と同じく、ロビーを編集できる権限でも設置できる。 */
    private static final String LOBBY_PERMISSION = "spsmc.lobby.break";

    private final SpaMedalPlugin plugin;

    MedalCommand(SpaMedalPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        boolean npc = command.getName().equals("medalnpc");
        if (!sender.hasPermission("spamedal.admin") && !(npc && sender.hasPermission(LOBBY_PERMISSION))) {
            sender.sendMessage(Text.prefixed("&cこのコマンドを使う権限がありません。"));
            return true;
        }
        String sub = args.length == 0 ? (npc ? "status" : "stats") : args[0];
        return npc ? npc(sender, sub) : medal(sender, sub);
    }

    private boolean medal(CommandSender sender, String sub) {
        switch (sub) {
            case "stats" -> {
                MedalLedger ledger = plugin.ledger();
                MedalConfig config = plugin.exchange().config();
                long outstanding = ledger.outstanding();
                sender.sendMessage(Text.prefixed("&6spaメダルの発行状況（1spaメダル換算）"));
                sender.sendMessage(Text.of("&7発行（累計）: &f" + ledger.issued() + "枚"));
                sender.sendMessage(Text.of("&7回収（累計）: &f" + ledger.redeemed() + "枚"));
                sender.sendMessage(Text.of("&7出回っている枚数: &f" + outstanding + "枚 &7（全部戻ると "
                        + config.formatMoney(outstanding * config.redeemPrice(MedalType.ONE, 1)) + " の支払い）"));
                if (outstanding < 0) {
                    sender.sendMessage(Text.of("&c回収が発行を上回っています。増殖の疑いがあります。medals.log を確認してください。"));
                }
            }
            case "reload" -> {
                plugin.reloadMedalConfig();
                sender.sendMessage(Text.prefixed("&a設定を読み込み直しました。"));
            }
            default -> sender.sendMessage(Text.prefixed("&7使い方: /medal <stats|reload>"));
        }
        return true;
    }

    private boolean npc(CommandSender sender, String sub) {
        switch (sub) {
            case "here" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Text.prefixed("&cゲーム内から実行してください。"));
                    return true;
                }
                plugin.npc().spawn(player.getLocation());
                sender.sendMessage(Text.prefixed("&a両替所の村人を足元に置きました。"));
            }
            case "remove" -> sender.sendMessage(Text.prefixed(plugin.npc().remove()
                    ? "&a両替所の村人を取り除きました。" : "&7取り除く村人が見つかりませんでした。"));
            case "status" -> {
                Location location = plugin.npc().savedLocation();
                if (location == null) {
                    sender.sendMessage(Text.prefixed("&7両替所の村人は置かれていません。/medalnpc here で置けます。"));
                } else {
                    boolean found = plugin.npc().find() != null;
                    sender.sendMessage(Text.prefixed("&7設置場所: &f" + location.getWorld().getName() + " "
                            + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()
                            + (found ? " &a（いる）" : " &c（見つからない。/medalnpc here で置き直してください）")));
                }
            }
            default -> sender.sendMessage(Text.prefixed("&7使い方: /medalnpc <here|remove|status>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = command.getName().equals("medalnpc")
                ? List.of("here", "remove", "status") : List.of("stats", "reload");
        return options.stream().filter(option -> option.startsWith(args[0])).toList();
    }
}

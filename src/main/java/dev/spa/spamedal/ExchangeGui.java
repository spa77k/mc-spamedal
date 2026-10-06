package dev.spa.spamedal;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 両替所の画面を組み立てる。統合版から見ても崩れないよう、チェスト型の枠とバニラのアイテムだけを使う。
 *
 * 1〜3段目が額面ごとの売買（左から、メダル・買う1/10/64枚・戻す1/10/全部）、5段目が崩す・束ねる。
 */
final class ExchangeGui {

    static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    private static final int CLOSE_SLOT = 49;
    private static final int[] BUY_COUNTS = {1, 10, 64};
    private static final int[] REDEEM_COUNTS = {1, 10};

    private final SpaMedalPlugin plugin;

    ExchangeGui(SpaMedalPlugin plugin) {
        this.plugin = plugin;
    }

    void open(Player player) {
        ExchangeHolder holder = new ExchangeHolder();
        Inventory inventory = plugin.getServer().createInventory(holder, SIZE, Text.of("&8[&6スパメダル両替所&8]"));
        holder.setInventory(inventory);
        render(player, holder);
        player.openInventory(inventory);
    }

    /** 所持金と枚数が変わるたびに描き直す。 */
    void render(Player player, ExchangeHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        MedalExchange exchange = plugin.exchange();
        MedalConfig config = exchange.config();
        double balance = plugin.economy().wallet(player).balance();

        inventory.setItem(INFO_SLOT, button(Material.SUNFLOWER, 1, "&6スパメダル両替所", List.of(
                "&7所持金: &f" + config.formatMoney(balance),
                "",
                "&7買うとき: 1スパメダル = " + config.formatMoney(config.buyPrice(MedalType.ONE, 1)),
                "&7戻すとき: 1スパメダル = " + config.formatMoney(config.redeemPrice(MedalType.ONE, 1)),
                "&c戻すときは" + config.redeemDiscount() + "になります。",
                "&7崩す・束ねるは手数料なし")));

        MedalType[] types = MedalType.values();
        for (int row = 0; row < types.length; row++) {
            MedalType type = types[row];
            int base = (row + 1) * 9;
            int have = exchange.count(player.getInventory(), type);

            ItemStack medal = plugin.medalItems().create(type, 1);
            ItemMeta meta = medal.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore());
            lore.add(Text.item(""));
            lore.add(Text.item("&a買う: &f" + config.formatMoney(config.buyPrice(type, 1)) + " / 枚"));
            lore.add(Text.item("&c戻す: &f" + config.formatMoney(config.redeemPrice(type, 1)) + " / 枚"));
            lore.add(Text.item("&7持っている枚数: &f" + have + "枚"));
            meta.lore(lore);
            medal.setItemMeta(meta);
            inventory.setItem(base, medal);

            for (int index = 0; index < BUY_COUNTS.length; index++) {
                int count = BUY_COUNTS[index];
                int slot = base + 2 + index;
                inventory.setItem(slot, button(Material.LIME_CONCRETE, count,
                        "&a" + count + "枚買う", List.of(
                                "&7" + type.displayName() + " &7× " + count,
                                "&7支払う: &f" + config.formatMoney(config.buyPrice(type, count)))));
                holder.put(slot, new ExchangeHolder.Action(ExchangeHolder.Kind.BUY, type, count));
            }
            for (int index = 0; index < REDEEM_COUNTS.length; index++) {
                int count = REDEEM_COUNTS[index];
                int slot = base + 6 + index;
                inventory.setItem(slot, button(Material.RED_CONCRETE, count,
                        "&c" + count + "枚戻す", List.of(
                                "&7" + type.displayName() + " &7× " + count,
                                "&7受け取る: &f" + config.formatMoney(config.redeemPrice(type, count)))));
                holder.put(slot, new ExchangeHolder.Action(ExchangeHolder.Kind.REDEEM, type, count));
            }
            inventory.setItem(base + 8, button(Material.ORANGE_CONCRETE, 1, "&6全部戻す", List.of(
                    "&7" + type.displayName() + " &7× " + have,
                    "&7受け取る: &f" + config.formatMoney(config.redeemPrice(type, have)))));
            holder.put(base + 8, new ExchangeHolder.Action(ExchangeHolder.Kind.REDEEM, type, null));
        }

        exchangeButton(holder, 37, ExchangeHolder.Kind.SPLIT, MedalType.HUNDRED);
        exchangeButton(holder, 38, ExchangeHolder.Kind.MERGE, MedalType.HUNDRED);
        exchangeButton(holder, 42, ExchangeHolder.Kind.SPLIT, MedalType.TEN);
        exchangeButton(holder, 43, ExchangeHolder.Kind.MERGE, MedalType.TEN);

        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, 1, "&c閉じる", List.of()));
        holder.put(CLOSE_SLOT, new ExchangeHolder.Action(ExchangeHolder.Kind.CLOSE, null, null));
    }

    private void exchangeButton(ExchangeHolder holder, int slot, ExchangeHolder.Kind kind, MedalType type) {
        MedalType smaller = type.smaller();
        String name = kind == ExchangeHolder.Kind.SPLIT
                ? "&b崩す: " + type.value() + " → " + smaller.value() + "×10"
                : "&d束ねる: " + smaller.value() + "×10 → " + type.value();
        String detail = kind == ExchangeHolder.Kind.SPLIT
                ? "&7" + type.displayName() + " &71枚を " + smaller.displayName() + " &710枚に"
                : "&7" + smaller.displayName() + " &710枚を " + type.displayName() + " &71枚に";
        Material material = kind == ExchangeHolder.Kind.SPLIT ? Material.LIGHT_BLUE_CONCRETE : Material.MAGENTA_CONCRETE;
        holder.getInventory().setItem(slot, button(material, 1, name, List.of(detail, "&7手数料なし")));
        holder.put(slot, new ExchangeHolder.Action(kind, type, 1));
    }

    private ItemStack button(Material material, int amount, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(amount, material.getMaxStackSize())));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.item(name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Text.item(line));
        }
        meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }
}

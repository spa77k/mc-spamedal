package dev.spa.spamedal;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * スパくじの券。メダルと同じく、回と券番号を PersistentDataContainer に書き込み、判定もそこだけを見る。
 * 券番号は回ごとの通し番号なので、券どうしは重ならない。
 */
final class LotteryTickets {

    static final Material MATERIAL = Material.PAPER;

    record Ticket(int round, int number) {
    }

    private final NamespacedKey roundKey;
    private final NamespacedKey numberKey;

    LotteryTickets(Plugin plugin) {
        this.roundKey = new NamespacedKey(plugin, "lottery_round");
        this.numberKey = new NamespacedKey(plugin, "lottery_number");
    }

    ItemStack create(int round, int number, String drawTime) {
        ItemStack stack = new ItemStack(MATERIAL);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.item("&eスパくじ 第" + round + "回 &f" + label(number)));
        meta.lore(List.of(
                Text.item("&7抽選: &f" + drawTime),
                Text.item("&8抽選のあと、ロビーの両替所で当たりを確かめられる")));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(roundKey, PersistentDataType.INTEGER, round);
        data.set(numberKey, PersistentDataType.INTEGER, number);
        stack.setItemMeta(meta);
        return stack;
    }

    /** 券でなければ null を返す。 */
    Ticket read(ItemStack stack) {
        if (stack == null || stack.getType() != MATERIAL || !stack.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer data = stack.getItemMeta().getPersistentDataContainer();
        Integer round = data.get(roundKey, PersistentDataType.INTEGER);
        Integer number = data.get(numberKey, PersistentDataType.INTEGER);
        return round == null || number == null ? null : new Ticket(round, number);
    }

    static String label(int number) {
        return String.format("#%04d", number);
    }
}

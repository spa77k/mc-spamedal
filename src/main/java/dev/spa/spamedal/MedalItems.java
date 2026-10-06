package dev.spa.spamedal;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * メダルの生成と判定。
 *
 * 額面は PersistentDataContainer に書き込み、判定もそこだけを見る。表示名やロアを見ないのは、
 * 金床で同じ名前を付けただけのオウムガイの殻をメダルとして認めないため。
 * 1枚ごとの通し番号は付けない。付けると同じ額面どうしが重ならなくなる。
 */
final class MedalItems {

    static final Material MATERIAL = Material.NAUTILUS_SHELL;

    private final NamespacedKey valueKey;

    MedalItems(Plugin plugin) {
        this.valueKey = new NamespacedKey(plugin, "value");
    }

    /**
     * 価格はロアに書かない。設定で価格を変えたときに、新旧のメダルが重ならなくなるため。
     */
    ItemStack create(MedalType type, int amount) {
        ItemStack stack = new ItemStack(MATERIAL, amount);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.item(type.displayName()));
        meta.lore(List.of(
                Text.item("&7物理版spaコイン"),
                Text.item("&8ロビーの両替所でスパコインと交換できる")));
        meta.setItemModel(new NamespacedKey("spamedal", type.modelId()));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.INTEGER, type.value());
        stack.setItemMeta(meta);
        return stack;
    }

    /** メダルでなければ null を返す。 */
    MedalType typeOf(ItemStack stack) {
        if (stack == null || stack.getType() != MATERIAL || !stack.hasItemMeta()) {
            return null;
        }
        Integer value = stack.getItemMeta().getPersistentDataContainer().get(valueKey, PersistentDataType.INTEGER);
        return value == null ? null : MedalType.ofValue(value);
    }

    boolean isMedal(ItemStack stack) {
        return typeOf(stack) != null;
    }

    boolean containsMedal(ItemStack[] stacks) {
        if (stacks == null) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (isMedal(stack)) {
                return true;
            }
        }
        return false;
    }
}

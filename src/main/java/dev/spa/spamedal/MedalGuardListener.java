package dev.spa.spamedal;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;

/**
 * メダルを材料にさせない。
 *
 * 素材のオウムガイの殻はコンジットの材料になるため、そのままだとメダルが作業台で消えてしまう。
 * 金床・砥石・鍛冶台も、名前やエンチャントを書き換えて見分けにくくなるので止める。
 * 判定は PersistentDataContainer だけを見るので、ただのオウムガイの殻は今までどおり使える。
 * スパくじの券（紙）も、地図や本の材料にして消えないよう同じく止める。
 */
final class MedalGuardListener implements Listener {

    private final MedalItems items;
    private final LotteryTickets tickets;

    MedalGuardListener(MedalItems items, LotteryTickets tickets) {
        this.items = items;
        this.tickets = tickets;
    }

    /** メダルかスパくじの券がひとつでも入っているか。 */
    private boolean guarded(ItemStack[] stacks) {
        if (stacks == null) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (items.isMedal(stack) || tickets.read(stack) != null) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        if (guarded(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter
                && guarded(crafter.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        if (guarded(event.getInventory().getContents())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGrindstone(PrepareGrindstoneEvent event) {
        if (guarded(event.getInventory().getContents())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(PrepareSmithingEvent event) {
        if (guarded(event.getInventory().getContents())) {
            event.setResult(null);
        }
    }
}

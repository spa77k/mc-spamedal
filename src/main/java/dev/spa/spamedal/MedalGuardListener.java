package dev.spa.spamedal;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;

/**
 * メダルを材料にさせない。
 *
 * 素材のオウムガイの殻はコンジットの材料になるため、そのままだとメダルが作業台で消えてしまう。
 * 金床・砥石・鍛冶台も、名前やエンチャントを書き換えて見分けにくくなるので止める。
 * 判定は PersistentDataContainer だけを見るので、ただのオウムガイの殻は今までどおり使える。
 */
final class MedalGuardListener implements Listener {

    private final MedalItems items;

    MedalGuardListener(MedalItems items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        if (items.containsMedal(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter
                && items.containsMedal(crafter.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        if (items.containsMedal(event.getInventory().getContents())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGrindstone(PrepareGrindstoneEvent event) {
        if (items.containsMedal(event.getInventory().getContents())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(PrepareSmithingEvent event) {
        if (items.containsMedal(event.getInventory().getContents())) {
            event.setResult(null);
        }
    }
}

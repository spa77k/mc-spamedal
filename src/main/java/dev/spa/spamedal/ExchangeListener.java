package dev.spa.spamedal;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** 両替画面の操作。画面の中身は飾りなので、あらゆる持ち出しを禁じる。 */
final class ExchangeListener implements Listener {

    private final SpaMedalPlugin plugin;

    ExchangeListener(SpaMedalPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ExchangeHolder holder)) {
            return;
        }
        // 自分の持ち物側をクリックした場合も、シフトクリックで飾りが動くのを防ぐため一律で止める。
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        ExchangeHolder.Action action = holder.action(event.getSlot());
        if (action == null) {
            return;
        }
        if (action.kind() == ExchangeHolder.Kind.CLOSE) {
            player.closeInventory();
            return;
        }

        MedalExchange exchange = plugin.exchange();
        Wallet wallet = plugin.economy().wallet(player);
        MedalExchange.Outcome outcome = switch (action.kind()) {
            case BUY -> exchange.buy(player.getInventory(), wallet, player.getUniqueId(), player.getName(),
                    action.type(), action.count());
            case REDEEM -> exchange.redeem(player.getInventory(), wallet, player.getUniqueId(), player.getName(),
                    action.type(), action.count());
            case SPLIT -> exchange.split(player.getInventory(), player.getUniqueId(), player.getName(), action.type());
            case MERGE -> exchange.merge(player.getInventory(), player.getUniqueId(), player.getName(), action.type());
            case CLOSE -> throw new IllegalStateException();
        };
        player.sendMessage(Text.prefixed(outcome.message()));
        player.playSound(player.getLocation(),
                outcome.ok() ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
        plugin.gui().render(player, holder);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ExchangeHolder) {
            event.setCancelled(true);
        }
    }
}

package dev.spa.spamedal;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** 両替画面。開いている画面が両替所かどうかを、この型で見分ける。枠ごとに押したときの操作を持つ。 */
final class ExchangeHolder implements InventoryHolder {

    enum Kind { BUY, REDEEM, SPLIT, MERGE, CLOSE }

    /** count が null の REDEEM は「全部戻す」。 */
    record Action(Kind kind, MedalType type, Integer count) {
    }

    private final Map<Integer, Action> actions = new HashMap<>();
    private Inventory inventory;

    Action action(int slot) {
        return actions.get(slot);
    }

    void put(int slot, Action action) {
        actions.put(slot, action);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}

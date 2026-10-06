package dev.spa.spamedal;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 両替の本体。買う・戻す・崩す・束ねるの4つを扱う。
 *
 * どの操作も持ち物欄の写しの上で先に組み替え、入りきるかを確かめてからお金を動かし、
 * 最後に写しをまとめて書き戻す。途中で失敗したときに、メダルかお金の片方だけが動くことはない。
 * 持ち物欄だけを対象にし、防具・左手の枠は触らない。
 */
final class MedalExchange {

    record Outcome(boolean ok, String message) {
        static Outcome fail(String message) {
            return new Outcome(false, message);
        }
    }

    private final MedalItems items;
    private final MedalLedger ledger;
    private MedalConfig config;

    MedalExchange(MedalItems items, MedalLedger ledger, MedalConfig config) {
        this.items = items;
        this.ledger = ledger;
        this.config = config;
    }

    void setConfig(MedalConfig config) {
        this.config = config;
    }

    MedalConfig config() {
        return config;
    }

    int count(Inventory inventory, MedalType type) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (items.typeOf(stack) == type) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    Outcome buy(Inventory inventory, Wallet wallet, UUID uuid, String name, MedalType type, int count) {
        if (count <= 0) {
            return Outcome.fail("&c枚数が正しくありません。");
        }
        double price = config.buyPrice(type, count);
        if (wallet.balance() < price) {
            return Outcome.fail("&cスパコインが足りません。" + config.formatMoney(price) + " 必要です。");
        }
        ItemStack[] contents = copy(inventory.getStorageContents());
        if (add(contents, items.create(type, 1), count) > 0) {
            return Outcome.fail("&c持ち物がいっぱいです。空きを作ってからもう一度どうぞ。");
        }
        if (!wallet.withdraw(price)) {
            return Outcome.fail("&cスパコインを引き出せませんでした。");
        }
        inventory.setStorageContents(contents);
        ledger.recordBuy(uuid, name, type, count, price);
        return new Outcome(true, "&a" + type.displayName() + " &aを" + count + "枚買いました。&7（-"
                + config.formatMoney(price) + "）");
    }

    /** count が null なら、持っているぶんを全部戻す。 */
    Outcome redeem(Inventory inventory, Wallet wallet, UUID uuid, String name, MedalType type, Integer count) {
        int have = count(inventory, type);
        int amount = count == null ? have : count;
        if (amount <= 0 || have == 0) {
            return Outcome.fail("&c" + type.displayName() + " &cを持っていません。");
        }
        if (have < amount) {
            return Outcome.fail("&c" + type.displayName() + " &cが足りません。持っているのは" + have + "枚です。");
        }
        ItemStack[] contents = copy(inventory.getStorageContents());
        remove(contents, type, amount);
        double money = config.redeemPrice(type, amount);
        if (!wallet.deposit(money)) {
            return Outcome.fail("&cスパコインを入金できませんでした。メダルはそのままです。");
        }
        inventory.setStorageContents(contents);
        ledger.recordRedeem(uuid, name, type, amount, money);
        return new Outcome(true, "&a" + type.displayName() + " &aを" + amount + "枚戻しました。&7（+"
                + config.formatMoney(money) + "）");
    }

    /** 1枚を、ひとつ下の額面10枚に崩す。 */
    Outcome split(Inventory inventory, UUID uuid, String name, MedalType type) {
        MedalType smaller = type.smaller();
        if (smaller == null) {
            return Outcome.fail("&cこれ以上は崩せません。");
        }
        if (count(inventory, type) < 1) {
            return Outcome.fail("&c" + type.displayName() + " &cを持っていません。");
        }
        ItemStack[] contents = copy(inventory.getStorageContents());
        remove(contents, type, 1);
        if (add(contents, items.create(smaller, 1), 10) > 0) {
            return Outcome.fail("&c持ち物がいっぱいです。空きを作ってからもう一度どうぞ。");
        }
        inventory.setStorageContents(contents);
        ledger.recordSplit(uuid, name, type, 1);
        return new Outcome(true, "&a" + type.displayName() + " &a1枚を " + smaller.displayName() + " &a10枚に崩しました。");
    }

    /** ひとつ下の額面10枚を、1枚に束ねる。type は束ねたあとの額面。 */
    Outcome merge(Inventory inventory, UUID uuid, String name, MedalType type) {
        MedalType smaller = type.smaller();
        if (smaller == null) {
            return Outcome.fail("&cこれ以上は束ねられません。");
        }
        if (count(inventory, smaller) < 10) {
            return Outcome.fail("&c" + smaller.displayName() + " &cが10枚必要です。");
        }
        ItemStack[] contents = copy(inventory.getStorageContents());
        remove(contents, smaller, 10);
        if (add(contents, items.create(type, 1), 1) > 0) {
            return Outcome.fail("&c持ち物がいっぱいです。空きを作ってからもう一度どうぞ。");
        }
        inventory.setStorageContents(contents);
        ledger.recordMerge(uuid, name, type, 1);
        return new Outcome(true, "&a" + smaller.displayName() + " &a10枚を " + type.displayName() + " &a1枚に束ねました。");
    }

    private static ItemStack[] copy(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copy[index] = contents[index] == null ? null : contents[index].clone();
        }
        return copy;
    }

    /** 同じメダルの山へ先に足し、残りを空き枠へ置く。入りきらなかった枚数を返す。 */
    private static int add(ItemStack[] contents, ItemStack template, int amount) {
        int max = template.getMaxStackSize();
        for (int index = 0; index < contents.length && amount > 0; index++) {
            ItemStack stack = contents[index];
            if (stack != null && !stack.getType().isAir() && stack.isSimilar(template) && stack.getAmount() < max) {
                int moved = Math.min(amount, max - stack.getAmount());
                stack.setAmount(stack.getAmount() + moved);
                amount -= moved;
            }
        }
        for (int index = 0; index < contents.length && amount > 0; index++) {
            if (contents[index] == null || contents[index].getType().isAir()) {
                ItemStack stack = template.clone();
                int moved = Math.min(amount, max);
                stack.setAmount(moved);
                contents[index] = stack;
                amount -= moved;
            }
        }
        return amount;
    }

    private void remove(ItemStack[] contents, MedalType type, int amount) {
        for (int index = 0; index < contents.length && amount > 0; index++) {
            ItemStack stack = contents[index];
            if (items.typeOf(stack) != type) {
                continue;
            }
            int taken = Math.min(amount, stack.getAmount());
            amount -= taken;
            if (taken == stack.getAmount()) {
                contents[index] = null;
            } else {
                stack.setAmount(stack.getAmount() - taken);
            }
        }
    }
}

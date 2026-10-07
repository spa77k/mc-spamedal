package dev.spa.spamedal;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 隔離Paperで、メダルの判定・両替4種・記録・作業台の禁止を確かめる。
 * 画面の見た目、村人、統合版の表示は実クライアントで別に確かめる。
 */
public final class MedalProbe extends JavaPlugin {

    private JavaPlugin medal;
    private ClassLoader loader;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                runProbe();
                getLogger().info("MEDAL_PROBE_PASS");
            } catch (Throwable error) {
                getLogger().log(Level.SEVERE, "MEDAL_PROBE_FAIL", error);
            } finally {
                Bukkit.shutdown();
            }
        }, 40);
    }

    private static void check(boolean result, String message) {
        if (!result) {
            throw new AssertionError(message);
        }
        Bukkit.getLogger().info("ok: " + message);
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                    method.setAccessible(true);
                    return method.invoke(target, args);
                }
            }
        }
        throw new NoSuchMethodException(name);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object type(String name) throws Exception {
        Class enumType = Class.forName("dev.spa.spamedal.MedalType", true, loader);
        return Enum.valueOf(enumType, name);
    }

    /** 残高だけを持つ口座。 */
    private final double[] balance = {0};

    private Object wallet() throws Exception {
        Class<?> walletType = Class.forName("dev.spa.spamedal.Wallet", true, loader);
        return Proxy.newProxyInstance(loader, new Class<?>[]{walletType}, (proxy, method, args) -> switch (method.getName()) {
            case "balance" -> balance[0];
            case "withdraw" -> {
                double amount = (double) args[0];
                if (balance[0] < amount) yield false;
                balance[0] -= amount;
                yield true;
            }
            case "deposit" -> {
                balance[0] += (double) args[0];
                yield true;
            }
            default -> null;
        });
    }

    private boolean ok(Object outcome) throws Exception {
        return (boolean) call(outcome, "ok");
    }

    private void runProbe() throws Exception {
        medal = (JavaPlugin) Bukkit.getPluginManager().getPlugin("SpaMedal");
        check(medal != null && medal.isEnabled(), "SpaMedal が有効");
        loader = medal.getClass().getClassLoader();
        Object items = call(medal, "medalItems");
        Object exchange = call(medal, "exchange");
        Object ledger = call(medal, "ledger");
        Object one = type("ONE");
        Object ten = type("TEN");
        Object hundred = type("HUNDRED");
        Object config = call(exchange, "config");

        // 価格
        check((double) call(config, "buyPrice", one, 1) == 2.5, "1スパメダルは2.5S");
        check((double) call(config, "redeemPrice", one, 1) == 2.0, "1スパメダルは戻すと2.0S");
        check((double) call(config, "buyPrice", hundred, 1) == 250.0, "100スパメダルは250S");
        check((double) call(config, "redeemPrice", hundred, 1) == 200.0, "100スパメダルは戻すと200S");
        check((double) call(config, "redeemPrice", ten, 3) == 60.0, "10スパメダル3枚は戻すと60S");

        // 本物と偽物
        ItemStack real = (ItemStack) call(items, "create", ten, 1);
        check(real.getType() == Material.NAUTILUS_SHELL, "素材はオウムガイの殻");
        check(call(items, "typeOf", real) == ten, "本物の10スパメダルを見分ける");
        ItemStack fake = new ItemStack(Material.NAUTILUS_SHELL);
        ItemMeta fakeMeta = fake.getItemMeta();
        fakeMeta.displayName(real.getItemMeta().displayName());
        fakeMeta.lore(real.getItemMeta().lore());
        fake.setItemMeta(fakeMeta);
        check(call(items, "typeOf", fake) == null, "名前とロアだけ同じ偽物はメダルではない");

        UUID id = UUID.nameUUIDFromBytes("MedalProbe".getBytes());
        Inventory inventory = Bukkit.createInventory(null, 36);
        Object wallet = wallet();
        long issued0 = (long) call(ledger, "issued");
        long redeemed0 = (long) call(ledger, "redeemed");

        // 買う
        balance[0] = 100;
        check(ok(call(exchange, "buy", inventory, wallet, id, "Probe", one, 10)), "1スパメダルを10枚買える");
        check(balance[0] == 75, "25S引かれる");
        check((int) call(exchange, "count", inventory, one) == 10, "10枚持っている");
        check(!ok(call(exchange, "buy", inventory, wallet, id, "Probe", hundred, 1)), "残高不足では100スパメダルを買えない");
        check(balance[0] == 75 && (int) call(exchange, "count", inventory, hundred) == 0, "失敗しても何も動かない");

        // 戻す
        check(ok(call(exchange, "redeem", inventory, wallet, id, "Probe", one, 5)), "5枚戻せる");
        check(balance[0] == 85 && (int) call(exchange, "count", inventory, one) == 5, "5枚で10S戻る");
        check(!ok(call(exchange, "redeem", inventory, wallet, id, "Probe", one, 6)), "持っている以上は戻せない");

        // 偽物は数えない・戻せない
        inventory.addItem(fake.clone());
        check((int) call(exchange, "count", inventory, one) == 5, "偽物は枚数に入らない");

        // 崩す・束ねる
        balance[0] = 1000;
        check(ok(call(exchange, "buy", inventory, wallet, id, "Probe", hundred, 1)), "100スパメダルを買える");
        check(ok(call(exchange, "split", inventory, id, "Probe", hundred)), "100を10×10に崩せる");
        check((int) call(exchange, "count", inventory, hundred) == 0
                && (int) call(exchange, "count", inventory, ten) == 10, "10スパメダルが10枚になる");
        check(ok(call(exchange, "split", inventory, id, "Probe", ten)), "10を1×10に崩せる");
        check((int) call(exchange, "count", inventory, one) == 15, "1スパメダルが15枚になる");
        check(ok(call(exchange, "merge", inventory, id, "Probe", ten)), "1×10を10に束ねられる");
        check((int) call(exchange, "count", inventory, one) == 5
                && (int) call(exchange, "count", inventory, ten) == 10, "束ねたあとの枚数");
        check(!ok(call(exchange, "merge", inventory, id, "Probe", ten)), "1スパメダルが10枚ないと束ねられない");
        check(ok(call(exchange, "merge", inventory, id, "Probe", hundred)), "10×10を100に束ねられる");
        check(balance[0] == 750, "崩す・束ねるでお金は動かない");

        // 全部戻す
        check(ok(call(exchange, "redeem", inventory, wallet, id, "Probe", hundred, null)), "100スパメダルを全部戻せる");
        check(balance[0] == 950, "100スパメダル1枚で200S戻る");

        // 持ち物がいっぱい
        Inventory full = Bukkit.createInventory(null, 36);
        for (int slot = 0; slot < 36; slot++) {
            full.setItem(slot, new ItemStack(Material.DIRT, 64));
        }
        check(!ok(call(exchange, "buy", full, wallet, id, "Probe", one, 1)), "持ち物がいっぱいなら買えない");
        check(balance[0] == 950, "いっぱいで断ったときはお金を引かない");
        // 2枚の山から1枚崩しても枠は空かないので、10スパメダル10枚の行き場がない。
        full.setItem(0, (ItemStack) call(items, "create", hundred, 2));
        check(!ok(call(exchange, "split", full, id, "Probe", hundred)), "崩した先が入らなければ崩さない");
        check((int) call(exchange, "count", full, hundred) == 2, "崩せなかったメダルは残る");
        full.setItem(0, (ItemStack) call(items, "create", hundred, 1));
        check(ok(call(exchange, "split", full, id, "Probe", hundred)), "最後の1枚なら空いた枠に崩せる");

        // 記録（1スパメダル換算）: 発行 10 + 100、回収 5 + 100
        check((long) call(ledger, "issued") - issued0 == 110, "発行の累計が110枚ぶん増える");
        check((long) call(ledger, "redeemed") - redeemed0 == 105, "回収の累計が105枚ぶん増える");

        // 作業台にメダルを置くと完成品が出ない
        ItemStack[] matrix = new ItemStack[9];
        for (int slot = 0; slot < 9; slot++) {
            matrix[slot] = (ItemStack) call(items, "create", one, 1);
        }
        matrix[4] = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemStack[] result = {new ItemStack(Material.CONDUIT)};
        CraftingInventory crafting = (CraftingInventory) Proxy.newProxyInstance(CraftingInventory.class.getClassLoader(),
                new Class<?>[]{CraftingInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getMatrix" -> matrix;
                    case "getResult" -> result[0];
                    case "setResult" -> {
                        result[0] = (ItemStack) args[0];
                        yield null;
                    }
                    case "getRecipe" -> null;
                    case "getViewers" -> java.util.List.of();
                    case "hashCode" -> 1;
                    default -> null;
                });
        InventoryView view = (InventoryView) Proxy.newProxyInstance(InventoryView.class.getClassLoader(),
                new Class<?>[]{InventoryView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getTopInventory" -> crafting;
                    case "title" -> Component.empty();
                    default -> null;
                });
        Bukkit.getPluginManager().callEvent(new PrepareItemCraftEvent(crafting, view, false));
        check(result[0] == null, "メダルを使ったコンジットは作れない");
        matrix[0] = new ItemStack(Material.NAUTILUS_SHELL);
        for (int slot = 1; slot < 9; slot++) {
            if (slot != 4) matrix[slot] = new ItemStack(Material.NAUTILUS_SHELL);
        }
        result[0] = new ItemStack(Material.CONDUIT);
        Bukkit.getPluginManager().callEvent(new PrepareItemCraftEvent(crafting, view, false));
        check(result[0] != null, "ただのオウムガイの殻なら作れる");

        // 両替所の村人
        Object npc = call(medal, "npc");
        org.bukkit.Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) call(npc, "spawn", spawn);
        check(villager.getScoreboardTags().contains("spsmc_medal") && !villager.hasAI() && villager.isInvulnerable(),
                "村人はタグ付き・AIなし・無敵");
        check(call(npc, "find") != null, "置いた村人を見つけられる");
        call(npc, "spawn", spawn);
        long count = spawn.getWorld().getNearbyEntities(spawn, 3, 3, 3).stream()
                .filter(entity -> entity.getScoreboardTags().contains("spsmc_medal")).count();
        check(count == 1, "置き直しても1体だけ");
        check((boolean) call(npc, "remove"), "村人を取り除ける");
        check(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "medal stats"), "/medal stats を実行できる");

        probeLottery(items, ledger, id);
    }

    /** 持ち物欄のメダルの合計（1スパメダル換算）。 */
    private int medalValue(Object items, Inventory inventory) throws Exception {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            Object type = call(items, "typeOf", stack);
            if (type != null) {
                total += (int) call(type, "value") * stack.getAmount();
            }
        }
        return total;
    }

    private int ticketCount(Object tickets, Inventory inventory) throws Exception {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (call(tickets, "read", stack) != null) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void probeLottery(Object items, Object ledger, UUID id) throws Exception {
        Object lottery = call(medal, "lottery");
        java.lang.reflect.Constructor<?> ticketsConstructor = Class.forName("dev.spa.spamedal.LotteryTickets", true, loader)
                .getDeclaredConstructor(org.bukkit.plugin.Plugin.class);
        ticketsConstructor.setAccessible(true);
        Object tickets = ticketsConstructor.newInstance(medal);
        check((int) call(lottery, "round") == 1 && (long) call(lottery, "pool") == 0, "スパくじは第1回・プール0から");
        long burned0 = (long) call(ledger, "lotteryBurned");
        long outstanding0 = (long) call(ledger, "outstanding");

        // おつり
        Inventory inventory = Bukkit.createInventory(null, 36);
        inventory.addItem((ItemStack) call(items, "create", type("HUNDRED"), 2));
        check(ok(call(lottery, "buy", inventory, id, "Probe", 1)), "100スパメダルでくじを1枚買える");
        check(medalValue(items, inventory) == 190 && ticketCount(tickets, inventory) == 1, "おつり90スパメダルと券1枚");
        check(ok(call(lottery, "buy", inventory, id, "Probe", 13)), "くじを13枚買える");
        check(medalValue(items, inventory) == 60 && ticketCount(tickets, inventory) == 15, "残り60スパメダルと、おまけ1枚込みの券15枚");
        check(!ok(call(lottery, "buy", inventory, id, "Probe", 10)), "足りなければ買えない");
        check(medalValue(items, inventory) == 60 && ticketCount(tickets, inventory) == 15, "買えなかったときは何も動かない");
        check((int) call(lottery, "sold") == 15 && (long) call(lottery, "pool") == 70, "売上140のうち70がプールに入り、おまけはプールを増やさない");
        check((long) call(ledger, "lotteryBurned") - burned0 == 70, "残りの70は消える");
        check((long) call(ledger, "outstanding") - outstanding0 == -140, "券の代金は出回りから抜ける");

        // 偽物の券
        ItemStack real = null;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (call(tickets, "read", stack) != null) {
                real = stack;
                break;
            }
        }
        ItemStack fake = new ItemStack(Material.PAPER);
        ItemMeta fakeMeta = fake.getItemMeta();
        fakeMeta.displayName(real.getItemMeta().displayName());
        fakeMeta.lore(real.getItemMeta().lore());
        fake.setItemMeta(fakeMeta);
        check(call(tickets, "read", fake) == null, "名前とロアだけ同じ紙は券ではない");

        // 抽選前の券は回収しない
        check(!ok(call(lottery, "claim", inventory, id, "Probe")), "抽選前は換金できない");
        check(ticketCount(tickets, inventory) == 15, "抽選前の券は残る");

        // 抽選: 1等42、2等5×3、3等1×10で67を配り、端数3を持ち越す
        Inventory copy = Bukkit.createInventory(null, 36);
        for (ItemStack stack : inventory.getStorageContents()) {
            if (call(tickets, "read", stack) != null) {
                copy.addItem(stack.clone());
            }
        }
        call(lottery, "draw");
        check((int) call(lottery, "round") == 2 && (int) call(lottery, "sold") == 0, "抽選すると第2回になる");
        check((long) call(lottery, "pool") == 3, "端数3スパメダルを持ち越す");
        check(ok(call(lottery, "claim", inventory, id, "Probe")), "当たりを換金できる");
        check(medalValue(items, inventory) == 127 && ticketCount(tickets, inventory) == 0, "当選金67を受け取り、券は回収される");
        check(ok(call(lottery, "claim", copy, id, "Probe")), "写した券も回収される");
        check(medalValue(items, copy) == 0 && ticketCount(tickets, copy) == 0, "写した券では二重に受け取れない");
        check((long) call(ledger, "outstanding") - outstanding0 == -73, "当選金は出回りに戻る");

        // 券が売れていない回は抽選しない
        call(lottery, "draw");
        check((int) call(lottery, "round") == 2 && (long) call(lottery, "pool") == 3, "0枚の回は抽選せず持ち越す");

        // 換金期限: 第2回の当選金4は、第6回の抽選で消える
        long burned1 = (long) call(ledger, "lotteryBurned");
        Inventory late = Bukkit.createInventory(null, 36);
        late.addItem((ItemStack) call(items, "create", type("TEN"), 5));
        Inventory others = Bukkit.createInventory(null, 36);
        others.addItem((ItemStack) call(items, "create", type("TEN"), 5));
        check(ok(call(lottery, "buy", late, id, "Probe", 1)), "第2回の券を買える");
        call(lottery, "draw");
        for (int round = 3; round <= 6; round++) {
            check(ok(call(lottery, "buy", others, id, "Probe", 1)), "第" + round + "回の券を買える");
            call(lottery, "draw");
        }
        check((long) call(ledger, "lotteryBurned") - burned1 == 25 + 4, "期限切れの当選金4が消える");
        int before = medalValue(items, late);
        check(ok(call(lottery, "claim", late, id, "Probe")), "期限切れの券も回収される");
        check(medalValue(items, late) == before && ticketCount(tickets, late) == 0, "期限切れの券には払わない");
        check(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lottery status"), "/lottery status を実行できる");
    }
}

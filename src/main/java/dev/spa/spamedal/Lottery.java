package dev.spa.spamedal;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * スパくじ。券をスパメダルで売り、決まった日時に売れた券の中から当選番号を選ぶ。
 *
 * 売上のうち pool-rate だけを賞金プールに積み、残りはその場で消す。配りきれなかった端数は次の回へ持ち越す。
 * 当選金は claim-draws 回の抽選が過ぎるまで換金でき、過ぎたぶんは消す。
 * 状態は lottery.yml に、変わるたびにその場で書く。
 *
 * 買う・換金するは両替と同じく、持ち物欄の写しの上で組み替えて入りきるかを確かめてから書き戻す。
 */
final class Lottery {

    /** 抽選済みの回。winners は券番号ごとの等級と当選金。 */
    private record DrawnRound(long drawnAt, Map<Integer, Win> winners, Set<Integer> claimed) {
        long unclaimed() {
            long total = 0;
            for (Map.Entry<Integer, Win> entry : winners.entrySet()) {
                if (!claimed.contains(entry.getKey())) {
                    total += entry.getValue().prize();
                }
            }
            return total;
        }
    }

    private record Win(String tier, long prize) {
    }

    private static final MedalType[] LARGEST_FIRST = {MedalType.HUNDRED, MedalType.TEN, MedalType.ONE};

    private final JavaPlugin plugin;
    private final MedalItems items;
    private final LotteryTickets tickets;
    private final MedalExchange exchange;
    private final MedalLedger ledger;
    private final File file;
    private final SecureRandom random = new SecureRandom();
    private LotteryConfig config;

    private int round = 1;
    private int sold;
    private long pool;
    private long nextDraw;
    private final Map<Integer, DrawnRound> drawn = new TreeMap<>();

    Lottery(JavaPlugin plugin, MedalItems items, LotteryTickets tickets, MedalExchange exchange,
            MedalLedger ledger, LotteryConfig config) {
        this.plugin = plugin;
        this.items = items;
        this.tickets = tickets;
        this.exchange = exchange;
        this.ledger = ledger;
        this.config = config;
        this.file = new File(plugin.getDataFolder(), "lottery.yml");
        load();
        if (nextDraw <= 0) {
            nextDraw = config.nextDrawAfter(System.currentTimeMillis());
            save();
        }
    }

    /** 抽選の曜日・時刻が変わっていれば、次の抽選日時を置き直す。 */
    void setConfig(LotteryConfig config) {
        boolean scheduleChanged = !config.drawDay().equals(this.config.drawDay())
                || !config.drawTime().equals(this.config.drawTime()) || !config.zone().equals(this.config.zone());
        this.config = config;
        if (scheduleChanged) {
            nextDraw = config.nextDrawAfter(System.currentTimeMillis());
            save();
        }
    }

    LotteryConfig config() {
        return config;
    }

    int round() {
        return round;
    }

    int sold() {
        return sold;
    }

    long pool() {
        return pool;
    }

    long nextDraw() {
        return nextDraw;
    }

    /** 賞金プールと、まだ換金されていない当選金の合計。いずれメダルとして出ていく枚数。 */
    long liabilities() {
        long total = pool;
        for (DrawnRound drawnRound : drawn.values()) {
            total += drawnRound.unclaimed();
        }
        return total;
    }

    /** 1分ごとに呼ぶ。抽選日時を過ぎていれば抽選する。 */
    void tick() {
        if (System.currentTimeMillis() >= nextDraw) {
            draw();
        }
    }

    /** 10枚買うごとに1枚おまけを付ける。代金と賞金プールは買った枚数ぶんだけ。 */
    static int bonus(int count) {
        return count / 10;
    }

    MedalExchange.Outcome buy(Inventory inventory, UUID uuid, String name, int count) {
        if (count <= 0) {
            return MedalExchange.Outcome.fail("&c枚数が正しくありません。");
        }
        long cost = (long) config.ticketPrice() * count;
        ItemStack[] contents = MedalExchange.copy(inventory.getStorageContents());
        Map<MedalType, Integer> have = new LinkedHashMap<>();
        long total = 0;
        for (MedalType type : MedalType.values()) {
            int amount = countIn(contents, type);
            have.put(type, amount);
            total += (long) type.value() * amount;
        }
        if (total < cost) {
            return MedalExchange.Outcome.fail("&cスパメダルが足りません。" + cost + "スパメダルぶん必要です。");
        }

        // 小さい額面から払い、足りなければ大きい額面を1枚出しておつりを受け取る。
        long remaining = cost;
        for (MedalType type : MedalType.values()) {
            int taken = (int) Math.min(have.get(type), remaining / type.value());
            exchange.remove(contents, type, taken);
            have.put(type, have.get(type) - taken);
            remaining -= (long) taken * type.value();
        }
        if (remaining > 0) {
            for (MedalType type : MedalType.values()) {
                if (have.get(type) > 0 && type.value() >= remaining) {
                    exchange.remove(contents, type, 1);
                    if (!give(contents, type.value() - remaining)) {
                        return MedalExchange.Outcome.fail("&c持ち物がいっぱいです。空きを作ってからもう一度どうぞ。");
                    }
                    remaining = 0;
                    break;
                }
            }
        }
        if (remaining > 0) {
            return MedalExchange.Outcome.fail("&cスパメダルで支払えませんでした。");
        }

        int issued = count + bonus(count);
        String drawTime = config.formatTime(nextDraw);
        for (int index = 1; index <= issued; index++) {
            if (MedalExchange.add(contents, tickets.create(round, sold + index, drawTime), 1) > 0) {
                return MedalExchange.Outcome.fail("&c持ち物がいっぱいです。券" + issued + "枚ぶんの空きを作ってからもう一度どうぞ。");
            }
        }

        inventory.setStorageContents(contents);
        int first = sold + 1;
        sold += issued;
        long toPool = config.poolPerTicket() * count;
        pool += toPool;
        save();
        ledger.recordLotteryBuy(uuid, name, round, issued, cost, cost - toPool);
        String numbers = issued == 1 ? LotteryTickets.label(first)
                : LotteryTickets.label(first) + "〜" + LotteryTickets.label(sold);
        return new MedalExchange.Outcome(true, "&aスパくじ第" + round + "回を" + issued + "枚買いました（"
                + (issued > count ? "おまけ" + (issued - count) + "枚込み、" : "") + numbers
                + "）。&7（-" + cost + "スパメダル）");
    }

    /** 抽選済みの券をすべて回収し、当たりのぶんをメダルで払う。抽選前の券には触らない。 */
    MedalExchange.Outcome claim(Inventory inventory, UUID uuid, String name) {
        ItemStack[] contents = MedalExchange.copy(inventory.getStorageContents());
        Map<Integer, List<Integer>> paid = new TreeMap<>();
        Map<String, Integer> tiers = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        long prize = 0;
        int collected = 0;
        for (int index = 0; index < contents.length; index++) {
            LotteryTickets.Ticket ticket = tickets.read(contents[index]);
            if (ticket == null || ticket.round() >= round) {
                continue;
            }
            collected += contents[index].getAmount();
            contents[index] = null;
            DrawnRound drawnRound = drawn.get(ticket.round());
            if (drawnRound == null || !seen.add(ticket.round() + ":" + ticket.number())) {
                continue;
            }
            Win win = drawnRound.winners().get(ticket.number());
            if (win == null || drawnRound.claimed().contains(ticket.number())) {
                continue;
            }
            prize += win.prize();
            paid.computeIfAbsent(ticket.round(), key -> new ArrayList<>()).add(ticket.number());
            tiers.merge(win.tier(), 1, Integer::sum);
        }
        if (collected == 0) {
            return MedalExchange.Outcome.fail("&c抽選済みのスパくじを持っていません。");
        }
        if (!give(contents, prize)) {
            return MedalExchange.Outcome.fail("&c持ち物がいっぱいです。空きを作ってからもう一度どうぞ。");
        }

        inventory.setStorageContents(contents);
        for (Map.Entry<Integer, List<Integer>> entry : paid.entrySet()) {
            drawn.get(entry.getKey()).claimed().addAll(entry.getValue());
        }
        save();
        for (Map.Entry<Integer, List<Integer>> entry : paid.entrySet()) {
            long amount = 0;
            for (int number : entry.getValue()) {
                amount += drawn.get(entry.getKey()).winners().get(number).prize();
            }
            ledger.recordLotteryPayout(uuid, name, entry.getKey(), entry.getValue().size(), amount);
        }
        int wins = tiers.values().stream().mapToInt(Integer::intValue).sum();
        if (wins == 0) {
            return new MedalExchange.Outcome(true, "&7当たりはありませんでした。抽選済みの券" + collected + "枚を回収しました。");
        }
        List<String> parts = new ArrayList<>();
        tiers.forEach((tier, number) -> parts.add(tier + "×" + number));
        return new MedalExchange.Outcome(true, "&6当たり！ &f" + String.join("・", parts) + " &aで" + prize
                + "スパメダルを受け取りました。&7（抽選済みの券" + collected + "枚を回収）");
    }

    /**
     * 今の回を抽選する。券が1枚も売れていなければ抽選せず、プールを持ち越して次の日時を待つ。
     * 結果を全体に知らせ、その文面を返す。
     */
    List<String> draw() {
        long now = System.currentTimeMillis();
        nextDraw = config.nextDrawAfter(now);
        List<String> lines = new ArrayList<>();
        if (sold == 0) {
            save();
            lines.add("&7第" + round + "回は券が売れなかったため、抽選を次の" + config.formatTime(nextDraw)
                    + "に延ばします。賞金プール" + pool + "スパメダルは持ち越しです。");
            return lines;
        }

        expire();
        List<Integer> numbers = new ArrayList<>();
        for (int number = 1; number <= sold; number++) {
            numbers.add(number);
        }
        Collections.shuffle(numbers, random);
        Map<Integer, Win> winners = new LinkedHashMap<>();
        long awarded = 0;
        int next = 0;
        lines.add("&e第" + round + "回スパくじの抽選結果 &7（" + sold + "枚、賞金プール" + pool + "スパメダル）");
        for (LotteryConfig.Prize prize : config.prizes()) {
            long each = config.prizeEach(prize, pool);
            if (each <= 0 || next >= numbers.size()) {
                continue;
            }
            List<String> labels = new ArrayList<>();
            for (int count = 0; count < prize.winners() && next < numbers.size(); count++) {
                int number = numbers.get(next++);
                winners.put(number, new Win(prize.name(), each));
                labels.add(LotteryTickets.label(number));
                awarded += each;
            }
            Collections.sort(labels);
            lines.add("&6" + prize.name() + " &f" + each + "スパメダル: &e" + String.join(" ", labels));
        }
        long carried = pool - awarded;
        ledger.recordLotteryDraw(round, sold, pool, awarded, carried);
        drawn.put(round, new DrawnRound(now, winners, new HashSet<>()));
        lines.add("&7当選券はロビーの両替所で換金できます（" + config.claimDraws() + "回あとの抽選まで）。次回は"
                + config.formatTime(nextDraw) + "、持ち越し" + carried + "スパメダル。");
        round++;
        sold = 0;
        pool = carried;
        save();
        for (String line : lines) {
            Bukkit.broadcast(Text.lottery(line));
        }
        return lines;
    }

    /** 今から抽選する回から数えて claim-draws 回以上前の当選金を消す。 */
    private void expire() {
        List<Integer> expired = new ArrayList<>();
        for (Map.Entry<Integer, DrawnRound> entry : drawn.entrySet()) {
            if (round - entry.getKey() >= config.claimDraws()) {
                expired.add(entry.getKey());
            }
        }
        for (int expiredRound : expired) {
            long amount = drawn.remove(expiredRound).unclaimed();
            ledger.recordLotteryExpire(expiredRound, amount);
        }
    }

    private int countIn(ItemStack[] contents, MedalType type) {
        int total = 0;
        for (ItemStack stack : contents) {
            if (items.typeOf(stack) == type) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** value 枚ぶんのメダルを、大きい額面からなるべく少ない枚数で写しに足す。入りきらなければ false。 */
    private boolean give(ItemStack[] contents, long value) {
        for (MedalType type : LARGEST_FIRST) {
            int count = (int) (value / type.value());
            value -= (long) count * type.value();
            if (count > 0 && MedalExchange.add(contents, items.create(type, 1), count) > 0) {
                return false;
            }
        }
        return true;
    }

    private void load() {
        YamlConfiguration state = YamlConfiguration.loadConfiguration(file);
        round = Math.max(1, state.getInt("round", 1));
        sold = Math.max(0, state.getInt("sold", 0));
        pool = Math.max(0, state.getLong("pool", 0));
        nextDraw = state.getLong("next-draw", 0);
        ConfigurationSection rounds = state.getConfigurationSection("rounds");
        if (rounds == null) {
            return;
        }
        for (String key : rounds.getKeys(false)) {
            ConfigurationSection section = rounds.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            Map<Integer, Win> winners = new LinkedHashMap<>();
            ConfigurationSection winnerSection = section.getConfigurationSection("winners");
            if (winnerSection != null) {
                for (String number : winnerSection.getKeys(false)) {
                    winners.put(Integer.parseInt(number), new Win(
                            winnerSection.getString(number + ".tier", "?"), winnerSection.getLong(number + ".prize")));
                }
            }
            drawn.put(Integer.parseInt(key), new DrawnRound(section.getLong("drawn-at"), winners,
                    new HashSet<>(section.getIntegerList("claimed"))));
        }
    }

    private void save() {
        YamlConfiguration state = new YamlConfiguration();
        state.set("round", round);
        state.set("sold", sold);
        state.set("pool", pool);
        state.set("next-draw", nextDraw);
        for (Map.Entry<Integer, DrawnRound> entry : drawn.entrySet()) {
            String path = "rounds." + entry.getKey();
            DrawnRound drawnRound = entry.getValue();
            state.set(path + ".drawn-at", drawnRound.drawnAt());
            for (Map.Entry<Integer, Win> win : drawnRound.winners().entrySet()) {
                state.set(path + ".winners." + win.getKey() + ".tier", win.getValue().tier());
                state.set(path + ".winners." + win.getKey() + ".prize", win.getValue().prize());
            }
            state.set(path + ".claimed", new ArrayList<>(new java.util.TreeSet<>(drawnRound.claimed())));
        }
        try {
            state.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("lottery.yml を保存できませんでした: " + exception.getMessage());
        }
    }
}

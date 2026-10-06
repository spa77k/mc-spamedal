package dev.spa.spamedal;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;

/**
 * 両替所の村人。右クリックすると取引画面ではなく両替画面を開く。
 *
 * ロビーの案内係と同じく、AI・ダメージ・取引を止めて動かないようにする。
 * スコアボードタグ spsmc_medal で見分け、置いた場所とUUIDを npc.yml に残す。
 * 置き直すときは、前の村人のチャンクを読み込んでから取り除き、二重に立たないようにする。
 */
final class MedalNpc implements Listener {

    static final String TAG = "spsmc_medal";

    private final SpaMedalPlugin plugin;
    private final File file;

    MedalNpc(SpaMedalPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npc.yml");
    }

    Villager spawn(Location at) {
        remove();
        Location location = at.getBlock().getLocation().add(0.5, 0, 0.5);
        location.setYaw(at.getYaw());
        Villager villager = at.getWorld().spawn(location, Villager.class, spawned -> {
            spawned.addScoreboardTag(TAG);
            spawned.customName(Text.of("&6スパメダル両替所"));
            spawned.setCustomNameVisible(true);
            spawned.setAI(false);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setCollidable(false);
            spawned.setProfession(Villager.Profession.CLERIC);
            // 経験値を持たせ、職業ブロックがなくても職業を失わないようにする。
            spawned.setVillagerExperience(1);
        });
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", villager.getUniqueId().toString());
        yaml.set("world", location.getWorld().getName());
        yaml.set("x", location.getX());
        yaml.set("y", location.getY());
        yaml.set("z", location.getZ());
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("npc.yml を保存できませんでした: " + exception.getMessage());
        }
        return villager;
    }

    /** 記録している村人を取り除く。いなければ false。 */
    boolean remove() {
        Entity entity = find();
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("npc.yml を削除できませんでした。");
        }
        if (entity == null) {
            return false;
        }
        entity.remove();
        return true;
    }

    /** 記録している村人を探す。チャンクが読み込まれていなければ読み込む。 */
    Entity find() {
        Location location = savedLocation();
        if (location == null) {
            return null;
        }
        location.getChunk().load();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        UUID uuid = UUID.fromString(yaml.getString("uuid"));
        Entity entity = plugin.getServer().getEntity(uuid);
        if (entity != null) {
            return entity;
        }
        // 読み込み直後はエンティティがまだ登録されていないことがあるため、タグでも探す。
        for (Entity nearby : location.getWorld().getNearbyEntities(location, 2, 2, 2)) {
            if (nearby.getScoreboardTags().contains(TAG)) {
                return nearby;
            }
        }
        return null;
    }

    Location savedLocation() {
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        World world = plugin.getServer().getWorld(yaml.getString("world", ""));
        if (world == null || yaml.getString("uuid") == null) {
            return null;
        }
        return new Location(world, yaml.getDouble("x"), yaml.getDouble("y"), yaml.getDouble("z"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!event.getRightClicked().getScoreboardTags().contains(TAG)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        plugin.gui().open(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity().getScoreboardTags().contains(TAG)) {
            event.setCancelled(true);
        }
    }

    /** 雷でウィッチになる、などの変化を止める。 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTransform(EntityTransformEvent event) {
        if (event.getEntity().getScoreboardTags().contains(TAG)) {
            event.setCancelled(true);
        }
    }
}

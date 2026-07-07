package com.aspireserver.chameleon.listeners;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.game.GameManager;
import com.aspireserver.chameleon.game.PlayerRole;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HiderItemListener implements Listener {

    private final MecchaChameleon plugin;
    private final Map<UUID, Location> freeCamOrigins = new ConcurrentHashMap<>();
    private final Map<UUID, ArmorStand> freeCamBodies = new ConcurrentHashMap<>();

    public HiderItemListener(MecchaChameleon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameActive()) return;
        if (gm.getRole(player.getUniqueId()) != PlayerRole.HIDER) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        String name = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        event.setCancelled(true);

        switch (name) {
            case "Pose Changer" -> handlePoseChange(player);
            case "Whistle" -> handleWhistle(player);
            case "Free-Cam" -> handleFreeCam(player);
            case "Lock Position" -> gm.toggleFreeze(player);
            case "Skin Selection" -> gm.openSkinMenu(player);
        }
    }

    private void handlePoseChange(Player player) {
        double scale = plugin.getConfigManager().getScaleFactor();
        // Cycle: standing -> sitting -> crawling -> standing
        if (player.hasMetadata("chameleon_pose")) {
            int pose = player.getMetadata("chameleon_pose").get(0).asInt();
            if (pose == 0) {
                // Sit: mount on invisible armor stand (offset scaled for small players)
                double seatOffset = 0.1 * scale;
                ArmorStand seat = player.getWorld().spawn(player.getLocation().subtract(0, seatOffset, 0), ArmorStand.class, as -> {
                    as.setVisible(false);
                    as.setGravity(false);
                    as.setInvulnerable(true);
                    as.setSmall(true);
                    as.setMarker(true);
                    as.setMetadata("chameleon_seat", new FixedMetadataValue(plugin, true));
                });
                seat.addPassenger(player);
                player.setMetadata("chameleon_pose", new FixedMetadataValue(plugin, 1));
                player.sendActionBar(net.kyori.adventure.text.Component.text("Pose: Sitting"));
            } else if (pose == 1) {
                // Crawl: dismount and force swimming animation
                if (player.getVehicle() instanceof ArmorStand as) {
                    as.remove();
                }
                player.setSwimming(true);
                // Keep re-applying swimming since vanilla resets it when not in water
                player.setMetadata("chameleon_pose", new FixedMetadataValue(plugin, 2));
                startCrawlTask(player);
                player.sendActionBar(net.kyori.adventure.text.Component.text("Pose: Crawling"));
            } else {
                // Stand
                player.setSwimming(false);
                if (player.getVehicle() instanceof ArmorStand as) {
                    as.remove();
                }
                player.setMetadata("chameleon_pose", new FixedMetadataValue(plugin, 0));
                player.sendActionBar(net.kyori.adventure.text.Component.text("Pose: Standing"));
            }
        } else {
            // First use — sit
            double seatOffset = 0.1 * scale;
            ArmorStand seat = player.getWorld().spawn(player.getLocation().subtract(0, seatOffset, 0), ArmorStand.class, as -> {
                as.setVisible(false);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setSmall(true);
                as.setMarker(true);
                as.setMetadata("chameleon_seat", new FixedMetadataValue(plugin, true));
            });
            seat.addPassenger(player);
            player.setMetadata("chameleon_pose", new FixedMetadataValue(plugin, 1));
            player.sendActionBar(net.kyori.adventure.text.Component.text("Pose: Sitting"));
        }
    }

    private void startCrawlTask(Player player) {
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || !plugin.getGameManager().isParticipant(player.getUniqueId())) {
                task.cancel();
                return;
            }
            if (!player.hasMetadata("chameleon_pose")) { task.cancel(); return; }
            int pose = player.getMetadata("chameleon_pose").get(0).asInt();
            if (pose != 2) { task.cancel(); return; }
            player.setSwimming(true);
        }, 5L, 5L);
    }

    private void handleWhistle(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 2.0f, 1.8f);
        player.sendActionBar(net.kyori.adventure.text.Component.text("*whistle*"));
    }

    private void handleFreeCam(Player player) {
        UUID uuid = player.getUniqueId();
        if (freeCamOrigins.containsKey(uuid)) {
            // Return from free-cam
            Location origin = freeCamOrigins.remove(uuid);
            ArmorStand body = freeCamBodies.remove(uuid);
            if (body != null) body.remove();
            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(origin);
            plugin.getGameManager().shrinkPlayer(player);
            player.sendActionBar(net.kyori.adventure.text.Component.text("Free-cam OFF"));
        } else {
            // Enter free-cam: spawn a body stand at current position
            Location origin = player.getLocation().clone();
            freeCamOrigins.put(uuid, origin);

            ArmorStand body = player.getWorld().spawn(origin, ArmorStand.class, as -> {
                as.setVisible(true);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setSmall(true);
                as.setCustomNameVisible(true);
                as.customName(net.kyori.adventure.text.Component.text(player.getName(),
                    net.kyori.adventure.text.format.NamedTextColor.GREEN));
                as.setMetadata("chameleon_freecam", new FixedMetadataValue(plugin, true));
                // Copy player's current armor onto the stand
                as.getEquipment().setHelmet(player.getInventory().getHelmet());
                as.getEquipment().setChestplate(player.getInventory().getChestplate());
                as.getEquipment().setLeggings(player.getInventory().getLeggings());
                as.getEquipment().setBoots(player.getInventory().getBoots());
            });
            freeCamBodies.put(uuid, body);

            player.setGameMode(GameMode.SPECTATOR);
            player.sendActionBar(net.kyori.adventure.text.Component.text("Free-cam ON — use Skin Selection item to exit"));
        }
    }

    public void cleanupFreeCam(Player player) {
        UUID uuid = player.getUniqueId();
        Location origin = freeCamOrigins.remove(uuid);
        ArmorStand body = freeCamBodies.remove(uuid);
        if (body != null) body.remove();
        if (origin != null) {
            player.teleport(origin);
        }
    }
}

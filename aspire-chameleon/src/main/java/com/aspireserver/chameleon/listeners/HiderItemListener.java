package com.aspireserver.chameleon.listeners;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.game.GameManager;
import com.aspireserver.chameleon.game.PlayerRole;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

public class HiderItemListener implements Listener {

    private final MecchaChameleon plugin;

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
        // Cycle: standing -> sitting -> crawling -> standing
        if (player.hasMetadata("chameleon_pose")) {
            int pose = player.getMetadata("chameleon_pose").get(0).asInt();
            if (pose == 0) {
                // Sit: mount on invisible armor stand
                ArmorStand seat = player.getWorld().spawn(player.getLocation().subtract(0, 0.3, 0), ArmorStand.class, as -> {
                    as.setVisible(false);
                    as.setGravity(false);
                    as.setInvulnerable(true);
                    as.setSmall(true);
                    as.setMetadata("chameleon_seat", new FixedMetadataValue(plugin, true));
                });
                seat.addPassenger(player);
                player.setMetadata("chameleon_pose", new FixedMetadataValue(plugin, 1));
                player.sendActionBar(net.kyori.adventure.text.Component.text("Pose: Sitting"));
            } else if (pose == 1) {
                // Crawl: dismount and set swimming
                if (player.getVehicle() instanceof ArmorStand as) {
                    as.remove();
                }
                player.setSwimming(true);
                player.setMetadata("chameleon_pose", new FixedMetadataValue(plugin, 2));
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
            ArmorStand seat = player.getWorld().spawn(player.getLocation().subtract(0, 0.3, 0), ArmorStand.class, as -> {
                as.setVisible(false);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setSmall(true);
                as.setMetadata("chameleon_seat", new FixedMetadataValue(plugin, true));
            });
            seat.addPassenger(player);
            player.setMetadata("chameleon_pose", new FixedMetadataValue(plugin, 1));
            player.sendActionBar(net.kyori.adventure.text.Component.text("Pose: Sitting"));
        }
    }

    private void handleWhistle(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 2.0f, 1.8f);
        player.sendActionBar(net.kyori.adventure.text.Component.text("*whistle*"));
    }

    private void handleFreeCam(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            // Return to adventure
            player.setGameMode(GameMode.ADVENTURE);
            player.sendActionBar(net.kyori.adventure.text.Component.text("Free-cam OFF"));
        } else {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendActionBar(net.kyori.adventure.text.Component.text("Free-cam ON — right-click again to exit"));
        }
    }
}

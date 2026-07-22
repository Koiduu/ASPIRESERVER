package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import com.aspireserver.smp.claim.ClaimManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GoldenShovelListener implements Listener {

    private final ClaimManager claimManager;
    private final AspireSMP plugin;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public GoldenShovelListener(ClaimManager claimManager, AspireSMP plugin) {
        this.claimManager = claimManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.GOLDEN_SHOVEL) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Location loc = block.getLocation();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            pos1.put(player.getUniqueId(), loc);
            player.sendMessage(Component.text("Claim corner 1 set at (" + loc.getBlockX() + ", " + loc.getBlockZ() + ")", NamedTextColor.GOLD));
            showParticles(player, loc);
            checkAndPrompt(player);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            pos2.put(player.getUniqueId(), loc);
            player.sendMessage(Component.text("Claim corner 2 set at (" + loc.getBlockX() + ", " + loc.getBlockZ() + ")", NamedTextColor.GOLD));
            showParticles(player, loc);
            checkAndPrompt(player);
        }
    }

    private void checkAndPrompt(Player player) {
        UUID uuid = player.getUniqueId();
        if (pos1.containsKey(uuid) && pos2.containsKey(uuid)) {
            Location p1 = pos1.get(uuid);
            Location p2 = pos2.get(uuid);

            if (!p1.getWorld().equals(p2.getWorld())) {
                player.sendMessage(Component.text("Both corners must be in the same world!", NamedTextColor.RED));
                return;
            }

            int minX = Math.min(p1.getBlockX(), p2.getBlockX());
            int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
            int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
            int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());
            int area = (maxX - minX + 1) * (maxZ - minZ + 1);
            int used = claimManager.getUsedBlocks(uuid);
            int limit = claimManager.getClaimLimit(uuid);
            int remaining = limit - used;

            player.sendMessage(Component.text("Area: " + area + " blocks | Available: " + remaining + "/" + limit, NamedTextColor.AQUA));

            if (area > remaining) {
                player.sendMessage(Component.text("Not enough claim blocks! Need " + area + ", have " + remaining, NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text("Right-click with golden shovel again to confirm, or use /claim confirm", NamedTextColor.GREEN));

                boolean success = claimManager.createClaim(uuid, p1.getWorld().getName(), minX, minZ, maxX, maxZ);
                if (success) {
                    player.sendMessage(Component.text("Land claimed! (" + area + " blocks)", NamedTextColor.GREEN));
                    showBorderParticles(player, p1.getWorld().getName(), minX, minZ, maxX, maxZ);
                } else {
                    player.sendMessage(Component.text("Could not claim! Area overlaps or exceeds limit.", NamedTextColor.RED));
                }
                pos1.remove(uuid);
                pos2.remove(uuid);
            }
        }
    }

    private void showParticles(Player player, Location loc) {
        player.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 1.2, 0.5), 10, 0.3, 0.3, 0.3, 0);
    }

    private void showBorderParticles(Player player, String worldName, int minX, int minZ, int maxX, int maxZ) {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) return;

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 60) {
                    cancel();
                    return;
                }
                int y = player.getLocation().getBlockY();
                for (int x = minX; x <= maxX; x++) {
                    player.spawnParticle(Particle.HAPPY_VILLAGER, x + 0.5, y + 0.5, minZ + 0.5, 1, 0, 0, 0, 0);
                    player.spawnParticle(Particle.HAPPY_VILLAGER, x + 0.5, y + 0.5, maxZ + 0.5, 1, 0, 0, 0, 0);
                }
                for (int z = minZ; z <= maxZ; z++) {
                    player.spawnParticle(Particle.HAPPY_VILLAGER, minX + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0);
                    player.spawnParticle(Particle.HAPPY_VILLAGER, maxX + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0);
                }
                ticks += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
}

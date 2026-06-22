package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import com.aspireserver.smp.claim.Claim;
import com.aspireserver.smp.claim.ClaimManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VisualizerListener implements Listener {

    private final ClaimManager claimManager;
    private final AspireSMP plugin;
    private final Map<UUID, Long> lastVisualize;

    public VisualizerListener(ClaimManager claimManager, AspireSMP plugin) {
        this.claimManager = claimManager;
        this.plugin = plugin;
        this.lastVisualize = new HashMap<>();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != Material.GOLDEN_SHOVEL) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - lastVisualize.getOrDefault(uuid, 0L) < 1000) return;
        lastVisualize.put(uuid, now);

        for (Claim claim : claimManager.getAllClaims()) {
            if (!claim.getWorldName().equals(player.getWorld().getName())) continue;

            Location playerLoc = player.getLocation();
            int px = playerLoc.getBlockX();
            int pz = playerLoc.getBlockZ();

            if (Math.abs(px - claim.getMinX()) > 50 || Math.abs(pz - claim.getMinZ()) > 50) continue;

            boolean isOwner = claim.getOwner().equals(uuid);
            showClaimBorder(player, claim, isOwner);
        }
    }

    private void showClaimBorder(Player player, Claim claim, boolean isOwner) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Particle.DustOptions dust = new Particle.DustOptions(
                    isOwner ? Color.fromRGB(0, 255, 0) : Color.fromRGB(255, 0, 0), 1.0f);

                int y = player.getLocation().getBlockY();

                for (int x = claim.getMinX(); x <= claim.getMaxX(); x += 2) {
                    spawnParticle(player, x, y, claim.getMinZ(), dust);
                    spawnParticle(player, x, y, claim.getMaxZ(), dust);
                }
                for (int z = claim.getMinZ(); z <= claim.getMaxZ(); z += 2) {
                    spawnParticle(player, claim.getMinX(), y, z, dust);
                    spawnParticle(player, claim.getMaxX(), y, z, dust);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void spawnParticle(Player player, int x, int y, int z, Particle.DustOptions dust) {
        Location loc = new Location(player.getWorld(), x + 0.5, y + 0.5, z + 0.5);
        player.spawnParticle(Particle.DUST, loc, 1, dust);
    }
}

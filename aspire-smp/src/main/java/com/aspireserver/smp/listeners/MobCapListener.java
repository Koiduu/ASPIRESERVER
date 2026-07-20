package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Armadillo;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zoglin;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Map;
import java.util.UUID;

public class MobCapListener implements Listener {

    private final AspireSMP plugin;
    private static final int CHUNK_RADIUS = 3;
    private final Map<UUID, Integer> cachedCounts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> cacheTimestamps = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 2000;

    public MobCapListener(AspireSMP plugin) {
        this.plugin = plugin;
    }

    private boolean isSmpWorld(World world) {
        String smpWorld = plugin.getConfig().getString("smp-world", "");
        return !smpWorld.isEmpty() && world.getName().equalsIgnoreCase(smpWorld);
    }

    private int mobCapPerPlayer() {
        return plugin.getConfig().getInt("mob-cap.per-player", 40);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (!isSmpWorld(entity.getWorld())) return;

        // Keep undead away from armadillos so they don't roll up (scute farms).
        if (isUndead(entity) && isSpawnNearArmadillo(entity)) {
            switch (event.getSpawnReason()) {
                case SPAWNER_EGG, COMMAND, CUSTOM -> { } // let admins force these
                default -> {
                    if (plugin.getConfig().getBoolean("armadillo-protection.enabled", false)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        if (!plugin.getConfig().getBoolean("mob-cap.enabled", true)) return;

        switch (event.getSpawnReason()) {
            // Never cap plugin/player-driven or event-driven spawns.
            case CUSTOM, SPAWNER_EGG, COMMAND, BREEDING, TRIAL_SPAWNER, SPAWNER, INFECTION, CURED,
                 // Raid / village / patrol spawns must be allowed or raids break entirely.
                 RAID, PATROL, VILLAGE_DEFENSE, VILLAGE_INVASION, REINFORCEMENTS, TRAP -> {
                return;
            }
            default -> { }
        }

        // Only cap hostile monsters. Animals are never capped so animal/passive farms work.
        if (!(entity instanceof Monster)) return;
        // Never cap named or persistent mobs.
        if (entity.isPersistent() || entity.getCustomName() != null) return;

        Player nearest = getNearestPlayer(entity);
        if (nearest == null) return;

        int mobCount = getCachedMobCount(nearest);
        if (mobCount >= mobCapPerPlayer()) {
            event.setCancelled(true);
        }
    }

    private boolean isUndead(Entity entity) {
        return entity instanceof Zombie || entity instanceof AbstractSkeleton
                || entity instanceof Phantom || entity instanceof Zoglin;
    }

    private boolean isSpawnNearArmadillo(Entity entity) {
        double radius = plugin.getConfig().getDouble("armadillo-protection.radius", 12.0);
        for (Entity nearby : entity.getWorld().getNearbyEntities(entity.getLocation(), radius, radius, radius)) {
            if (nearby instanceof Armadillo) return true;
        }
        return false;
    }

    private int getCachedMobCount(Player player) {
        UUID uuid = player.getUniqueId();
        Long ts = cacheTimestamps.get(uuid);
        if (ts != null && System.currentTimeMillis() - ts < CACHE_DURATION_MS) {
            return cachedCounts.getOrDefault(uuid, 0);
        }
        int count = countMobsAroundPlayer(player);
        cachedCounts.put(uuid, count);
        cacheTimestamps.put(uuid, System.currentTimeMillis());
        return count;
    }

    private Player getNearestPlayer(Entity entity) {
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Player player : entity.getWorld().getPlayers()) {
            double dist = player.getLocation().distanceSquared(entity.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = player;
            }
        }
        return nearest;
    }

    private int countMobsAroundPlayer(Player player) {
        int count = 0;
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        World world = player.getWorld();

        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                if (!world.isChunkLoaded(chunkX + dx, chunkZ + dz)) continue;
                Chunk chunk = world.getChunkAt(chunkX + dx, chunkZ + dz);
                for (Entity entity : chunk.getEntities()) {
                    // Only hostile monsters count toward the cap; animals are exempt.
                    if (entity instanceof Monster) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}

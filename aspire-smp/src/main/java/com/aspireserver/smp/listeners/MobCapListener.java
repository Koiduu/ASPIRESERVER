package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Map;
import java.util.UUID;

public class MobCapListener implements Listener {

    private final AspireSMP plugin;
    private static final int MOB_CAP_PER_PLAYER = 5;
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.BREEDING
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof Monster) && !(entity instanceof Animals)) return;
        if (!isSmpWorld(entity.getWorld())) return;

        Chunk spawnChunk = entity.getLocation().getChunk();
        Player nearest = getNearestPlayer(entity);
        if (nearest == null) return;

        int mobCount = getCachedMobCount(nearest);
        if (mobCount >= MOB_CAP_PER_PLAYER) {
            event.setCancelled(true);
        }
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
                    if (entity instanceof Monster || entity instanceof Animals) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}

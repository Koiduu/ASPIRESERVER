package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Item;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class AntiLagListener implements Listener {

    private final AspireSMP plugin;
    private static final int ITEM_MERGE_RADIUS = 3;
    private static final int DESPAWN_TICKS = 3 * 60 * 20; // 3 minutes
    private static final int MAX_ENDERPEARLS_PER_CHUNK = 20;
    private static final int MAX_ITEMS_PER_CHUNK = 100;
    private static final int CLEANUP_INTERVAL = 60 * 20; // 1 minute

    public AntiLagListener(AspireSMP plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    private boolean isSmpWorld(World world) {
        String smpWorld = plugin.getConfig().getString("smp-world", "");
        return !smpWorld.isEmpty() && world.getName().equalsIgnoreCase(smpWorld);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (!isSmpWorld(item.getWorld())) return;

        // Set 3-minute despawn
        item.setTicksLived(1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (item.isValid() && !item.isDead()) {
                item.remove();
            }
        }, DESPAWN_TICKS);

        // Merge nearby identical items within 3 blocks
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!item.isValid() || item.isDead()) return;
            mergeNearbyItems(item);
        }, 5L);

        // Chunk item cap
        Chunk chunk = item.getLocation().getChunk();
        int itemCount = 0;
        for (Entity e : chunk.getEntities()) {
            if (e instanceof Item) itemCount++;
        }
        if (itemCount > MAX_ITEMS_PER_CHUNK) {
            event.setCancelled(true);
        }
    }

    private void mergeNearbyItems(Item item) {
        ItemStack stack = item.getItemStack();
        for (Entity nearby : item.getNearbyEntities(ITEM_MERGE_RADIUS, ITEM_MERGE_RADIUS, ITEM_MERGE_RADIUS)) {
            if (!(nearby instanceof Item other)) continue;
            if (other.equals(item) || other.isDead()) continue;

            ItemStack otherStack = other.getItemStack();
            if (!stack.isSimilar(otherStack)) continue;

            int total = stack.getAmount() + otherStack.getAmount();
            int max = stack.getMaxStackSize();
            if (total <= max) {
                stack.setAmount(total);
                item.setItemStack(stack);
                other.remove();
            } else {
                stack.setAmount(max);
                item.setItemStack(stack);
                otherStack.setAmount(total - max);
                other.setItemStack(otherStack);
            }
            break;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!isSmpWorld(event.getEntity().getWorld())) return;

        if (event.getEntity() instanceof Snowball) {
            // Despawn snowballs after 3 minutes
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (event.getEntity().isValid() && !event.getEntity().isDead()) {
                    event.getEntity().remove();
                }
            }, DESPAWN_TICKS);
        }

        if (event.getEntity() instanceof EnderPearl) {
            // Cap enderpearls per chunk
            Chunk chunk = event.getEntity().getLocation().getChunk();
            int pearlCount = 0;
            for (Entity e : chunk.getEntities()) {
                if (e instanceof EnderPearl) pearlCount++;
            }
            if (pearlCount >= MAX_ENDERPEARLS_PER_CHUNK) {
                event.setCancelled(true);
            }
        }
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    if (!isSmpWorld(world)) continue;
                    for (Entity entity : world.getEntities()) {
                        // Remove old items that somehow survived
                        if (entity instanceof Item item) {
                            if (item.getTicksLived() > DESPAWN_TICKS) {
                                item.remove();
                            }
                        }
                        // Remove lingering snowball entities
                        if (entity instanceof Snowball snowball) {
                            if (snowball.getTicksLived() > DESPAWN_TICKS) {
                                snowball.remove();
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }
}

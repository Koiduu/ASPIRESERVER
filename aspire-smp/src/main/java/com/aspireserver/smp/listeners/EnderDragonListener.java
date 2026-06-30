package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class EnderDragonListener implements Listener {

    private final AspireSMP plugin;
    private static final int TOTAL_DRAGONS = 3;
    private final AtomicInteger dragonsAlive = new AtomicInteger(0);
    private final AtomicInteger dragonsKilled = new AtomicInteger(0);
    private boolean fightActive = false;

    public EnderDragonListener(AspireSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonSpawn(EntitySpawnEvent event) {
        if (event.getEntityType() != EntityType.ENDER_DRAGON) return;
        if (event.getEntity().getWorld().getEnvironment() != World.Environment.THE_END) return;

        if (!fightActive) {
            fightActive = true;
            dragonsKilled.set(0);
            dragonsAlive.set(1);

            // Spawn 2 additional dragons after a short delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                World endWorld = event.getEntity().getWorld();
                Location spawnLoc = new Location(endWorld, 0, 70, 0);

                for (int i = 0; i < TOTAL_DRAGONS - 1; i++) {
                    double angle = (2 * Math.PI / (TOTAL_DRAGONS - 1)) * i;
                    double offsetX = Math.cos(angle) * 30;
                    double offsetZ = Math.sin(angle) * 30;
                    Location dragonLoc = spawnLoc.clone().add(offsetX, 10 + (i * 5), offsetZ);

                    endWorld.spawnEntity(dragonLoc, EntityType.ENDER_DRAGON);
                    dragonsAlive.incrementAndGet();
                }

                Bukkit.broadcast(Component.text("3 Ender Dragons have awakened! Defeat them all!", NamedTextColor.DARK_PURPLE)
                    .decorate(TextDecoration.BOLD));
            }, 60L); // 3 second delay
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) return;
        if (event.getEntity().getWorld().getEnvironment() != World.Environment.THE_END) return;

        int killed = dragonsKilled.incrementAndGet();
        int alive = dragonsAlive.decrementAndGet();

        if (alive > 0) {
            Bukkit.broadcast(Component.text("Ender Dragon defeated! " + alive + " remaining!", NamedTextColor.LIGHT_PURPLE));

            // Don't drop XP or activate portal until all dragons are dead
            event.setDroppedExp(0);
        } else {
            // All dragons killed — full reward
            fightActive = false;
            event.setDroppedExp(12000 * TOTAL_DRAGONS);
            Bukkit.broadcast(Component.text("All 3 Ender Dragons have been slain!", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        }
    }
}

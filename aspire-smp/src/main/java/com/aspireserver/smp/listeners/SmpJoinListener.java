package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SmpJoinListener implements Listener {

    private final AspireSMP plugin;
    private final Map<UUID, Long> damageImmunity = new ConcurrentHashMap<>();
    private static final int IMMUNITY_SECONDS = 7;
    private static final int SLOW_CHUNK_VIEW = 4;
    private static final int NORMAL_CHUNK_VIEW = 10;

    public SmpJoinListener(AspireSMP plugin) {
        this.plugin = plugin;
    }

    private boolean isSmpWorld(World world) {
        String smpWorld = plugin.getConfig().getString("smp-world", "");
        return !smpWorld.isEmpty() && world.getName().equalsIgnoreCase(smpWorld);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isSmpWorld(player.getWorld())) {
            applyImmunity(player);
            applySlowChunks(player);
        } else if (isSmpWorld(event.getFrom())) {
            resetChunkView(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isSmpWorld(player.getWorld())) {
                applyImmunity(player);
                applySlowChunks(player);
            }
        }, 5L);
    }

    private void applyImmunity(Player player) {
        damageImmunity.put(player.getUniqueId(), System.currentTimeMillis() + (IMMUNITY_SECONDS * 1000L));
        player.sendMessage(Component.text("You have " + IMMUNITY_SECONDS + "s damage immunity.", NamedTextColor.GREEN));

        new BukkitRunnable() {
            @Override
            public void run() {
                damageImmunity.remove(player.getUniqueId());
                if (player.isOnline()) {
                    player.sendMessage(Component.text("Damage immunity expired.", NamedTextColor.YELLOW));
                }
            }
        }.runTaskLater(plugin, IMMUNITY_SECONDS * 20L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isSmpWorld(player.getWorld())) return;

        Long expiry = damageImmunity.get(player.getUniqueId());
        if (expiry != null && System.currentTimeMillis() < expiry) {
            event.setCancelled(true);
        }
    }

    private void applySlowChunks(Player player) {
        player.setViewDistance(SLOW_CHUNK_VIEW);
    }

    private void resetChunkView(Player player) {
        player.setViewDistance(NORMAL_CHUNK_VIEW);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isSmpWorld(player.getWorld())) return;

        if (player.isGliding()) {
            if (player.getViewDistance() != NORMAL_CHUNK_VIEW) {
                player.setViewDistance(NORMAL_CHUNK_VIEW);
            }
        } else {
            if (player.getViewDistance() != SLOW_CHUNK_VIEW) {
                player.setViewDistance(SLOW_CHUNK_VIEW);
            }
        }
    }
}

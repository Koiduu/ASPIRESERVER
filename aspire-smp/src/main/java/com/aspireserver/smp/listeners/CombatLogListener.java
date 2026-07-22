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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatLogListener implements Listener {

    private final AspireSMP plugin;
    private final Map<UUID, Long> combatTagged = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> combatTimers = new ConcurrentHashMap<>();
    private static final int COMBAT_DURATION_SECONDS = 10;

    public CombatLogListener(AspireSMP plugin) {
        this.plugin = plugin;
    }

    private boolean isSmpWorld(World world) {
        String smpWorld = plugin.getConfig().getString("smp-world", "");
        return !smpWorld.isEmpty() && world.getName().equalsIgnoreCase(smpWorld);
    }

    public boolean isInCombat(UUID playerId) {
        Long expiry = combatTagged.get(playerId);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            combatTagged.remove(playerId);
            return false;
        }
        return true;
    }

    private void tagPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        long expiry = System.currentTimeMillis() + (COMBAT_DURATION_SECONDS * 1000L);
        boolean wasTagged = isInCombat(uuid);
        combatTagged.put(uuid, expiry);

        BukkitTask existing = combatTimers.remove(uuid);
        if (existing != null) existing.cancel();

        if (!wasTagged) {
            player.sendMessage(Component.text("You are now in combat! Do not log out for " + COMBAT_DURATION_SECONDS + "s.", NamedTextColor.RED));
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                combatTagged.remove(uuid);
                combatTimers.remove(uuid);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.sendMessage(Component.text("You are no longer in combat.", NamedTextColor.GREEN));
                }
            }
        }.runTaskLater(plugin, COMBAT_DURATION_SECONDS * 20L);
        combatTimers.put(uuid, task);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!isSmpWorld(victim.getWorld())) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null || attacker.equals(victim)) return;

        tagPlayer(attacker);
        tagPlayer(victim);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!isSmpWorld(player.getWorld())) return;

        if (isInCombat(player.getUniqueId())) {
            player.setHealth(0);
            Bukkit.broadcast(Component.text(player.getName() + " combat logged and died!", NamedTextColor.RED));
        }
        cleanup(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!isSmpWorld(player.getWorld())) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_GATEWAY) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) return;

        if (isInCombat(player.getUniqueId())) {
            if (event.getTo() != null && !isSmpWorld(event.getTo().getWorld())) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You cannot leave SMP while in combat! " + getRemainingTime(player.getUniqueId()) + "s remaining.", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isSmpWorld(player.getWorld())) return;
        if (!isInCombat(player.getUniqueId())) return;

        String cmd = event.getMessage().toLowerCase();
        if (cmd.startsWith("/lobby") || cmd.startsWith("/l ") || cmd.equals("/l")
                || cmd.startsWith("/spawn") || cmd.startsWith("/mvtp")
                || cmd.startsWith("/tp ") || cmd.startsWith("/home")
                || cmd.startsWith("/warp")) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You cannot use that command while in combat! " + getRemainingTime(player.getUniqueId()) + "s remaining.", NamedTextColor.RED));
        }
    }

    private int getRemainingTime(UUID uuid) {
        Long expiry = combatTagged.get(uuid);
        if (expiry == null) return 0;
        return Math.max(0, (int) ((expiry - System.currentTimeMillis()) / 1000));
    }

    private void cleanup(UUID uuid) {
        combatTagged.remove(uuid);
        BukkitTask task = combatTimers.remove(uuid);
        if (task != null) task.cancel();
    }
}

package com.aspireserver.bedwars.listeners;

import com.aspireserver.bedwars.AspireBedwars;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatListener implements Listener {

    private final AspireBedwars plugin;
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();

    public CombatListener(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!plugin.getGameManager().isInBedwarsWorld(victim)) return;
        if (!plugin.getGameManager().isRunning()) { event.setCancelled(true); return; }

        Player attacker = resolveAttacker(event);

        if (plugin.getSpectatorManager().isSpectator(victim.getUniqueId())) { event.setCancelled(true); return; }
        if (plugin.getGameManager().isInvulnerable(victim.getUniqueId()) && attacker != null) { event.setCancelled(true); return; }

        if (attacker != null && !attacker.equals(victim)) {
            if (plugin.getSpectatorManager().isSpectator(attacker.getUniqueId())) { event.setCancelled(true); return; }
            // No friendly fire
            if (plugin.getTeamManager().sameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            lastDamager.put(victim.getUniqueId(), attacker.getUniqueId());
            lastDamageTime.put(victim.getUniqueId(), System.currentTimeMillis());
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!plugin.getGameManager().isInBedwarsWorld(victim)) return;
        if (!plugin.getGameManager().isRunning()) return;
        if (plugin.getSpectatorManager().isSpectator(victim.getUniqueId())) { event.setCancelled(true); return; }

        boolean lethal = event.getCause() == EntityDamageEvent.DamageCause.VOID
                || event.getFinalDamage() >= victim.getHealth();
        if (!lethal) return;

        // Death already being processed (e.g. repeated void ticks) — swallow duplicates.
        if (plugin.getGameManager().isPendingDeath(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        victim.setHealth(victim.getAttribute(Attribute.MAX_HEALTH).getValue());
        victim.setFireTicks(0);

        Player killer = null;
        Long t = lastDamageTime.get(victim.getUniqueId());
        if (t != null && System.currentTimeMillis() - t <= 10_000) {
            UUID kid = lastDamager.get(victim.getUniqueId());
            if (kid != null) killer = plugin.getServer().getPlayer(kid);
        }
        lastDamager.remove(victim.getUniqueId());
        lastDamageTime.remove(victim.getUniqueId());

        // Drop the victim's collectible resources at death location
        dropResources(victim);
        plugin.getGameManager().handleDeath(victim, killer);
    }

    private void dropResources(Player victim) {
        for (var is : victim.getInventory().getContents()) {
            if (is == null) continue;
            switch (is.getType()) {
                case IRON_INGOT, GOLD_INGOT, DIAMOND, EMERALD ->
                        victim.getWorld().dropItemNaturally(victim.getLocation(), is.clone());
                default -> {}
            }
        }
    }
}

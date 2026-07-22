package com.aspireserver.chameleon.listeners;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.game.GameManager;
import com.aspireserver.chameleon.game.PlayerRole;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GameListener implements Listener {

    private final MecchaChameleon plugin;

    public GameListener(MecchaChameleon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        GameManager gm = plugin.getGameManager();
        if (!gm.isGameActive()) return;
        if (!gm.isParticipant(victim.getUniqueId())) return;

        PlayerRole victimRole = gm.getRole(victim.getUniqueId());
        PlayerRole attackerRole = gm.getRole(attacker.getUniqueId());

        // Only seekers can damage hiders
        if (attackerRole == PlayerRole.SEEKER && victimRole == PlayerRole.HIDER) {
            event.setCancelled(true);
            gm.onHiderCaught(victim, attacker);
        } else {
            event.setCancelled(true); // No other PvP allowed
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        GameManager gm = plugin.getGameManager();
        if (!gm.isParticipant(player.getUniqueId())) return;

        // Cancel all non-player damage during game
        if (event instanceof EntityDamageByEntityEvent) return; // handled above
        event.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameActive()) return;

        Player player = event.getPlayer();
        if (!gm.isParticipant(player.getUniqueId())) return;

        // Freeze check
        if (gm.isFrozen(player.getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() ||
                    event.getFrom().getZ() != event.getTo().getZ() ||
                    event.getFrom().getY() != event.getTo().getY()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGameManager().handleDisconnect(event.getPlayer());
    }
}

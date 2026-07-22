package com.aspireserver.chameleon.listeners;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.game.GameManager;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

public class DoubleJumpListener implements Listener {

    private final MecchaChameleon plugin;

    public DoubleJumpListener(MecchaChameleon plugin) {
        this.plugin = plugin;
    }

    // Re-enable the "flight" ability each time the player lands so they can
    // press space again in mid-air to trigger a double jump.
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameActive()) return;
        if (!gm.isParticipant(player.getUniqueId())) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        if (player.isOnGround() && !player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameActive()) return;
        if (!gm.isParticipant(player.getUniqueId())) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (gm.isFrozen(player.getUniqueId())) return;

        // Consume the flight toggle and turn it into a double jump boost
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);

        Vector dir = player.getLocation().getDirection().normalize();
        Vector boost = new Vector(dir.getX() * 0.6, 0.7, dir.getZ() * 0.6);
        player.setVelocity(boost);
        player.setFallDistance(0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.2f);
    }
}

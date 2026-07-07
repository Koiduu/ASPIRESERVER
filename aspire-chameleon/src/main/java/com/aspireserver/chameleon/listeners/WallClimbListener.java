package com.aspireserver.chameleon.listeners;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.game.GameManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

public class WallClimbListener implements Listener {

    private final MecchaChameleon plugin;

    public WallClimbListener(MecchaChameleon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameActive()) return;
        if (!gm.isParticipant(player.getUniqueId())) return;
        if (gm.isFrozen(player.getUniqueId())) return;

        if (!isMovingForward(event)) return;

        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().clone();
        direction.setY(0).normalize();

        Location ahead = player.getLocation().add(direction.clone().multiply(0.4));
        Block blockAhead = ahead.getBlock();

        if (blockAhead.getType().isSolid()) {
            // Check if there's open space above (so we can climb into it)
            Block abovePlayer = player.getLocation().add(0, player.getHeight(), 0).getBlock();

            Vector vel = player.getVelocity();

            if (player.isSneaking()) {
                // Sneak = hold position on wall
                vel.setY(0.0);
            } else if (!abovePlayer.getType().isSolid()) {
                // Auto-climb: steady upward velocity (like a fast ladder)
                vel.setY(0.25);
            } else {
                // Ceiling above, hold position
                vel.setY(0.0);
            }

            player.setVelocity(vel);
            player.setFallDistance(0);
        }
    }

    private boolean isMovingForward(PlayerMoveEvent event) {
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        return dx * dx + dz * dz > 0.0001;
    }
}

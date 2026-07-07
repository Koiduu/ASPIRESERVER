package com.aspireserver.chameleon.listeners;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.game.GameManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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

        // Check if player is pressing forward against a wall
        if (!player.isSprinting() && !isMovingForward(event)) return;

        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();
        direction.setY(0).normalize();

        // Check block in front at feet level
        Location ahead = player.getLocation().add(direction.multiply(0.4));
        Block blockAhead = ahead.getBlock();

        if (blockAhead.getType().isSolid()) {
            // Check if there's air above
            Block above = blockAhead.getRelative(BlockFace.UP);
            if (!above.getType().isSolid()) {
                // Apply upward velocity (climbing)
                Vector vel = player.getVelocity();
                vel.setY(0.2);
                player.setVelocity(vel);
                player.setFallDistance(0);
            }
        }
    }

    private boolean isMovingForward(PlayerMoveEvent event) {
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        return dx * dx + dz * dz > 0.001;
    }
}

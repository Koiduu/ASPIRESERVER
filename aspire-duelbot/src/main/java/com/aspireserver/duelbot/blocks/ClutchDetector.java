package com.aspireserver.duelbot.blocks;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Anti-void clutch detection (Section 4.1). Projects the bot's fall forward using the
 * standard gravity/drag recurrence to decide whether — and when — to place a save block.
 */
public final class ClutchDetector {

    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
    private static final double CLUTCH_THRESHOLD_Y = -0.3;
    private static final int MAX_SCAN_DOWN = 6;

    /** True if the bot is falling with no ground within scan range below it. */
    public boolean shouldClutch(LivingEntity bot) {
        if (bot.isOnGround()) return false;
        if (bot.getVelocity().getY() >= CLUTCH_THRESHOLD_Y) return false;
        return groundBlockBelow(bot.getLocation()) == null;
    }

    /**
     * Estimates whether the fatal-fall threshold is reached within {@code placeWithinTicks};
     * used to place exactly one tick before impact/void.
     */
    public boolean shouldPlaceNow(LivingEntity bot, int placeWithinTicks) {
        double y = bot.getLocation().getY();
        double vy = bot.getVelocity().getY();
        double minY = bot.getWorld().getMinHeight();
        double fatalY = Math.min(minY - 2, y - 4); // void or a lethal drop
        for (int t = 0; t < placeWithinTicks; t++) {
            vy = (vy - GRAVITY) * DRAG;
            y += vy;
            if (y <= fatalY) return true;
        }
        return false;
    }

    /** Block directly beneath the bot's feet to place a clutch block on. */
    public Block clutchTarget(LivingEntity bot) {
        Location feet = bot.getLocation();
        return feet.getBlock().getRelative(0, -1, 0);
    }

    private Block groundBlockBelow(Location loc) {
        Block b = loc.getBlock();
        for (int i = 0; i < MAX_SCAN_DOWN; i++) {
            Block below = b.getRelative(0, -1 - i, 0);
            Material m = below.getType();
            if (m.isSolid()) return below;
            if (below.getY() <= below.getWorld().getMinHeight()) break;
        }
        return null;
    }

    public Vector nextTickVelocity(Vector current) {
        double vy = (current.getY() - GRAVITY) * DRAG;
        return new Vector(current.getX(), vy, current.getZ());
    }
}

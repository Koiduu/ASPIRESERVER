package com.aspireserver.duelbot.combat;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Jump-gated critical hits (Section 3.3). A hit crits only while airborne,
 * descending, not on a ladder, not in water, and not sprinting.
 */
public final class CritJumpController {

    private static final double JUMP_VELOCITY = 0.42;

    /** Hop straight up (preserving horizontal momentum) to set up a crit. */
    public void jump(LivingEntity bot) {
        Vector v = bot.getVelocity();
        bot.setVelocity(new Vector(v.getX(), JUMP_VELOCITY, v.getZ()));
    }

    public boolean canJump(LivingEntity bot) {
        return bot.isOnGround() && !inWater(bot) && !onLadder(bot);
    }

    /** Whether a hit landing right now would qualify as a 1.8 critical. */
    public boolean critValid(LivingEntity bot, boolean sprinting) {
        if (bot.isOnGround()) return false;
        if (sprinting) return false;
        if (inWater(bot)) return false;
        if (onLadder(bot)) return false;
        return bot.getVelocity().getY() < 0.0; // descending
    }

    private boolean inWater(LivingEntity bot) {
        Material m = bot.getLocation().getBlock().getType();
        return m == Material.WATER || m == Material.BUBBLE_COLUMN;
    }

    private boolean onLadder(LivingEntity bot) {
        Material m = bot.getLocation().getBlock().getType();
        return m == Material.LADDER || m == Material.VINE;
    }
}

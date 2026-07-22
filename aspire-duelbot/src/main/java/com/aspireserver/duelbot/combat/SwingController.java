package com.aspireserver.duelbot.combat;

import com.aspireserver.duelbot.npc.BotProfile;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;

/**
 * Clicks-per-second swing gate (Section 3.2). Not a cooldown — a jittered CPS timer.
 */
public final class SwingController {

    private final BotProfile profile;
    private int ticksUntilNext;

    public SwingController(BotProfile profile) {
        this.profile = profile;
        this.ticksUntilNext = profile.nextSwingInterval();
    }

    public void tick() {
        if (ticksUntilNext > 0) ticksUntilNext--;
    }

    public boolean ready() {
        return ticksUntilNext <= 0;
    }

    /** Call after a swing fires to schedule the next one. */
    public void consume() {
        ticksUntilNext = profile.nextSwingInterval();
    }

    /** Reach gate using eye-to-bounding-box distance vs the 1.8 reach (~3 blocks). */
    public boolean inReach(LivingEntity bot, LivingEntity target, double reach) {
        return eyeToBoxDistance(bot.getEyeLocation(), target.getBoundingBox()) <= reach;
    }

    public static double eyeToBoxDistance(Location eye, BoundingBox box) {
        double cx = clamp(eye.getX(), box.getMinX(), box.getMaxX());
        double cy = clamp(eye.getY(), box.getMinY(), box.getMaxY());
        double cz = clamp(eye.getZ(), box.getMinZ(), box.getMaxZ());
        double dx = eye.getX() - cx;
        double dy = eye.getY() - cy;
        double dz = eye.getZ() - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

package com.aspireserver.duelbot.combat;

import com.aspireserver.duelbot.config.DifficultyTier;
import com.aspireserver.duelbot.npc.BotProfile;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Orbit-strafing (Section 3.5): blends a forward vector toward the target with a
 * perpendicular component whose sign flips on a randomised interval.
 */
public final class StrafeController {

    private final BotProfile profile;
    private int strafeSign = 1;
    private int ticksUntilFlip;

    public StrafeController(BotProfile profile) {
        this.profile = profile;
        this.ticksUntilFlip = profile.nextStrafeHold();
    }

    public void tick() {
        if (ticksUntilFlip > 0) {
            ticksUntilFlip--;
            return;
        }
        strafeSign = -strafeSign;
        ticksUntilFlip = profile.nextStrafeHold();
    }

    /** Horizontal, unit-ish desired move direction blending closing + circling. */
    public Vector desiredMove(Location bot, Location target) {
        DifficultyTier tier = profile.tier();
        double fx = target.getX() - bot.getX();
        double fz = target.getZ() - bot.getZ();
        double len = Math.sqrt(fx * fx + fz * fz);
        if (len < 1.0e-4) return new Vector(0, 0, 0);
        fx /= len;
        fz /= len;
        // R = 90° rotation of F in the XZ plane
        double rx = -fz;
        double rz = fx;
        double mx = fx * tier.forwardWeight + rx * strafeSign * tier.strafeWeight;
        double mz = fz * tier.forwardWeight + rz * strafeSign * tier.strafeWeight;
        Vector move = new Vector(mx, 0, mz);
        if (move.lengthSquared() > 1.0e-6) move.normalize();
        return move;
    }

    public int strafeSign() {
        return strafeSign;
    }
}

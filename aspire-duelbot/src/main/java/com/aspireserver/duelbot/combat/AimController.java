package com.aspireserver.duelbot.combat;

import com.aspireserver.duelbot.config.DifficultyTier;
import com.aspireserver.duelbot.npc.BotProfile;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Aim tracking with decaying human error and shortest-path angle smoothing (Section 3.1).
 */
public final class AimController {

    private final BotProfile profile;
    private float currentYaw;
    private float currentPitch;
    private boolean initialized;

    private int settleTicks;
    private double offsetYaw0;
    private double offsetPitch0;

    public AimController(BotProfile profile) {
        this.profile = profile;
        rerollOffset();
    }

    /** Re-randomise the error vector and reset its decay clock (call on target (re)acquire). */
    public void rerollOffset() {
        DifficultyTier tier = profile.tier();
        Random r = profile.random();
        double mag = tier.aimOffset0;
        double ang = r.nextDouble() * Math.PI * 2.0;
        offsetYaw0 = Math.cos(ang) * mag;
        offsetPitch0 = Math.sin(ang) * mag * 0.5; // less vertical wander than horizontal
        settleTicks = 0;
    }

    public float currentYaw() { return currentYaw; }
    public float currentPitch() { return currentPitch; }

    /** Updates the smoothed look angles toward the target (with error) and returns the point to face. */
    public Location computeLook(LivingEntity bot, LivingEntity target) {
        DifficultyTier tier = profile.tier();
        Location e = bot.getEyeLocation();
        Location t = target.getEyeLocation();

        double dx = t.getX() - e.getX();
        double dy = t.getY() - e.getY();
        double dz = t.getZ() - e.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double targetPitch = Math.toDegrees(-Math.atan2(dy, distXZ));

        double decay = Math.exp(-settleTicks / Math.max(0.001, tier.aimTau));
        targetYaw += offsetYaw0 * decay;
        targetPitch += offsetPitch0 * decay;
        // Small permanent micro-wander so a settled aim is never perfectly locked (anti-robotic).
        Random r = profile.random();
        double jitter = tier.aimOffset0 * 0.08 + 0.15;
        targetYaw += (r.nextDouble() * 2.0 - 1.0) * jitter;
        targetPitch += (r.nextDouble() * 2.0 - 1.0) * jitter * 0.5;
        settleTicks++;

        if (!initialized) {
            currentYaw = e.getYaw();
            currentPitch = e.getPitch();
            initialized = true;
        }

        double delta = ((targetYaw - currentYaw + 540.0) % 360.0) - 180.0;
        double lerp = clamp(1.0 / (1.0 + tier.aimTau), 0.15, 1.0);
        currentYaw += (float) (delta * lerp);
        currentPitch += (float) ((targetPitch - currentPitch) * lerp);
        currentPitch = (float) clamp(currentPitch, -90.0, 90.0);

        double yawRad = Math.toRadians(currentYaw);
        double pitchRad = Math.toRadians(currentPitch);
        Vector dir = new Vector(
                -Math.cos(pitchRad) * Math.sin(yawRad),
                -Math.sin(pitchRad),
                Math.cos(pitchRad) * Math.cos(yawRad));
        return e.clone().add(dir.multiply(4.0));
    }

    /** True if the smoothed aim is within {@code degrees} of the target — gate swings on this. */
    public boolean onTarget(LivingEntity bot, LivingEntity target, double degrees) {
        Location e = bot.getEyeLocation();
        Location t = target.getEyeLocation();
        double dx = t.getX() - e.getX();
        double dy = t.getY() - e.getY();
        double dz = t.getZ() - e.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double targetPitch = Math.toDegrees(-Math.atan2(dy, distXZ));
        double yawDelta = Math.abs(((targetYaw - currentYaw + 540.0) % 360.0) - 180.0);
        double pitchDelta = Math.abs(targetPitch - currentPitch);
        return yawDelta <= degrees && pitchDelta <= degrees;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

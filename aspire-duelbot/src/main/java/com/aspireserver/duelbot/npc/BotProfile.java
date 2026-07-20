package com.aspireserver.duelbot.npc;

import com.aspireserver.duelbot.config.DifficultyTier;

import java.util.Random;

/**
 * Per-instance behavioural profile resolved from a {@link DifficultyTier}.
 * Owns the RNG so all sampled values (swing interval, reaction delay, dice rolls)
 * are deterministic per bot but vary tick-to-tick.
 */
public final class BotProfile {

    private final DifficultyTier tier;
    private final Random random;

    public BotProfile(DifficultyTier tier, long seed) {
        this.tier = tier;
        this.random = new Random(seed);
    }

    public DifficultyTier tier() { return tier; }
    public Random random() { return random; }

    /** Ticks until the next swing, sampled from a gaussian around 20/CPS (Section 3.2). */
    public int nextSwingInterval() {
        double meanInterval = 20.0 / Math.max(0.1, tier.cpsMean);
        double sampled = meanInterval + random.nextGaussian() * intervalStddev();
        return Math.max(1, (int) Math.round(sampled));
    }

    /** Convert the tier's CPS stddev into a stddev on the swing interval. */
    private double intervalStddev() {
        double cpsLow = Math.max(0.5, tier.cpsMean - tier.cpsStddev);
        double cpsHigh = tier.cpsMean + tier.cpsStddev;
        double intervalHigh = 20.0 / cpsLow;
        double intervalLow = 20.0 / cpsHigh;
        return Math.max(0.05, (intervalHigh - intervalLow) / 2.0);
    }

    /** Reaction delay in ticks, uniform on the tier's [min,max]. */
    public int nextReactionDelay() {
        return uniform(tier.reactionMinTicks, tier.reactionMaxTicks);
    }

    /** Ticks a single strafe direction is held before flipping. */
    public int nextStrafeHold() {
        return uniform(tier.strafeMinTicks, tier.strafeMaxTicks);
    }

    public boolean rollCrit() { return random.nextDouble() < tier.critConsistency; }

    public boolean rollWTap() { return random.nextDouble() < tier.wtapReliability; }

    public int uniform(int min, int max) {
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }
}

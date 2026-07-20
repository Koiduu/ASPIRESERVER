package com.aspireserver.duelbot.config;

/**
 * Immutable, resolved stat block for a single difficulty tier (Section 5/6 schema).
 * All angular values are degrees; all time values are ticks (20/sec).
 */
public final class DifficultyTier {

    public final String name;
    public final double reach;
    public final double cpsMean;
    public final double cpsStddev;
    public final double aimOffset0;
    public final double aimTau;
    public final double critConsistency;
    public final double wtapReliability;
    public final int wtapTicks;
    public final int strafeMinTicks;
    public final int strafeMaxTicks;
    public final double strafeWeight;
    public final double forwardWeight;
    public final int reactionMinTicks;
    public final int reactionMaxTicks;
    public final int blockPlaceDelayTicks;

    public DifficultyTier(String name, double reach, double cpsMean, double cpsStddev,
                          double aimOffset0, double aimTau, double critConsistency,
                          double wtapReliability, int wtapTicks,
                          int strafeMinTicks, int strafeMaxTicks, double strafeWeight, double forwardWeight,
                          int reactionMinTicks, int reactionMaxTicks, int blockPlaceDelayTicks) {
        this.name = name;
        this.reach = reach;
        this.cpsMean = cpsMean;
        this.cpsStddev = cpsStddev;
        this.aimOffset0 = aimOffset0;
        this.aimTau = aimTau;
        this.critConsistency = critConsistency;
        this.wtapReliability = wtapReliability;
        this.wtapTicks = wtapTicks;
        this.strafeMinTicks = strafeMinTicks;
        this.strafeMaxTicks = strafeMaxTicks;
        this.strafeWeight = strafeWeight;
        this.forwardWeight = forwardWeight;
        this.reactionMinTicks = reactionMinTicks;
        this.reactionMaxTicks = reactionMaxTicks;
        this.blockPlaceDelayTicks = blockPlaceDelayTicks;
    }
}

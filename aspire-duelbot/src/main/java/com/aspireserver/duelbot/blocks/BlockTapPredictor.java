package com.aspireserver.duelbot.blocks;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Offensive block-tap (Section 4.3): derive the opponent's velocity from position deltas
 * and place a block at their predicted feet to interrupt sprint/combo spacing.
 */
public final class BlockTapPredictor {

    private final Material tapMaterial;
    private final int placeDelayTicks;

    private Location lastTargetPos;
    private int cooldown;

    public BlockTapPredictor(Material tapMaterial, int placeDelayTicks) {
        this.tapMaterial = tapMaterial;
        this.placeDelayTicks = Math.max(1, placeDelayTicks);
    }

    public void observe(LivingEntity target) {
        Location now = target.getLocation();
        if (cooldown > 0) cooldown--;
        this.lastTargetPos = now.clone();
    }

    public boolean ready() {
        return cooldown <= 0;
    }

    /**
     * Predicts the opponent's feet block ~{@code ticksAhead} ticks out from a derived
     * velocity and places a block there. No-op if reach/cooldown/prediction fail.
     */
    public boolean tryTap(Player bot, LivingEntity target, double reach, int ticksAhead) {
        if (cooldown > 0 || lastTargetPos == null) return false;
        Location now = target.getLocation();
        if (!now.getWorld().equals(lastTargetPos.getWorld())) return false;

        Vector est = now.toVector().subtract(lastTargetPos.toVector());
        Vector predicted = now.toVector().add(est.multiply(ticksAhead));

        Block feet = now.getWorld().getBlockAt(
                predicted.getBlockX(), now.getBlockY(), predicted.getBlockZ());

        if (bot.getEyeLocation().distance(feet.getLocation().add(0.5, 0.5, 0.5)) > reach + 1.0) return false;

        boolean placed = BlockPlacer.place(bot, feet, tapMaterial);
        if (placed) cooldown = placeDelayTicks;
        return placed;
    }
}

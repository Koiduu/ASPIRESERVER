package com.aspireserver.duelbot;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/** Global, world-scoping and combat-tuning settings loaded from config.yml. */
public final class DuelBotSettings {

    public String duelWorld;
    public double aggroRange;
    public double disengageRange;
    public double combatRange;
    public double aimToleranceDegrees;
    public double panicHealthRatio;
    public double reengageHealthRatio;
    public int hitWindowTicks;
    public int hitCountThreshold;
    public int retargetIntervalTicks;
    public int blockTapTicksAhead;
    public double moveSpeed;
    public Material weaponMaterial;
    public Material buildMaterial;
    public boolean giveKit;
    public boolean defaultBlocksEnabled;

    public void load(FileConfiguration cfg) {
        duelWorld = cfg.getString("duel-world", "");
        aggroRange = cfg.getDouble("aggro-range", 16.0);
        disengageRange = cfg.getDouble("disengage-range", 24.0);
        combatRange = cfg.getDouble("combat-range", 4.0);
        aimToleranceDegrees = cfg.getDouble("aim-tolerance-degrees", 12.0);
        panicHealthRatio = cfg.getDouble("panic-health-ratio", 0.3);
        reengageHealthRatio = cfg.getDouble("reengage-health-ratio", 0.6);
        hitWindowTicks = cfg.getInt("hit-window-ticks", 40);
        hitCountThreshold = cfg.getInt("hit-count-threshold", 4);
        retargetIntervalTicks = cfg.getInt("retarget-interval-ticks", 10);
        blockTapTicksAhead = cfg.getInt("block-tap-ticks-ahead", 2);
        moveSpeed = cfg.getDouble("move-speed", 0.27);
        weaponMaterial = material(cfg.getString("kit.weapon", "DIAMOND_SWORD"), Material.DIAMOND_SWORD);
        buildMaterial = material(cfg.getString("kit.build-block", "SANDSTONE"), Material.SANDSTONE);
        giveKit = cfg.getBoolean("kit.give-on-spawn", true);
        defaultBlocksEnabled = cfg.getBoolean("blocks-enabled", true);
    }

    private Material material(String name, Material fallback) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name.toUpperCase());
        return m != null ? m : fallback;
    }
}

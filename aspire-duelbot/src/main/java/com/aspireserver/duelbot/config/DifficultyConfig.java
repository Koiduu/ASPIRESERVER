package com.aspireserver.duelbot.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Loads and holds the difficulty tiers from difficulty.yml. */
public final class DifficultyConfig {

    private final Plugin plugin;
    private final Map<String, DifficultyTier> tiers = new LinkedHashMap<>();
    private String defaultTier = "MEDIUM";

    public DifficultyConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration cfg) {
        tiers.clear();
        ConfigurationSection root = cfg.getConfigurationSection("difficulty");
        if (root == null) {
            plugin.getLogger().warning("No 'difficulty' section in difficulty.yml — using built-in defaults.");
            loadBuiltinDefaults();
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            tiers.put(key.toUpperCase(), parse(key.toUpperCase(), s));
        }
        if (tiers.isEmpty()) loadBuiltinDefaults();
        String cfgDefault = cfg.getString("default-tier", "MEDIUM");
        if (cfgDefault != null && tiers.containsKey(cfgDefault.toUpperCase())) {
            defaultTier = cfgDefault.toUpperCase();
        } else if (!tiers.containsKey(defaultTier)) {
            defaultTier = tiers.keySet().iterator().next();
        }
    }

    private DifficultyTier parse(String name, ConfigurationSection s) {
        double reach = s.getDouble("reach", 2.9);
        double cpsMean = s.getDouble("cps.mean", 7.0);
        double cpsStddev = s.getDouble("cps.stddev", 1.2);
        double offset0 = s.getDouble("aim.offset0", 4.0);
        double tau = s.getDouble("aim.tau", 4.0);
        double crit = s.getDouble("crit_consistency", 0.5);
        double wtap = s.getDouble("wtap_reliability", 0.4);
        int wtapTicks = s.getInt("wtap_ticks", 1);
        int strafeMin = s.getInt("strafe.min_ticks", 10);
        int strafeMax = s.getInt("strafe.max_ticks", 20);
        double strafeWeight = s.getDouble("strafe.weight", 0.75);
        double forwardWeight = s.getDouble("strafe.forward_weight", 1.0);
        int reactMin = s.getInt("reaction_delay_ticks.min", 5);
        int reactMax = s.getInt("reaction_delay_ticks.max", 8);
        int blockDelay = s.getInt("block_place_delay_ticks", 6);
        return new DifficultyTier(name, reach, cpsMean, cpsStddev, offset0, tau, crit, wtap,
                Math.max(0, wtapTicks), Math.max(1, strafeMin), Math.max(1, strafeMax),
                strafeWeight, forwardWeight, Math.max(0, reactMin), Math.max(0, reactMax),
                Math.max(1, blockDelay));
    }

    private void loadBuiltinDefaults() {
        tiers.put("EASY", new DifficultyTier("EASY", 2.8, 4.5, 1.5, 8.0, 6, 0.20, 0.10, 1, 15, 30, 0.6, 1.0, 8, 12, 10));
        tiers.put("MEDIUM", new DifficultyTier("MEDIUM", 2.9, 7.0, 1.2, 4.0, 4, 0.50, 0.40, 1, 10, 20, 0.75, 1.0, 5, 8, 6));
        tiers.put("HARD", new DifficultyTier("HARD", 2.95, 10.0, 0.8, 2.0, 2, 0.80, 0.80, 1, 6, 14, 0.9, 1.0, 2, 4, 3));
        tiers.put("HACKER", new DifficultyTier("HACKER", 3.0, 14.0, 0.2, 0.3, 0.5, 0.98, 0.99, 1, 4, 8, 1.0, 1.0, 0, 1, 1));
    }

    public DifficultyTier get(String name) {
        if (name == null) return tiers.get(defaultTier);
        return tiers.get(name.toUpperCase());
    }

    public DifficultyTier getDefault() { return tiers.get(defaultTier); }

    public Set<String> tierNames() { return tiers.keySet(); }
}

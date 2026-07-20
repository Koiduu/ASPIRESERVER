package com.aspireserver.duelbot;

import com.aspireserver.duelbot.config.DifficultyConfig;
import com.aspireserver.duelbot.npc.CombatTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.trait.TraitInfo;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Bootstrap: loads config + difficulty tiers, registers the Citizens {@link CombatTrait},
 * the /duelbot command and the incoming-hit listener.
 */
public final class DuelBotPlugin extends JavaPlugin {

    private static DuelBotPlugin instance;

    private final DuelBotSettings settings = new DuelBotSettings();
    private DifficultyConfig difficultyConfig;

    public static DuelBotPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        if (getServer().getPluginManager().getPlugin("Citizens") == null) {
            getLogger().severe("Citizens not found — aspire-duelbot requires Citizens2. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        reloadSettings();

        try {
            CitizensAPI.getTraitFactory().registerTrait(
                    TraitInfo.create(CombatTrait.class).withName("duelbot_combat"));
        } catch (Throwable t) {
            getLogger().warning("Trait registration issue (may already be registered): " + t.getMessage());
        }

        DuelBotCommand cmd = new DuelBotCommand(this);
        if (getCommand("duelbot") != null) {
            getCommand("duelbot").setExecutor(cmd);
            getCommand("duelbot").setTabCompleter(cmd);
        }
        if (getCommand("botkit") != null) {
            getCommand("botkit").setExecutor(cmd);
            getCommand("botkit").setTabCompleter(cmd);
        }
        getServer().getPluginManager().registerEvents(new DuelBotListener(), this);

        getLogger().info("AspireDuelBot enabled (tiers: " + difficultyConfig.tierNames() + ").");
    }

    public void reloadSettings() {
        reloadConfig();
        settings.load(getConfig());

        File file = new File(getDataFolder(), "difficulty.yml");
        if (!file.exists()) saveResource("difficulty.yml", false);
        difficultyConfig = new DifficultyConfig(this);
        difficultyConfig.load(YamlConfiguration.loadConfiguration(file));
    }

    public DuelBotSettings getSettings() {
        return settings;
    }

    public DifficultyConfig getDifficultyConfig() {
        return difficultyConfig;
    }
}

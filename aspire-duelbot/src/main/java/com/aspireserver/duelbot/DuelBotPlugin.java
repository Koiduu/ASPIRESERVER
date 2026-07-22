package com.aspireserver.duelbot;

import com.aspireserver.duelbot.config.DifficultyConfig;
import com.aspireserver.duelbot.learning.HumanSampleStore;
import com.aspireserver.duelbot.learning.MovementRecorder;
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

    private final HumanSampleStore sampleStore = new HumanSampleStore();
    private MovementRecorder recorder;

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

        sampleStore.configure(settings.maxSamplesPerBucket, settings.maxKbTraces);
        sampleStore.load(sampleFile(), getLogger());
        recorder = new MovementRecorder(this, sampleStore);
        recorder.setRecording(settings.recordOnStart);
        getServer().getPluginManager().registerEvents(recorder, this);
        getServer().getScheduler().runTaskTimer(this, recorder, 1L, 1L);
        int saveEvery = Math.max(200, settings.saveIntervalTicks);
        getServer().getScheduler().runTaskTimer(this, this::saveSamplesAsync, saveEvery, saveEvery);

        getLogger().info("AspireDuelBot enabled (tiers: " + difficultyConfig.tierNames() + ").");
    }

    @Override
    public void onDisable() {
        if (recorder != null) {
            HumanSampleStore.Snapshot snap = sampleStore.snapshotForSave();
            HumanSampleStore.save(snap, sampleFile(), getLogger()); // synchronous on shutdown
        }
    }

    private File sampleFile() {
        return new File(getDataFolder(), "human-samples.yml");
    }

    /** Copies an immutable snapshot on the main thread, then writes it off-thread. */
    public void saveSamplesAsync() {
        HumanSampleStore.Snapshot snap = sampleStore.snapshotForSave();
        getServer().getScheduler().runTaskAsynchronously(this,
                () -> HumanSampleStore.save(snap, sampleFile(), getLogger()));
    }

    public HumanSampleStore getSampleStore() {
        return sampleStore;
    }

    public MovementRecorder getRecorder() {
        return recorder;
    }

    public void reloadSettings() {
        reloadConfig();
        settings.load(getConfig());
        sampleStore.configure(settings.maxSamplesPerBucket, settings.maxKbTraces);

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

package com.aspireserver.creative;

import com.aspireserver.creative.commands.PlotCommand;
import com.aspireserver.creative.generator.FlatPlotGenerator;
import com.aspireserver.creative.listeners.PlotListener;
import com.aspireserver.creative.listeners.WorldEditLimiter;
import com.aspireserver.creative.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

public final class AspireCreative extends JavaPlugin {

    private static AspireCreative instance;
    private PlotManager plotManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        plotManager = new PlotManager(this);
        plotManager.loadPlots();

        registerCommands();
        registerListeners();
        initPlotWorlds();

        getLogger().info("[AspireCreative] Plugin enabled!");
    }

    @Override
    public void onDisable() {
        plotManager.savePlots();
        getLogger().info("[AspireCreative] Plugin disabled.");
    }

    private void registerCommands() {
        PlotCommand plotCmd = new PlotCommand(plotManager);
        getCommand("plot").setExecutor(plotCmd);
        getCommand("plot").setTabCompleter(plotCmd);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlotListener(plotManager), this);
        getServer().getPluginManager().registerEvents(new WorldEditLimiter(plotManager, this), this);
    }

    private void initPlotWorlds() {
        createPlotWorld("creative_small");
        createPlotWorld("creative_medium");
        createPlotWorld("creative_large");
    }

    private void createPlotWorld(String worldName) {
        if (Bukkit.getWorld(worldName) != null) return;

        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(new FlatPlotGenerator());
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(false);

        World world = creator.createWorld();
        if (world != null) {
            world.setAutoSave(true);
            world.setKeepSpawnInMemory(false);
            world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(org.bukkit.GameRule.RANDOM_TICK_SPEED, 0);
            getLogger().info("Created plot world: " + worldName);
        }
    }

    public static AspireCreative getInstance() {
        return instance;
    }

    public PlotManager getPlotManager() {
        return plotManager;
    }
}

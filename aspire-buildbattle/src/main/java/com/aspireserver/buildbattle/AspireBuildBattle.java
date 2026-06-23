package com.aspireserver.buildbattle;

import com.aspireserver.buildbattle.admin.PlotManagementGui;
import com.aspireserver.buildbattle.arena.ArenaManager;
import com.aspireserver.buildbattle.commands.BuildBattleCommand;
import com.aspireserver.buildbattle.commands.PlotSetCommand;
import com.aspireserver.buildbattle.commands.SpawnSetWaitingCommand;
import com.aspireserver.buildbattle.listeners.AntiGriefListener;
import com.aspireserver.buildbattle.listeners.PlotToolListener;
import com.aspireserver.buildbattle.listeners.PlayerListener;
import com.aspireserver.buildbattle.mob.MobManager;
import com.aspireserver.buildbattle.plot.FloorManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class AspireBuildBattle extends JavaPlugin {

    private static AspireBuildBattle instance;
    private ArenaManager arenaManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        arenaManager = new ArenaManager(this);
        arenaManager.loadArenas();

        registerCommands();
        registerListeners();

        getLogger().info("[AspireBuildBattle] Plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (arenaManager != null) {
            arenaManager.shutdown();
        }
        getLogger().info("[AspireBuildBattle] Plugin disabled.");
    }

    private void registerCommands() {
        PlotManagementGui plotGui = new PlotManagementGui(this);
        getServer().getPluginManager().registerEvents(plotGui, this);

        BuildBattleCommand bbCmd = new BuildBattleCommand(this);
        bbCmd.setPlotGui(plotGui);
        getCommand("buildbattle").setExecutor(bbCmd);
        getCommand("buildbattle").setTabCompleter(bbCmd);
        getCommand("bb").setExecutor(bbCmd);
        getCommand("bb").setTabCompleter(bbCmd);

        PlotSetCommand plotSetCmd = new PlotSetCommand(this);
        getCommand("plotset").setExecutor(plotSetCmd);

        SpawnSetWaitingCommand waitingCmd = new SpawnSetWaitingCommand(this);
        getCommand("spawnsetwaiting").setExecutor(waitingCmd);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new AntiGriefListener(this), this);
        getServer().getPluginManager().registerEvents(new PlotToolListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new MobManager(this), this);
        getServer().getPluginManager().registerEvents(new FloorManager(this), this);
    }

    public static AspireBuildBattle getInstance() {
        return instance;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }
}

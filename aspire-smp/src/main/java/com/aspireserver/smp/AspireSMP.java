package com.aspireserver.smp;

import com.aspireserver.smp.claim.ClaimManager;
import com.aspireserver.smp.commands.ClaimCommand;
import com.aspireserver.smp.commands.TrustCommand;
import com.aspireserver.smp.commands.UpgradeLandCommand;
import com.aspireserver.smp.graveyard.GraveyardManager;
import com.aspireserver.smp.listeners.ClaimListener;
import com.aspireserver.smp.listeners.DeathListener;
import com.aspireserver.smp.listeners.SleepListener;
import com.aspireserver.smp.listeners.VisualizerListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class AspireSMP extends JavaPlugin {

    private static AspireSMP instance;
    private ClaimManager claimManager;
    private GraveyardManager graveyardManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        claimManager = new ClaimManager(this);
        graveyardManager = new GraveyardManager(this);

        registerCommands();
        registerListeners();

        getLogger().info("[AspireSMP] Plugin enabled!");
    }

    @Override
    public void onDisable() {
        claimManager.saveAllClaims();
        graveyardManager.saveAll();
        getLogger().info("[AspireSMP] Plugin disabled.");
    }

    private void registerCommands() {
        ClaimCommand claimCmd = new ClaimCommand(claimManager);
        getCommand("claim").setExecutor(claimCmd);
        getCommand("claim").setTabCompleter(claimCmd);
        getCommand("unclaim").setExecutor(claimCmd);

        getCommand("trust").setExecutor(new TrustCommand(claimManager));
        getCommand("upgradeland").setExecutor(new UpgradeLandCommand(claimManager));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ClaimListener(claimManager), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new SleepListener(this), this);
        getServer().getPluginManager().registerEvents(new VisualizerListener(claimManager, this), this);
    }

    public static AspireSMP getInstance() {
        return instance;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public GraveyardManager getGraveyardManager() {
        return graveyardManager;
    }
}

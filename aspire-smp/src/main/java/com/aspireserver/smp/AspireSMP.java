package com.aspireserver.smp;

import com.aspireserver.smp.claim.ClaimManager;
import com.aspireserver.smp.commands.ClaimCommand;
import com.aspireserver.smp.commands.TradeCommand;
import com.aspireserver.smp.commands.TrustCommand;
import com.aspireserver.smp.commands.SmpWorldCommand;
import com.aspireserver.smp.commands.UpgradeLandCommand;
import com.aspireserver.smp.graveyard.GraveyardManager;
import com.aspireserver.smp.listeners.ClaimListener;
import com.aspireserver.smp.listeners.CombatLogListener;
import com.aspireserver.smp.listeners.DeathListener;
import com.aspireserver.smp.listeners.EnderDragonListener;
import com.aspireserver.smp.listeners.AntiLagListener;
import com.aspireserver.smp.listeners.GameplayFixListener;
import com.aspireserver.smp.listeners.LootBoostListener;
import com.aspireserver.smp.listeners.MobCapListener;
import com.aspireserver.smp.listeners.SleepListener;
import com.aspireserver.smp.listeners.SmpJoinListener;
import com.aspireserver.smp.listeners.SpawnProtectionListener;
import com.aspireserver.smp.listeners.TradeListener;
import com.aspireserver.smp.listeners.GoldenShovelListener;
import com.aspireserver.smp.listeners.VisualizerListener;
import com.aspireserver.smp.trade.TradeManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class AspireSMP extends JavaPlugin {

    private static AspireSMP instance;
    private ClaimManager claimManager;
    private GraveyardManager graveyardManager;
    private TradeManager tradeManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        claimManager = new ClaimManager(this);
        graveyardManager = new GraveyardManager(this);
        tradeManager = new TradeManager();

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

        SmpWorldCommand smpWorldCmd = new SmpWorldCommand(this);
        getCommand("smpworld").setExecutor(smpWorldCmd);
        getCommand("smpworld").setTabCompleter(smpWorldCmd);

        TradeCommand tradeCmd = new TradeCommand(tradeManager);
        getCommand("trade").setExecutor(tradeCmd);
        getCommand("trade").setTabCompleter(tradeCmd);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ClaimListener(claimManager), this);
        getServer().getPluginManager().registerEvents(new CombatLogListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new MobCapListener(this), this);
        getServer().getPluginManager().registerEvents(new SleepListener(this), this);
        getServer().getPluginManager().registerEvents(new SmpJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new VisualizerListener(claimManager, this), this);
        getServer().getPluginManager().registerEvents(new TradeListener(tradeManager), this);
        getServer().getPluginManager().registerEvents(new GoldenShovelListener(claimManager, this), this);
        getServer().getPluginManager().registerEvents(new SpawnProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new LootBoostListener(this), this);
        getServer().getPluginManager().registerEvents(new AntiLagListener(this), this);
        getServer().getPluginManager().registerEvents(new EnderDragonListener(this), this);
        getServer().getPluginManager().registerEvents(new GameplayFixListener(this), this);
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

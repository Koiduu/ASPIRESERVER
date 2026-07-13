package com.aspireserver.bedwars;

import com.aspireserver.bedwars.arena.ArenaResetManager;
import com.aspireserver.bedwars.chat.ChatManager;
import com.aspireserver.bedwars.commands.BedwarsCommand;
import com.aspireserver.bedwars.config.SetupConfigManager;
import com.aspireserver.bedwars.config.SetupModeManager;
import com.aspireserver.bedwars.game.DragonManager;
import com.aspireserver.bedwars.game.GameManager;
import com.aspireserver.bedwars.generator.GeneratorManager;
import com.aspireserver.bedwars.listeners.BlockListener;
import com.aspireserver.bedwars.listeners.CombatListener;
import com.aspireserver.bedwars.listeners.ConnectionListener;
import com.aspireserver.bedwars.listeners.InteractionListener;
import com.aspireserver.bedwars.listeners.SpecialItemListener;
import com.aspireserver.bedwars.shop.NPCManager;
import com.aspireserver.bedwars.shop.ShopManager;
import com.aspireserver.bedwars.spectator.SpectatorManager;
import com.aspireserver.bedwars.stats.StatsManager;
import com.aspireserver.bedwars.ui.ScoreboardUI;
import com.aspireserver.bedwars.team.TeamManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class AspireBedwars extends JavaPlugin {

    private SetupConfigManager setupConfig;
    private SetupModeManager setupMode;
    private TeamManager teamManager;
    private GameManager gameManager;
    private GeneratorManager generatorManager;
    private ShopManager shopManager;
    private NPCManager npcManager;
    private ChatManager chatManager;
    private StatsManager statsManager;
    private ArenaResetManager arenaReset;
    private SpectatorManager spectatorManager;
    private DragonManager dragonManager;
    private ScoreboardUI scoreboardUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        com.aspireserver.bedwars.util.Keys.init(this);
        setupConfig = new SetupConfigManager(this);
        setupMode = new SetupModeManager(this);
        teamManager = new TeamManager(this);
        statsManager = new StatsManager(this);
        statsManager.init();
        arenaReset = new ArenaResetManager(this);
        spectatorManager = new SpectatorManager(this);
        chatManager = new ChatManager(this);
        generatorManager = new GeneratorManager(this);
        shopManager = new ShopManager(this);
        npcManager = new NPCManager(this);
        dragonManager = new DragonManager(this);
        scoreboardUI = new ScoreboardUI(this);
        gameManager = new GameManager(this);

        registerListeners();
        registerCommands();

        // Soft-depend diagnostics
        if (getServer().getPluginManager().getPlugin("OldCombatMechanics") == null) {
            getLogger().info("[Bedwars] OldCombatMechanics not found — configure a bedwars-scoped OCM modeset for 1.8 combat when available.");
        }
        if (getServer().getWorld(setupConfig.getWorldName()) == null) {
            getLogger().warning("[Bedwars] World '" + setupConfig.getWorldName() + "' is not loaded. Load your map world with that name.");
        }

        getLogger().info("[AspireBedwars] Enabled.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.isRunning()) gameManager.fullReset();
        if (npcManager != null) npcManager.despawnAll();
        if (statsManager != null) statsManager.close();
        getLogger().info("[AspireBedwars] Disabled.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(setupMode, this);
        getServer().getPluginManager().registerEvents(chatManager, this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractionListener(this), this);
        getServer().getPluginManager().registerEvents(new SpecialItemListener(this), this);
    }

    private void registerCommands() {
        BedwarsCommand cmd = new BedwarsCommand(this);
        getCommand("bw").setExecutor(cmd);
        getCommand("bw").setTabCompleter(cmd);
    }

    public SetupConfigManager getSetupConfig() { return setupConfig; }
    public SetupModeManager getSetupMode() { return setupMode; }
    public TeamManager getTeamManager() { return teamManager; }
    public GameManager getGameManager() { return gameManager; }
    public GeneratorManager getGeneratorManager() { return generatorManager; }
    public ShopManager getShopManager() { return shopManager; }
    public NPCManager getNpcManager() { return npcManager; }
    public ChatManager getChatManager() { return chatManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public ArenaResetManager getArenaReset() { return arenaReset; }
    public SpectatorManager getSpectatorManager() { return spectatorManager; }
    public DragonManager getDragonManager() { return dragonManager; }
    public ScoreboardUI getScoreboardUI() { return scoreboardUI; }
}

package com.aspireserver.chameleon;

import com.aspireserver.chameleon.commands.ChameleonCommand;
import com.aspireserver.chameleon.config.ConfigManager;
import com.aspireserver.chameleon.game.CamoManager;
import com.aspireserver.chameleon.game.GameManager;
import com.aspireserver.chameleon.listeners.*;
import com.aspireserver.chameleon.lobby.LobbyManager;
import com.aspireserver.chameleon.scoreboard.ScoreboardManager;
import com.aspireserver.chameleon.skin.SkinCache;
import org.bukkit.plugin.java.JavaPlugin;

public class MecchaChameleon extends JavaPlugin {

    private static MecchaChameleon instance;
    private ConfigManager configManager;
    private SkinCache skinCache;
    private CamoManager camoManager;
    private GameManager gameManager;
    private LobbyManager lobbyManager;
    private ScoreboardManager scoreboardManager;
    private HiderItemListener hiderItemListener;

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);
        skinCache = new SkinCache(this, configManager);
        camoManager = new CamoManager(this);
        scoreboardManager = new ScoreboardManager(this);
        gameManager = new GameManager(this, configManager, skinCache, scoreboardManager);
        lobbyManager = new LobbyManager(this, configManager, gameManager);

        // Register commands
        ChameleonCommand cmd = new ChameleonCommand(this);
        getCommand("chameleon").setExecutor(cmd);
        getCommand("chameleon").setTabCompleter(cmd);

        // Register listeners
        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        hiderItemListener = new HiderItemListener(this);
        getServer().getPluginManager().registerEvents(hiderItemListener, this);
        getServer().getPluginManager().registerEvents(new QuakeGunListener(this), this);
        getServer().getPluginManager().registerEvents(new WallClimbListener(this), this);

        // Fetch skins async
        skinCache.fetchAllSkins();

        getLogger().info("MecchaChameleon enabled!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.isGameActive()) {
            gameManager.forceStop();
        }
        getLogger().info("MecchaChameleon disabled.");
    }

    public static MecchaChameleon getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public SkinCache getSkinCache() { return skinCache; }
    public CamoManager getCamoManager() { return camoManager; }
    public GameManager getGameManager() { return gameManager; }
    public LobbyManager getLobbyManager() { return lobbyManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public HiderItemListener getHiderItemListener() { return hiderItemListener; }
}

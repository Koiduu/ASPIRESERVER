package com.aspireserver.chameleon;

import com.aspireserver.chameleon.commands.CamoCommand;
import com.aspireserver.chameleon.config.ConfigManager;
import com.aspireserver.chameleon.game.GameManager;
import com.aspireserver.chameleon.listeners.GuiListener;
import com.aspireserver.chameleon.listeners.PlayerListener;
import com.aspireserver.chameleon.skin.SkinCache;
import org.bukkit.plugin.java.JavaPlugin;

public final class AspireChameleon extends JavaPlugin {

    private static AspireChameleon instance;
    private ConfigManager configManager;
    private SkinCache skinCache;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);
        skinCache = new SkinCache(this, configManager);
        gameManager = new GameManager(this, configManager, skinCache);

        // Pre-fetch all configured skins asynchronously
        skinCache.fetchAllSkins();

        CamoCommand cmd = new CamoCommand(gameManager);
        getCommand("camo").setExecutor(cmd);
        getCommand("camo").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new GuiListener(gameManager, skinCache), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(gameManager), this);

        getLogger().info("[AspireChameleon] Plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stopGame();
        }
        getLogger().info("[AspireChameleon] Plugin disabled.");
    }

    public static AspireChameleon getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() { return configManager; }
    public SkinCache getSkinCache() { return skinCache; }
    public GameManager getGameManager() { return gameManager; }
}

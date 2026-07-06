package com.aspireserver.chameleon;

import com.aspireserver.chameleon.commands.CamoCommand;
import com.aspireserver.chameleon.commands.SpawnCommand;
import com.aspireserver.chameleon.config.ConfigManager;
import com.aspireserver.chameleon.game.GameManager;
import com.aspireserver.chameleon.listeners.GuiListener;
import com.aspireserver.chameleon.listeners.PlayerListener;
import com.aspireserver.chameleon.skin.SkinCache;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
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

        // Create the chameleon flat world if it doesn't exist
        createChameleonWorld();

        // Pre-fetch all configured skins asynchronously
        skinCache.fetchAllSkins();

        CamoCommand cmd = new CamoCommand(gameManager);
        getCommand("camo").setExecutor(cmd);
        getCommand("camo").setTabCompleter(cmd);

        SpawnCommand spawnCmd = new SpawnCommand(configManager);
        getCommand("setchameleonspawn").setExecutor(spawnCmd);
        getCommand("setchameleonspawn").setTabCompleter(spawnCmd);
        getCommand("removechameleonspawn").setExecutor(spawnCmd);
        getCommand("removechameleonspawn").setTabCompleter(spawnCmd);

        getServer().getPluginManager().registerEvents(new GuiListener(gameManager, skinCache), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(gameManager), this);

        getLogger().info("[AspireChameleon] Plugin enabled!");
    }

    private void createChameleonWorld() {
        String worldName = configManager.getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            getLogger().info("[AspireChameleon] World '" + worldName + "' already loaded.");
            return;
        }

        getLogger().info("[AspireChameleon] Creating flat world '" + worldName + "'...");
        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        Bukkit.createWorld(creator);
        getLogger().info("[AspireChameleon] World '" + worldName + "' created!");
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

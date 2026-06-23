package com.aspireserver.core.loadout;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LoadoutManager {

    private final JavaPlugin plugin;
    private final File loadoutsFile;
    private FileConfiguration loadoutsConfig;
    private final Map<UUID, Map<String, ItemStack[]>> playerLoadouts;

    public LoadoutManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.loadoutsFile = new File(plugin.getDataFolder(), "loadouts.yml");
        this.playerLoadouts = new HashMap<>();
        load();
    }

    private void load() {
        if (!loadoutsFile.exists()) {
            try {
                loadoutsFile.getParentFile().mkdirs();
                loadoutsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create loadouts.yml");
            }
        }
        loadoutsConfig = YamlConfiguration.loadConfiguration(loadoutsFile);

        ConfigurationSection section = loadoutsConfig.getConfigurationSection("loadouts");
        if (section == null) return;

        for (String uuidStr : section.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            ConfigurationSection playerSection = section.getConfigurationSection(uuidStr);
            if (playerSection == null) continue;

            Map<String, ItemStack[]> loadouts = new HashMap<>();
            for (String name : playerSection.getKeys(false)) {
                @SuppressWarnings("unchecked")
                List<ItemStack> items = (List<ItemStack>) playerSection.getList(name);
                if (items != null) {
                    loadouts.put(name, items.toArray(new ItemStack[0]));
                }
            }
            playerLoadouts.put(uuid, loadouts);
        }
    }

    public void save() {
        loadoutsConfig = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, ItemStack[]>> entry : playerLoadouts.entrySet()) {
            for (Map.Entry<String, ItemStack[]> loadout : entry.getValue().entrySet()) {
                String path = "loadouts." + entry.getKey().toString() + "." + loadout.getKey();
                loadoutsConfig.set(path, Arrays.asList(loadout.getValue()));
            }
        }
        try {
            loadoutsConfig.save(loadoutsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save loadouts.yml");
        }
    }

    public boolean saveLoadout(UUID player, String name, ItemStack[] contents) {
        playerLoadouts.computeIfAbsent(player, k -> new HashMap<>()).put(name, contents.clone());
        save();
        return true;
    }

    public ItemStack[] getLoadout(UUID player, String name) {
        Map<String, ItemStack[]> loadouts = playerLoadouts.get(player);
        if (loadouts == null) return null;
        return loadouts.get(name);
    }

    public boolean deleteLoadout(UUID player, String name) {
        Map<String, ItemStack[]> loadouts = playerLoadouts.get(player);
        if (loadouts == null) return false;
        boolean removed = loadouts.remove(name) != null;
        if (removed) save();
        return removed;
    }

    public Set<String> getLoadoutNames(UUID player) {
        Map<String, ItemStack[]> loadouts = playerLoadouts.get(player);
        if (loadouts == null) return Collections.emptySet();
        return loadouts.keySet();
    }

    public String getAutoLoadout(UUID player) {
        String autoName = plugin.getConfig().getString("auto-loadout." + player.toString());
        return autoName;
    }

    public void setAutoLoadout(UUID player, String name) {
        plugin.getConfig().set("auto-loadout." + player.toString(), name);
        plugin.saveConfig();
    }

    public void clearAutoLoadout(UUID player) {
        plugin.getConfig().set("auto-loadout." + player.toString(), null);
        plugin.saveConfig();
    }
}

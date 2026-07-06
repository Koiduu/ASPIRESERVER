package com.aspireserver.chameleon.config;

import com.aspireserver.chameleon.AspireChameleon;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ConfigManager {

    private final AspireChameleon plugin;
    private File mapsFile;
    private YamlConfiguration mapsConfig;
    private double scaleFactor;
    private int gracePeriod;
    private String worldName;
    private final Map<String, List<SkinEntry>> mapSkins = new HashMap<>();
    private final Map<String, Location> mapSpawns = new HashMap<>();

    public ConfigManager(AspireChameleon plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        mapsFile = new File(plugin.getDataFolder(), "maps.yml");
        if (!mapsFile.exists()) {
            plugin.saveResource("maps.yml", false);
        }
        mapsConfig = YamlConfiguration.loadConfiguration(mapsFile);

        scaleFactor = mapsConfig.getDouble("scale-factor", 0.35);
        gracePeriod = mapsConfig.getInt("grace-period", 30);
        worldName = mapsConfig.getString("world-name", "chameleon");

        mapSkins.clear();
        mapSpawns.clear();

        ConfigurationSection mapsSection = mapsConfig.getConfigurationSection("maps");
        if (mapsSection == null) return;

        for (String mapId : mapsSection.getKeys(false)) {
            ConfigurationSection mapSection = mapsSection.getConfigurationSection(mapId);
            if (mapSection == null) continue;

            // Load skins
            List<SkinEntry> skins = new ArrayList<>();
            List<?> skinsList = mapSection.getList("skins");
            if (skinsList != null) {
                for (Object obj : skinsList) {
                    if (obj instanceof Map<?, ?> map) {
                        String name = String.valueOf(map.get("name"));
                        String uuid = String.valueOf(map.get("uuid"));
                        if (name != null && uuid != null) {
                            skins.add(new SkinEntry(name, uuid.replace("-", "")));
                        }
                    }
                }
            }
            mapSkins.put(mapId, skins);

            // Load spawn location
            if (mapSection.contains("spawn")) {
                ConfigurationSection spawnSection = mapSection.getConfigurationSection("spawn");
                if (spawnSection != null) {
                    String spawnWorldName = spawnSection.getString("world", worldName);
                    double x = spawnSection.getDouble("x");
                    double y = spawnSection.getDouble("y");
                    double z = spawnSection.getDouble("z");
                    float yaw = (float) spawnSection.getDouble("yaw", 0);
                    float pitch = (float) spawnSection.getDouble("pitch", 0);
                    World world = Bukkit.getWorld(spawnWorldName);
                    if (world != null) {
                        mapSpawns.put(mapId, new Location(world, x, y, z, yaw, pitch));
                    }
                }
            }
        }
    }

    public void setMapSpawn(String mapId, Location location) {
        mapSpawns.put(mapId, location);

        // Ensure map section exists
        ConfigurationSection mapsSection = mapsConfig.getConfigurationSection("maps");
        if (mapsSection == null) {
            mapsSection = mapsConfig.createSection("maps");
        }
        ConfigurationSection mapSection = mapsSection.getConfigurationSection(mapId);
        if (mapSection == null) {
            mapSection = mapsSection.createSection(mapId);
        }

        ConfigurationSection spawnSection = mapSection.createSection("spawn");
        spawnSection.set("world", location.getWorld().getName());
        spawnSection.set("x", location.getX());
        spawnSection.set("y", location.getY());
        spawnSection.set("z", location.getZ());
        spawnSection.set("yaw", location.getYaw());
        spawnSection.set("pitch", location.getPitch());

        saveConfig();
    }

    public void removeMapSpawn(String mapId) {
        mapSpawns.remove(mapId);

        ConfigurationSection mapsSection = mapsConfig.getConfigurationSection("maps");
        if (mapsSection == null) return;
        ConfigurationSection mapSection = mapsSection.getConfigurationSection(mapId);
        if (mapSection == null) return;

        mapSection.set("spawn", null);
        saveConfig();
    }

    public Location getMapSpawn(String mapId) {
        return mapSpawns.get(mapId);
    }

    private void saveConfig() {
        try {
            mapsConfig.save(mapsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[ConfigManager] Failed to save maps.yml: " + e.getMessage());
        }
    }

    public double getScaleFactor() { return scaleFactor; }
    public int getGracePeriod() { return gracePeriod; }
    public String getWorldName() { return worldName; }

    public Set<String> getMapNames() { return mapSkins.keySet(); }

    public List<SkinEntry> getSkinsForMap(String mapId) {
        return mapSkins.getOrDefault(mapId, Collections.emptyList());
    }

    public String getMapDisplayName(String mapId) {
        ConfigurationSection mapsSection = mapsConfig.getConfigurationSection("maps");
        if (mapsSection == null) return mapId;
        ConfigurationSection mapSection = mapsSection.getConfigurationSection(mapId);
        if (mapSection == null) return mapId;
        return mapSection.getString("display-name", mapId);
    }

    public Set<String> getAllMapIds() {
        ConfigurationSection mapsSection = mapsConfig.getConfigurationSection("maps");
        if (mapsSection == null) return Collections.emptySet();
        return mapsSection.getKeys(false);
    }

    public record SkinEntry(String name, String uuid) {}
}

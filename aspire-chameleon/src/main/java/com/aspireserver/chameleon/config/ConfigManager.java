package com.aspireserver.chameleon.config;

import com.aspireserver.chameleon.AspireChameleon;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class ConfigManager {

    private final AspireChameleon plugin;
    private YamlConfiguration mapsConfig;
    private double scaleFactor;
    private int gracePeriod;
    private final Map<String, List<SkinEntry>> mapSkins = new HashMap<>();

    public ConfigManager(AspireChameleon plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File mapsFile = new File(plugin.getDataFolder(), "maps.yml");
        if (!mapsFile.exists()) {
            plugin.saveResource("maps.yml", false);
        }
        mapsConfig = YamlConfiguration.loadConfiguration(mapsFile);

        scaleFactor = mapsConfig.getDouble("scale-factor", 0.35);
        gracePeriod = mapsConfig.getInt("grace-period", 30);

        mapSkins.clear();
        ConfigurationSection mapsSection = mapsConfig.getConfigurationSection("maps");
        if (mapsSection == null) return;

        for (String mapId : mapsSection.getKeys(false)) {
            ConfigurationSection mapSection = mapsSection.getConfigurationSection(mapId);
            if (mapSection == null) continue;

            List<SkinEntry> skins = new ArrayList<>();
            List<?> skinsList = mapSection.getList("skins");
            if (skinsList == null) continue;

            for (Object obj : skinsList) {
                if (obj instanceof Map<?, ?> map) {
                    String name = String.valueOf(map.get("name"));
                    String uuid = String.valueOf(map.get("uuid"));
                    if (name != null && uuid != null) {
                        skins.add(new SkinEntry(name, uuid.replace("-", "")));
                    }
                }
            }
            mapSkins.put(mapId, skins);
        }
    }

    public double getScaleFactor() { return scaleFactor; }
    public int getGracePeriod() { return gracePeriod; }

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

    public record SkinEntry(String name, String uuid) {}
}

package com.aspireserver.chameleon.config;

import com.aspireserver.chameleon.MecchaChameleon;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ConfigManager {

    private final MecchaChameleon plugin;
    private File mapsFile;
    private YamlConfiguration mapsConfig;

    // Global settings
    private double scaleFactor;
    private int defaultRoundDuration; // seconds
    private int defaultHidingDuration; // seconds
    private int defaultSeekerCount;
    private int minPlayers;
    private int lobbyCountdown;
    private int quakeGunCooldown; // ticks
    private String worldName;

    // Locations
    private Location lobbySpawn;
    private Location hunterRoom;

    // Map data
    private final Map<String, MapData> maps = new HashMap<>();

    public ConfigManager(MecchaChameleon plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        scaleFactor = plugin.getConfig().getDouble("scale-factor", 0.35);
        defaultRoundDuration = plugin.getConfig().getInt("default-round-duration", 300);
        defaultHidingDuration = plugin.getConfig().getInt("default-hiding-duration", 30);
        defaultSeekerCount = plugin.getConfig().getInt("default-seeker-count", 1);
        minPlayers = plugin.getConfig().getInt("min-players", 2);
        lobbyCountdown = plugin.getConfig().getInt("lobby-countdown", 30);
        quakeGunCooldown = plugin.getConfig().getInt("quake-gun-cooldown", 60);
        worldName = plugin.getConfig().getString("world-name", "chameleon");

        // Load lobby spawn
        if (plugin.getConfig().contains("lobby-spawn")) {
            lobbySpawn = deserializeLocation(plugin.getConfig().getConfigurationSection("lobby-spawn"));
        }
        if (plugin.getConfig().contains("hunter-room")) {
            hunterRoom = deserializeLocation(plugin.getConfig().getConfigurationSection("hunter-room"));
        }

        loadMaps();
    }

    private void loadMaps() {
        mapsFile = new File(plugin.getDataFolder(), "maps.yml");
        if (!mapsFile.exists()) {
            plugin.saveResource("maps.yml", false);
        }
        mapsConfig = YamlConfiguration.loadConfiguration(mapsFile);
        maps.clear();

        ConfigurationSection mapsSection = mapsConfig.getConfigurationSection("maps");
        if (mapsSection == null) return;

        for (String mapId : mapsSection.getKeys(false)) {
            ConfigurationSection sec = mapsSection.getConfigurationSection(mapId);
            if (sec == null) continue;

            MapData data = new MapData(mapId);
            data.displayName = sec.getString("display-name", mapId);

            // Load hider spawns
            if (sec.contains("hider-spawns")) {
                List<?> spawns = sec.getList("hider-spawns");
                if (spawns != null) {
                    for (Object obj : spawns) {
                        if (obj instanceof Map<?, ?> m) {
                            Location loc = deserializeLocationMap(m);
                            if (loc != null) data.hiderSpawns.add(loc);
                        }
                    }
                }
            }

            // Load seeker spawn
            if (sec.contains("seeker-spawn")) {
                data.seekerSpawn = deserializeLocation(sec.getConfigurationSection("seeker-spawn"));
            }

            // Load skins
            List<?> skinsList = sec.getList("skins");
            if (skinsList != null) {
                for (Object obj : skinsList) {
                    if (obj instanceof Map<?, ?> m) {
                        String name = String.valueOf(m.get("name"));
                        String uuid = String.valueOf(m.get("uuid"));
                        if (name != null && uuid != null) {
                            data.skins.add(new SkinEntry(name, uuid.replace("-", "")));
                        }
                    }
                }
            }

            maps.put(mapId, data);
        }
    }

    // --- Save methods ---

    public void setLobbySpawn(Location loc) {
        this.lobbySpawn = loc;
        serializeLocation(plugin.getConfig().createSection("lobby-spawn"), loc);
        plugin.saveConfig();
    }

    public void setHunterRoom(Location loc) {
        this.hunterRoom = loc;
        serializeLocation(plugin.getConfig().createSection("hunter-room"), loc);
        plugin.saveConfig();
    }

    public void createMap(String mapId) {
        maps.put(mapId, new MapData(mapId));
        ConfigurationSection sec = mapsConfig.getConfigurationSection("maps");
        if (sec == null) sec = mapsConfig.createSection("maps");
        sec.createSection(mapId).set("display-name", mapId);
        saveMaps();
    }

    public boolean deleteMap(String mapId) {
        if (!maps.containsKey(mapId)) return false;
        maps.remove(mapId);
        ConfigurationSection sec = mapsConfig.getConfigurationSection("maps");
        if (sec != null) sec.set(mapId, null);
        saveMaps();
        return true;
    }

    public void addHiderSpawn(String mapId, Location loc) {
        MapData data = maps.get(mapId);
        if (data == null) return;
        data.hiderSpawns.add(loc);
        saveMapSpawns(mapId, data);
    }

    public void setSeekerSpawn(String mapId, Location loc) {
        MapData data = maps.get(mapId);
        if (data == null) return;
        data.seekerSpawn = loc;

        ConfigurationSection sec = getOrCreateMapSection(mapId);
        serializeLocation(sec.createSection("seeker-spawn"), loc);
        saveMaps();
    }

    public void addSkin(String mapId, String name, String uuid) {
        MapData data = maps.computeIfAbsent(mapId, MapData::new);
        data.skins.add(new SkinEntry(name, uuid));
        saveMapSkins(mapId, data);
    }

    public boolean removeSkin(String mapId, String skinName) {
        MapData data = maps.get(mapId);
        if (data == null) return false;
        boolean removed = data.skins.removeIf(e -> e.name().equalsIgnoreCase(skinName));
        if (removed) saveMapSkins(mapId, data);
        return removed;
    }

    private void saveMapSpawns(String mapId, MapData data) {
        ConfigurationSection sec = getOrCreateMapSection(mapId);
        List<Map<String, Object>> spawnList = new ArrayList<>();
        for (Location l : data.hiderSpawns) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", l.getWorld().getName());
            m.put("x", l.getX());
            m.put("y", l.getY());
            m.put("z", l.getZ());
            m.put("yaw", (double) l.getYaw());
            m.put("pitch", (double) l.getPitch());
            spawnList.add(m);
        }
        sec.set("hider-spawns", spawnList);
        saveMaps();
    }

    private void saveMapSkins(String mapId, MapData data) {
        ConfigurationSection sec = getOrCreateMapSection(mapId);
        List<Map<String, String>> skinsList = new ArrayList<>();
        for (SkinEntry e : data.skins) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("name", e.name());
            m.put("uuid", e.uuid());
            skinsList.add(m);
        }
        sec.set("skins", skinsList);
        saveMaps();
    }

    private ConfigurationSection getOrCreateMapSection(String mapId) {
        ConfigurationSection sec = mapsConfig.getConfigurationSection("maps");
        if (sec == null) sec = mapsConfig.createSection("maps");
        ConfigurationSection mapSec = sec.getConfigurationSection(mapId);
        if (mapSec == null) mapSec = sec.createSection(mapId);
        return mapSec;
    }

    private void saveMaps() {
        try {
            mapsConfig.save(mapsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save maps.yml: " + e.getMessage());
        }
    }

    // --- Location serialization ---

    private void serializeLocation(ConfigurationSection sec, Location loc) {
        sec.set("world", loc.getWorld().getName());
        sec.set("x", loc.getX());
        sec.set("y", loc.getY());
        sec.set("z", loc.getZ());
        sec.set("yaw", (double) loc.getYaw());
        sec.set("pitch", (double) loc.getPitch());
    }

    private Location deserializeLocation(ConfigurationSection sec) {
        if (sec == null) return null;
        String wName = sec.getString("world", worldName);
        World w = Bukkit.getWorld(wName);
        if (w == null) return null;
        return new Location(w, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                (float) sec.getDouble("yaw"), (float) sec.getDouble("pitch"));
    }

    private Location deserializeLocationMap(Object obj) {
        if (!(obj instanceof Map<?, ?> m)) return null;
        Object worldObj = m.get("world");
        String wName = worldObj != null ? String.valueOf(worldObj) : worldName;
        World w = Bukkit.getWorld(wName);
        if (w == null) return null;
        double x = ((Number) m.get("x")).doubleValue();
        double y = ((Number) m.get("y")).doubleValue();
        double z = ((Number) m.get("z")).doubleValue();
        double yaw = m.containsKey("yaw") ? ((Number) m.get("yaw")).doubleValue() : 0;
        double pitch = m.containsKey("pitch") ? ((Number) m.get("pitch")).doubleValue() : 0;
        return new Location(w, x, y, z, (float) yaw, (float) pitch);
    }

    // --- Getters ---

    public double getScaleFactor() { return scaleFactor; }
    public int getDefaultRoundDuration() { return defaultRoundDuration; }
    public int getDefaultHidingDuration() { return defaultHidingDuration; }
    public int getDefaultSeekerCount() { return defaultSeekerCount; }
    public int getMinPlayers() { return minPlayers; }
    public int getLobbyCountdown() { return lobbyCountdown; }
    public int getQuakeGunCooldown() { return quakeGunCooldown; }
    public String getWorldName() { return worldName; }
    public Location getLobbySpawn() { return lobbySpawn; }
    public Location getHunterRoom() { return hunterRoom; }
    public Map<String, MapData> getMaps() { return maps; }
    public MapData getMap(String id) { return maps.get(id); }
    public Set<String> getMapIds() { return maps.keySet(); }

    // --- Inner classes ---

    public static class MapData {
        public final String id;
        public String displayName;
        public final List<Location> hiderSpawns = new ArrayList<>();
        public Location seekerSpawn;
        public final List<SkinEntry> skins = new ArrayList<>();

        public MapData(String id) {
            this.id = id;
            this.displayName = id;
        }
    }

    public record SkinEntry(String name, String uuid) {}
}

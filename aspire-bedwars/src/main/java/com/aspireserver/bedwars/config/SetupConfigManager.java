package com.aspireserver.bedwars.config;

import com.aspireserver.bedwars.AspireBedwars;
import com.aspireserver.bedwars.generator.GeneratorType;
import com.aspireserver.bedwars.team.TeamColor;
import com.aspireserver.bedwars.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Stores and persists map setup. Supports multiple named map layouts (each with
 * its own lobby spawn, per-team bed+spawn, generators, and NPCs) within the
 * single shared bedwars world. One map is "current" — it is both what /bw sb
 * edits and what games use. Backed by setup.yml.
 */
public class SetupConfigManager {

    private final AspireBedwars plugin;
    private File file;
    private YamlConfiguration cfg;

    private String worldName;

    // Multiple maps, keyed by id; currentMapId is the active/edited map.
    private final Map<String, MapConfig> maps = new LinkedHashMap<>();
    private String currentMapId;

    // Timeline (minutes) — all overridable in config.yml
    private int diamondTier2Min;
    private int diamondTier3Min;
    private int bedDestructionMin;
    private int suddenDeathMin;
    private int gameOverMin;

    private int minPlayers;
    private int maxTeams;
    private int teamSize;
    private int lobbyCountdown;
    private int respawnSeconds;
    private int respawnInvulnSeconds;
    private int disconnectGraceSeconds;
    private int sharpnessCap;
    private final List<LoadoutItem> loadout = new ArrayList<>();

    public SetupConfigManager(AspireBedwars plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        worldName = plugin.getConfig().getString("world-name", "bedwars");
        diamondTier2Min = plugin.getConfig().getInt("timeline.diamond-tier2-min", 6);
        diamondTier3Min = plugin.getConfig().getInt("timeline.diamond-tier3-min", 6);
        bedDestructionMin = plugin.getConfig().getInt("timeline.bed-destruction-min", 6);
        suddenDeathMin = plugin.getConfig().getInt("timeline.sudden-death-min", 10);
        gameOverMin = plugin.getConfig().getInt("timeline.game-over-min", 10);

        minPlayers = plugin.getConfig().getInt("min-players", 2);
        maxTeams = plugin.getConfig().getInt("max-teams", 8);
        teamSize = plugin.getConfig().getInt("team-size", 1);
        lobbyCountdown = plugin.getConfig().getInt("lobby-countdown", 20);
        respawnSeconds = plugin.getConfig().getInt("respawn-seconds", 5);
        respawnInvulnSeconds = plugin.getConfig().getInt("respawn-invuln-seconds", 4);
        disconnectGraceSeconds = plugin.getConfig().getInt("disconnect-grace-seconds", 60);
        sharpnessCap = plugin.getConfig().getInt("sharpness-cap", 3);

        loadLoadout();
        loadSetup();
    }

    private void loadLoadout() {
        loadout.clear();
        List<Map<?, ?>> defined = plugin.getConfig().getMapList("loadout");
        if (defined.isEmpty()) {
            loadout.add(new LoadoutItem(Material.WOODEN_SWORD, 1, true));
            return;
        }
        for (Map<?, ?> entry : defined) {
            Object matRaw = entry.get("material");
            if (matRaw == null) continue;
            Material mat = Material.matchMaterial(String.valueOf(matRaw));
            if (mat == null) {
                plugin.getLogger().warning("Unknown loadout material: " + matRaw);
                continue;
            }
            int amount = entry.get("amount") instanceof Number n ? n.intValue() : 1;
            boolean unbreakable = Boolean.parseBoolean(String.valueOf(entry.get("unbreakable")));
            loadout.add(new LoadoutItem(mat, Math.max(1, amount), unbreakable));
        }
        if (loadout.isEmpty()) loadout.add(new LoadoutItem(Material.WOODEN_SWORD, 1, true));
    }

    public List<LoadoutItem> getLoadout() { return loadout; }

    private void loadSetup() {
        file = new File(plugin.getDataFolder(), "setup.yml");
        cfg = YamlConfiguration.loadConfiguration(file);
        maps.clear();

        ConfigurationSection mapsSection = cfg.getConfigurationSection("maps");
        if (mapsSection != null) {
            for (String id : mapsSection.getKeys(false)) {
                ConfigurationSection ms = mapsSection.getConfigurationSection(id);
                if (ms != null) maps.put(id, loadMap(id, ms));
            }
        }

        // Legacy single-map migration: top-level lobby-spawn/teams/generators/npcs
        if (maps.isEmpty() && (cfg.contains("lobby-spawn") || cfg.contains("teams")
                || cfg.contains("generators") || cfg.contains("npcs"))) {
            MapConfig legacy = loadMap("default", cfg);
            maps.put("default", legacy);
        }

        currentMapId = cfg.getString("current-map");
        if (currentMapId == null || !maps.containsKey(currentMapId)) {
            currentMapId = maps.isEmpty() ? null : maps.keySet().iterator().next();
        }
        if (maps.isEmpty()) {
            // Ensure at least one editable map exists
            maps.put("default", new MapConfig("default"));
            currentMapId = "default";
        }
    }

    private MapConfig loadMap(String id, ConfigurationSection s) {
        MapConfig map = new MapConfig(id);
        map.lobbySpawn = LocationUtil.deserialize(s.getString("lobby-spawn"));

        ConfigurationSection teams = s.getConfigurationSection("teams");
        if (teams != null) {
            for (String key : teams.getKeys(false)) {
                TeamColor color = TeamColor.fromString(key);
                if (color == null) continue;
                Location spawn = LocationUtil.deserialize(teams.getString(key + ".spawn"));
                Location bed = LocationUtil.deserialize(teams.getString(key + ".bed"));
                if (spawn != null) map.teamSpawns.put(color, spawn);
                if (bed != null) map.teamBeds.put(color, bed);
            }
        }

        for (Map<?, ?> m : s.getMapList("generators")) {
            GeneratorType type = GeneratorType.fromString(String.valueOf(m.get("type")));
            Location loc = LocationUtil.deserialize(String.valueOf(m.get("loc")));
            TeamColor team = m.get("team") != null ? TeamColor.fromString(String.valueOf(m.get("team"))) : null;
            if (type != null && loc != null) map.generators.add(new GeneratorPoint(type, loc, team));
        }

        for (Map<?, ?> m : s.getMapList("npcs")) {
            NpcPoint.NpcType type;
            try {
                type = NpcPoint.NpcType.valueOf(String.valueOf(m.get("type")).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                continue;
            }
            Location loc = LocationUtil.deserialize(String.valueOf(m.get("loc")));
            if (loc != null) map.npcs.add(new NpcPoint(type, loc));
        }
        return map;
    }

    public void save() {
        // Clear legacy top-level keys if present
        cfg.set("lobby-spawn", null);
        cfg.set("teams", null);
        cfg.set("generators", null);
        cfg.set("npcs", null);

        cfg.set("current-map", currentMapId);
        cfg.set("maps", null);
        for (MapConfig map : maps.values()) {
            String base = "maps." + map.id + ".";
            cfg.set(base + "lobby-spawn", LocationUtil.serialize(map.lobbySpawn));
            for (TeamColor color : TeamColor.values()) {
                if (map.teamSpawns.containsKey(color)) {
                    cfg.set(base + "teams." + color.name() + ".spawn", LocationUtil.serialize(map.teamSpawns.get(color)));
                }
                if (map.teamBeds.containsKey(color)) {
                    cfg.set(base + "teams." + color.name() + ".bed", LocationUtil.serialize(map.teamBeds.get(color)));
                }
            }
            List<Map<String, Object>> gens = new ArrayList<>();
            for (GeneratorPoint g : map.generators) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", g.type.name());
                m.put("loc", LocationUtil.serialize(g.location));
                if (g.team != null) m.put("team", g.team.name());
                gens.add(m);
            }
            cfg.set(base + "generators", gens);

            List<Map<String, Object>> npcList = new ArrayList<>();
            for (NpcPoint n : map.npcs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", n.type.name());
                m.put("loc", LocationUtil.serialize(n.location));
                npcList.add(m);
            }
            cfg.set(base + "npcs", npcList);
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save setup.yml: " + e.getMessage());
        }
    }

    // --- Map management ---
    private MapConfig current() {
        MapConfig m = maps.get(currentMapId);
        if (m == null) {
            m = new MapConfig("default");
            maps.put("default", m);
            currentMapId = "default";
        }
        return m;
    }

    public String getCurrentMapId() { return currentMapId; }
    public Set<String> getMapIds() { return maps.keySet(); }
    public boolean hasMap(String id) { return maps.containsKey(id); }

    /** Create a new (empty) map and switch to it. Returns false if it already exists. */
    public boolean createMap(String id) {
        if (maps.containsKey(id)) return false;
        maps.put(id, new MapConfig(id));
        currentMapId = id;
        save();
        return true;
    }

    /** Switch the current/edited/active map. Returns false if the id is unknown. */
    public boolean switchMap(String id) {
        if (!maps.containsKey(id)) return false;
        currentMapId = id;
        save();
        return true;
    }

    /** Delete a map. Returns false if unknown or it is the only map left. */
    public boolean deleteMap(String id) {
        if (!maps.containsKey(id) || maps.size() <= 1) return false;
        maps.remove(id);
        if (id.equals(currentMapId)) currentMapId = maps.keySet().iterator().next();
        save();
        return true;
    }

    // --- Mutators (operate on the current map) ---
    public void setLobbySpawn(Location loc) { current().lobbySpawn = loc; }
    public void setTeamSpawn(TeamColor color, Location loc) { current().teamSpawns.put(color, loc); }
    public void setTeamBed(TeamColor color, Location loc) { current().teamBeds.put(color, loc); }
    public void addGenerator(GeneratorPoint g) { current().generators.add(g); }
    public void addNpc(NpcPoint n) { current().npcs.add(n); }

    public void removeTeamSpawn(TeamColor color) { current().teamSpawns.remove(color); }
    public void removeTeamBed(TeamColor color) { current().teamBeds.remove(color); }

    // --- Accessors ---
    public String getWorldName() { return worldName; }
    public Location getLobbySpawn() { return current().lobbySpawn; }
    public Map<TeamColor, Location> getTeamSpawns() { return current().teamSpawns; }
    public Map<TeamColor, Location> getTeamBeds() { return current().teamBeds; }
    public List<GeneratorPoint> getGenerators() { return current().generators; }
    public List<NpcPoint> getNpcs() { return current().npcs; }

    public int getDiamondTier2Min() { return diamondTier2Min; }
    public int getDiamondTier3Min() { return diamondTier3Min; }
    public int getBedDestructionMin() { return bedDestructionMin; }
    public int getSuddenDeathMin() { return suddenDeathMin; }
    public int getGameOverMin() { return gameOverMin; }

    public int getMinPlayers() { return minPlayers; }
    public int getMaxTeams() { return maxTeams; }
    public int getTeamSize() { return teamSize; }
    public int getLobbyCountdown() { return lobbyCountdown; }
    public int getRespawnSeconds() { return respawnSeconds; }
    public int getRespawnInvulnSeconds() { return respawnInvulnSeconds; }
    public int getDisconnectGraceSeconds() { return disconnectGraceSeconds; }
    public int getSharpnessCap() { return sharpnessCap; }

    /** Teams that have both a spawn and a bed configured on the current map. */
    public Set<TeamColor> configuredTeams() {
        Set<TeamColor> set = EnumSet.noneOf(TeamColor.class);
        MapConfig m = current();
        for (TeamColor c : TeamColor.values()) {
            if (m.teamSpawns.containsKey(c) && m.teamBeds.containsKey(c)) set.add(c);
        }
        return set;
    }
}

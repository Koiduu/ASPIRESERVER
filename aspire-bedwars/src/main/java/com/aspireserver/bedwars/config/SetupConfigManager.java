package com.aspireserver.bedwars.config;

import com.aspireserver.bedwars.AspireBedwars;
import com.aspireserver.bedwars.generator.GeneratorType;
import com.aspireserver.bedwars.team.TeamColor;
import com.aspireserver.bedwars.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Stores and persists all map setup: world, lobby spawn, per-team bed+spawn,
 * generators, and shop/upgrade NPC locations. Backed by setup.yml.
 */
public class SetupConfigManager {

    private final AspireBedwars plugin;
    private File file;
    private YamlConfiguration cfg;

    private String worldName;
    private Location lobbySpawn;
    private final Map<TeamColor, Location> teamSpawns = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, Location> teamBeds = new EnumMap<>(TeamColor.class);
    private final List<GeneratorPoint> generators = new ArrayList<>();
    private final List<NpcPoint> npcs = new ArrayList<>();

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

        loadSetup();
    }

    private void loadSetup() {
        file = new File(plugin.getDataFolder(), "setup.yml");
        cfg = YamlConfiguration.loadConfiguration(file);

        teamSpawns.clear();
        teamBeds.clear();
        generators.clear();
        npcs.clear();

        lobbySpawn = LocationUtil.deserialize(cfg.getString("lobby-spawn"));

        ConfigurationSection teams = cfg.getConfigurationSection("teams");
        if (teams != null) {
            for (String key : teams.getKeys(false)) {
                TeamColor color = TeamColor.fromString(key);
                if (color == null) continue;
                Location spawn = LocationUtil.deserialize(teams.getString(key + ".spawn"));
                Location bed = LocationUtil.deserialize(teams.getString(key + ".bed"));
                if (spawn != null) teamSpawns.put(color, spawn);
                if (bed != null) teamBeds.put(color, bed);
            }
        }

        List<Map<?, ?>> gens = cfg.getMapList("generators");
        for (Map<?, ?> m : gens) {
            GeneratorType type = GeneratorType.fromString(String.valueOf(m.get("type")));
            Location loc = LocationUtil.deserialize(String.valueOf(m.get("loc")));
            TeamColor team = m.get("team") != null ? TeamColor.fromString(String.valueOf(m.get("team"))) : null;
            if (type != null && loc != null) generators.add(new GeneratorPoint(type, loc, team));
        }

        List<Map<?, ?>> npcList = cfg.getMapList("npcs");
        for (Map<?, ?> m : npcList) {
            NpcPoint.NpcType type;
            try {
                type = NpcPoint.NpcType.valueOf(String.valueOf(m.get("type")).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                continue;
            }
            Location loc = LocationUtil.deserialize(String.valueOf(m.get("loc")));
            if (loc != null) npcs.add(new NpcPoint(type, loc));
        }
    }

    public void save() {
        cfg.set("lobby-spawn", LocationUtil.serialize(lobbySpawn));

        cfg.set("teams", null);
        for (TeamColor color : TeamColor.values()) {
            if (teamSpawns.containsKey(color)) {
                cfg.set("teams." + color.name() + ".spawn", LocationUtil.serialize(teamSpawns.get(color)));
            }
            if (teamBeds.containsKey(color)) {
                cfg.set("teams." + color.name() + ".bed", LocationUtil.serialize(teamBeds.get(color)));
            }
        }

        List<Map<String, Object>> gens = new ArrayList<>();
        for (GeneratorPoint g : generators) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", g.type.name());
            m.put("loc", LocationUtil.serialize(g.location));
            if (g.team != null) m.put("team", g.team.name());
            gens.add(m);
        }
        cfg.set("generators", gens);

        List<Map<String, Object>> npcList = new ArrayList<>();
        for (NpcPoint n : npcs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", n.type.name());
            m.put("loc", LocationUtil.serialize(n.location));
            npcList.add(m);
        }
        cfg.set("npcs", npcList);

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save setup.yml: " + e.getMessage());
        }
    }

    // --- Mutators ---
    public void setLobbySpawn(Location loc) { this.lobbySpawn = loc; }
    public void setTeamSpawn(TeamColor color, Location loc) { teamSpawns.put(color, loc); }
    public void setTeamBed(TeamColor color, Location loc) { teamBeds.put(color, loc); }
    public void addGenerator(GeneratorPoint g) { generators.add(g); }
    public void addNpc(NpcPoint n) { npcs.add(n); }

    public void removeTeamSpawn(TeamColor color) { teamSpawns.remove(color); }
    public void removeTeamBed(TeamColor color) { teamBeds.remove(color); }

    // --- Accessors ---
    public String getWorldName() { return worldName; }
    public Location getLobbySpawn() { return lobbySpawn; }
    public Map<TeamColor, Location> getTeamSpawns() { return teamSpawns; }
    public Map<TeamColor, Location> getTeamBeds() { return teamBeds; }
    public List<GeneratorPoint> getGenerators() { return generators; }
    public List<NpcPoint> getNpcs() { return npcs; }

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

    /** Teams that have both a spawn and a bed configured. */
    public Set<TeamColor> configuredTeams() {
        Set<TeamColor> set = EnumSet.noneOf(TeamColor.class);
        for (TeamColor c : TeamColor.values()) {
            if (teamSpawns.containsKey(c) && teamBeds.containsKey(c)) set.add(c);
        }
        return set;
    }
}

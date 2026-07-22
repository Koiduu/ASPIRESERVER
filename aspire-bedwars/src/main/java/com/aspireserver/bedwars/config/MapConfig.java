package com.aspireserver.bedwars.config;

import com.aspireserver.bedwars.team.TeamColor;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** All setup data for a single Bedwars map layout (within the shared world). */
public class MapConfig {

    public final String id;
    public Location lobbySpawn;
    public final Map<TeamColor, Location> teamSpawns = new EnumMap<>(TeamColor.class);
    public final Map<TeamColor, Location> teamBeds = new EnumMap<>(TeamColor.class);
    public final List<GeneratorPoint> generators = new ArrayList<>();
    public final List<NpcPoint> npcs = new ArrayList<>();

    public MapConfig(String id) {
        this.id = id;
    }
}

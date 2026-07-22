package com.aspireserver.bedwars.config;

import com.aspireserver.bedwars.generator.GeneratorType;
import com.aspireserver.bedwars.team.TeamColor;
import org.bukkit.Location;

public class GeneratorPoint {
    public final GeneratorType type;
    public final Location location;
    public final TeamColor team; // null for public (diamond/emerald)

    public GeneratorPoint(GeneratorType type, Location location, TeamColor team) {
        this.type = type;
        this.location = location;
        this.team = team;
    }
}

package com.aspireserver.bedwars.generator;

import com.aspireserver.bedwars.team.TeamColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

import java.util.ArrayList;
import java.util.List;

public class Generator {
    public final GeneratorType type;
    public final Location location;
    public final TeamColor team; // null for public
    public int ticksUntilDrop;
    public final List<ArmorStand> holograms = new ArrayList<>();
    public int secondsUntilDrop;

    public Generator(GeneratorType type, Location location, TeamColor team) {
        this.type = type;
        this.location = location;
        this.team = team;
    }
}

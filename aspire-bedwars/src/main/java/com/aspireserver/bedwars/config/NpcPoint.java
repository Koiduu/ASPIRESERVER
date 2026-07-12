package com.aspireserver.bedwars.config;

import org.bukkit.Location;

public class NpcPoint {
    public enum NpcType { SHOP, UPGRADE }

    public final NpcType type;
    public final Location location;

    public NpcPoint(NpcType type, Location location) {
        this.type = type;
        this.location = location;
    }
}

package com.aspireserver.bedwars.generator;

import org.bukkit.Material;

public enum GeneratorType {
    IRON(Material.IRON_INGOT, true),
    GOLD(Material.GOLD_INGOT, true),
    DIAMOND(Material.DIAMOND, false),
    EMERALD(Material.EMERALD, false);

    private final Material material;
    private final boolean teamGenerator; // team base gen (iron/gold) vs public (diamond/emerald)

    GeneratorType(Material material, boolean teamGenerator) {
        this.material = material;
        this.teamGenerator = teamGenerator;
    }

    public Material material() { return material; }
    public boolean isTeamGenerator() { return teamGenerator; }

    public static GeneratorType fromString(String s) {
        for (GeneratorType t : values()) {
            if (t.name().equalsIgnoreCase(s)) return t;
        }
        return null;
    }
}

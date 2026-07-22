package com.aspireserver.bedwars.team;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;

public enum TeamColor {
    RED(NamedTextColor.RED, DyeColor.RED, Color.RED, Material.RED_WOOL, Material.RED_BED, Material.RED_STAINED_GLASS, Material.RED_CONCRETE),
    BLUE(NamedTextColor.BLUE, DyeColor.BLUE, Color.BLUE, Material.BLUE_WOOL, Material.BLUE_BED, Material.BLUE_STAINED_GLASS, Material.BLUE_CONCRETE),
    GREEN(NamedTextColor.GREEN, DyeColor.LIME, Color.LIME, Material.LIME_WOOL, Material.LIME_BED, Material.LIME_STAINED_GLASS, Material.LIME_CONCRETE),
    YELLOW(NamedTextColor.YELLOW, DyeColor.YELLOW, Color.YELLOW, Material.YELLOW_WOOL, Material.YELLOW_BED, Material.YELLOW_STAINED_GLASS, Material.YELLOW_CONCRETE),
    AQUA(NamedTextColor.AQUA, DyeColor.LIGHT_BLUE, Color.AQUA, Material.LIGHT_BLUE_WOOL, Material.LIGHT_BLUE_BED, Material.LIGHT_BLUE_STAINED_GLASS, Material.LIGHT_BLUE_CONCRETE),
    WHITE(NamedTextColor.WHITE, DyeColor.WHITE, Color.WHITE, Material.WHITE_WOOL, Material.WHITE_BED, Material.WHITE_STAINED_GLASS, Material.WHITE_CONCRETE),
    PINK(NamedTextColor.LIGHT_PURPLE, DyeColor.PINK, Color.fromRGB(0xF38BAA), Material.PINK_WOOL, Material.PINK_BED, Material.PINK_STAINED_GLASS, Material.PINK_CONCRETE),
    GRAY(NamedTextColor.DARK_GRAY, DyeColor.GRAY, Color.GRAY, Material.GRAY_WOOL, Material.GRAY_BED, Material.GRAY_STAINED_GLASS, Material.GRAY_CONCRETE);

    private final NamedTextColor textColor;
    private final DyeColor dyeColor;
    private final Color armorColor;
    private final Material wool;
    private final Material bed;
    private final Material glass;
    private final Material concrete;

    TeamColor(NamedTextColor textColor, DyeColor dyeColor, Color armorColor, Material wool, Material bed, Material glass, Material concrete) {
        this.textColor = textColor;
        this.dyeColor = dyeColor;
        this.armorColor = armorColor;
        this.wool = wool;
        this.bed = bed;
        this.glass = glass;
        this.concrete = concrete;
    }

    public NamedTextColor textColor() { return textColor; }
    public DyeColor dyeColor() { return dyeColor; }
    public Color armorColor() { return armorColor; }
    public Material wool() { return wool; }
    public Material bed() { return bed; }
    public Material glass() { return glass; }
    public Material concrete() { return concrete; }

    public String displayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }

    public static TeamColor fromString(String s) {
        for (TeamColor c : values()) {
            if (c.name().equalsIgnoreCase(s)) return c;
        }
        return null;
    }
}

package com.aspireserver.bedwars.shop;

import org.bukkit.Material;

public enum ShopCategory {
    QUICK_BUY("Quick Buy", Material.NETHER_STAR),
    BLOCKS("Blocks", Material.TERRACOTTA),
    MELEE("Melee", Material.GOLDEN_SWORD),
    ARMOR("Armor", Material.CHAINMAIL_BOOTS),
    TOOLS("Tools", Material.STONE_PICKAXE),
    RANGED("Ranged", Material.BOW),
    POTIONS("Potions", Material.BREWING_STAND),
    UTILITY("Utility", Material.TNT);

    private final String display;
    private final Material icon;

    ShopCategory(String display, Material icon) {
        this.display = display;
        this.icon = icon;
    }

    public String display() { return display; }
    public Material icon() { return icon; }
}

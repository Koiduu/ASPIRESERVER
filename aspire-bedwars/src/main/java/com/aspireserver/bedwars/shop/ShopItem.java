package com.aspireserver.bedwars.shop;

import org.bukkit.Material;

/**
 * A purchasable shop entry. {@code id} identifies special-handled purchases
 * (armor/tool tiers, utility items); {@code give} is the plain item handed to
 * the player for simple buys.
 */
public class ShopItem {
    public final String id;
    public final ShopCategory category;
    public final String name;
    public final Material icon;
    public final int iconAmount;
    public final Material costMaterial;
    public final int costAmount;
    public final Material giveMaterial;  // may be null for special handlers
    public final int giveAmount;

    public ShopItem(String id, ShopCategory category, String name, Material icon, int iconAmount,
                    Material costMaterial, int costAmount, Material giveMaterial, int giveAmount) {
        this.id = id;
        this.category = category;
        this.name = name;
        this.icon = icon;
        this.iconAmount = iconAmount;
        this.costMaterial = costMaterial;
        this.costAmount = costAmount;
        this.giveMaterial = giveMaterial;
        this.giveAmount = giveAmount;
    }

    public static ShopItem give(String id, ShopCategory cat, String name, Material give, int giveAmount,
                                Material cost, int costAmount) {
        return new ShopItem(id, cat, name, give, giveAmount, cost, costAmount, give, giveAmount);
    }

    public static ShopItem special(String id, ShopCategory cat, String name, Material icon, int iconAmount,
                                   Material cost, int costAmount) {
        return new ShopItem(id, cat, name, icon, iconAmount, cost, costAmount, null, 0);
    }
}

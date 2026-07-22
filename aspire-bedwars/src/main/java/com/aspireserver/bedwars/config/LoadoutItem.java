package com.aspireserver.bedwars.config;

import org.bukkit.Material;

/**
 * One entry in a custom starting loadout, given to a player on every (re)spawn
 * in addition to the base kit.
 */
public class LoadoutItem {
    public final Material material;
    public final int amount;
    public final boolean unbreakable;

    public LoadoutItem(Material material, int amount, boolean unbreakable) {
        this.material = material;
        this.amount = amount;
        this.unbreakable = unbreakable;
    }
}

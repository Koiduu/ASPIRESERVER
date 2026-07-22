package com.aspireserver.bedwars.util;

import com.aspireserver.bedwars.AspireBedwars;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Central registry of {@link NamespacedKey}s used to tag setup and shop items
 * with stable identifiers, so item identity never relies on {@link org.bukkit.Material}
 * (which can collide between different tools).
 */
public final class Keys {

    /** Setup-mode hotbar action id (bed/spawn/gen_place/npc_shop/...). */
    public static NamespacedKey SETUP_ACTION;
    /** Generator type stored on the generator placer/selector tools. */
    public static NamespacedKey GEN_TYPE;
    /** Shop entry id on a rendered shop icon. */
    public static NamespacedKey SHOP_ID;
    /** Team-upgrade action id on an upgrade icon. */
    public static NamespacedKey UPGRADE_ID;

    private Keys() {}

    public static void init(AspireBedwars plugin) {
        SETUP_ACTION = new NamespacedKey(plugin, "setup_action");
        GEN_TYPE = new NamespacedKey(plugin, "gen_type");
        SHOP_ID = new NamespacedKey(plugin, "shop_id");
        UPGRADE_ID = new NamespacedKey(plugin, "upgrade_id");
    }

    public static String read(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}

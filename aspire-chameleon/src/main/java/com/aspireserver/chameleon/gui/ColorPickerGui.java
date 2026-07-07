package com.aspireserver.chameleon.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.List;

/**
 * Interactive 9x6 color picker GUI.
 * Each slot maps to a precise RGB hex value.
 * Clicking a slot applies that color as dyed leather armor camo.
 */
public class ColorPickerGui {

    public static final String TITLE = "Color Picker";

    // 54-slot color grid — each entry is an RGB hex
    private static final String[] COLOR_MAP = {
            // Row 1: Reds & Warm tones
            "#FF0000", "#FF3300", "#FF6600", "#FF9900", "#FFCC00", "#FFFF00", "#CCFF00", "#99FF00", "#66FF00",
            // Row 2: Greens
            "#33FF00", "#00FF00", "#00FF33", "#00FF66", "#00FF99", "#00FFCC", "#00FFFF", "#00CCFF", "#0099FF",
            // Row 3: Blues
            "#0066FF", "#0033FF", "#0000FF", "#3300FF", "#6600FF", "#9900FF", "#CC00FF", "#FF00FF", "#FF00CC",
            // Row 4: Pastels
            "#FFB3B3", "#FFD9B3", "#FFFFB3", "#D9FFB3", "#B3FFB3", "#B3FFD9", "#B3FFFF", "#B3D9FF", "#B3B3FF",
            // Row 5: Earth tones & darks
            "#8B4513", "#A0522D", "#6B8E23", "#556B2F", "#2E8B57", "#008080", "#4682B4", "#483D8B", "#4B0082",
            // Row 6: Grays, black, white, materials
            "#FFFFFF", "#E0E0E0", "#C0C0C0", "#A0A0A0", "#808080", "#606060", "#404040", "#202020", "#000000"
    };

    public static void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54,
                Component.text(TITLE, NamedTextColor.LIGHT_PURPLE));

        for (int i = 0; i < COLOR_MAP.length && i < 54; i++) {
            String hex = COLOR_MAP[i];
            int rgb = Integer.parseInt(hex.substring(1), 16);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
            LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
            meta.setColor(Color.fromRGB(r, g, b));
            meta.displayName(Component.text(hex, TextColor.color(r, g, b)));
            meta.lore(List.of(
                    Component.text("R:" + r + " G:" + g + " B:" + b, NamedTextColor.GRAY),
                    Component.text("Click to apply this color", NamedTextColor.YELLOW)
            ));
            item.setItemMeta(meta);
            gui.setItem(i, item);
        }

        player.openInventory(gui);
    }

    /**
     * Gets the hex color for a given slot index.
     * Returns null if slot is out of range.
     */
    public static String getHexForSlot(int slot) {
        if (slot < 0 || slot >= COLOR_MAP.length) return null;
        return COLOR_MAP[slot];
    }
}

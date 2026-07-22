package com.aspireserver.chameleon.game;

import com.aspireserver.chameleon.MecchaChameleon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dyed leather armor camo for hiders.
 * Uses Custom Model Data on leather armor + dynamic RGB dyeing
 * so players appear as solid colored blocks when wearing the set.
 */
public class CamoManager {

    private static final int CUSTOM_MODEL_DATA = 10001;
    private final MecchaChameleon plugin;
    private final Map<UUID, Color> playerColors = new ConcurrentHashMap<>();

    public CamoManager(MecchaChameleon plugin) {
        this.plugin = plugin;
    }

    public void applyColor(Player player, Color color) {
        playerColors.put(player.getUniqueId(), color);

        // Full leather armor set dyed to the chosen color
        player.getInventory().setHelmet(createArmorPiece(Material.LEATHER_HELMET, color, "Camo Helmet"));
        player.getInventory().setChestplate(createArmorPiece(Material.LEATHER_CHESTPLATE, color, "Camo Chestplate"));
        player.getInventory().setLeggings(createArmorPiece(Material.LEATHER_LEGGINGS, color, "Camo Leggings"));
        player.getInventory().setBoots(createArmorPiece(Material.LEATHER_BOOTS, color, "Camo Boots"));

        String hex = String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
        player.sendMessage(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                .append(Component.text("Camo color set to " + hex, NamedTextColor.AQUA)));
    }

    public void applyColorFromHex(Player player, String hex) {
        try {
            int rgb = Integer.parseInt(hex.replace("#", ""), 16);
            Color color = Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            applyColor(player, color);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid color!", NamedTextColor.RED));
        }
    }

    public void clearCamo(Player player) {
        playerColors.remove(player.getUniqueId());
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
    }

    public Color getPlayerColor(UUID uuid) {
        return playerColors.get(uuid);
    }

    private ItemStack createArmorPiece(Material material, Color color, String name) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        meta.displayName(Component.text(name, NamedTextColor.GREEN));
        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        item.setItemMeta(meta);
        return item;
    }
}

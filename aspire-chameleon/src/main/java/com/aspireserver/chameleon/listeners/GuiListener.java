package com.aspireserver.chameleon.listeners;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.config.ConfigManager;
import com.aspireserver.chameleon.game.GameManager;
import com.aspireserver.chameleon.game.PlayerRole;
import com.aspireserver.chameleon.gui.ColorPickerGui;
import com.aspireserver.chameleon.skin.SkinCache.CachedSkin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class GuiListener implements Listener {

    private final MecchaChameleon plugin;

    public GuiListener(MecchaChameleon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component title = event.getView().title();
        String titleStr = PlainTextComponentSerializer.plainText().serialize(title);

        switch (titleStr) {
            case "Chameleon Voting" -> {
                event.setCancelled(true);
                plugin.getLobbyManager().handleVote(player, event.getRawSlot());
            }
            case "Camo Skin Selection" -> {
                event.setCancelled(true);
                handleSkinSelection(player, event);
            }
            case "Color Picker" -> {
                event.setCancelled(true);
                handleColorPicker(player, event);
            }
            default -> {
                if (titleStr.startsWith("Skins: ")) {
                    event.setCancelled(true);
                    handleSkinsAdmin(player, event, titleStr.substring("Skins: ".length()));
                }
            }
        }
    }

    private void handleSkinSelection(Player player, InventoryClickEvent event) {
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameActive()) {
            player.closeInventory();
            return;
        }
        if (gm.getRole(player.getUniqueId()) != PlayerRole.HIDER) {
            player.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        String displayName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // Check if it's the "Color Picker" button
        if ("Color Picker".equals(displayName)) {
            player.closeInventory();
            ColorPickerGui.open(player);
            return;
        }

        List<Component> lore = meta.lore();
        if (lore == null || lore.size() < 2) return;

        String loreLine = PlainTextComponentSerializer.plainText().serialize(lore.get(1));
        if (!loreLine.startsWith("UUID: ")) return;
        String uuid = loreLine.substring(6).trim();

        CachedSkin skin = plugin.getSkinCache().getSkin(uuid);
        if (skin == null) {
            player.sendMessage(Component.text("Skin not cached yet!", NamedTextColor.RED));
            return;
        }

        gm.applySkin(player, skin);
        player.closeInventory();
    }

    private void handleColorPicker(Player player, InventoryClickEvent event) {
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameActive() || gm.getRole(player.getUniqueId()) != PlayerRole.HIDER) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        String hex = ColorPickerGui.getHexForSlot(slot);
        if (hex == null) return;

        plugin.getCamoManager().applyColorFromHex(player, hex);
        player.closeInventory();
    }

    private void handleSkinsAdmin(Player player, InventoryClickEvent event, String mapId) {
        if (!player.hasPermission("chameleon.admin")) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        String displayName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        if ("+ Add Skin".equals(displayName)) {
            player.closeInventory();
            player.sendMessage(Component.text("Use: /chameleon map addskin " + mapId + " <name> <uuid>", NamedTextColor.AQUA));
            return;
        }

        List<Component> lore = meta.lore();
        if (lore == null) return;
        boolean hasRemove = false;
        for (Component line : lore) {
            if (PlainTextComponentSerializer.plainText().serialize(line).contains("REMOVE")) {
                hasRemove = true;
                break;
            }
        }
        if (!hasRemove) return;

        if (plugin.getConfigManager().removeSkin(mapId, displayName)) {
            player.sendMessage(Component.text("[Chameleon] Removed skin '" + displayName + "'", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Failed to remove.", NamedTextColor.RED));
        }
        player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String titleStr = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if ("Chameleon Voting".equals(titleStr) || "Camo Skin Selection".equals(titleStr)
                || "Color Picker".equals(titleStr) || titleStr.startsWith("Skins: ")) {
            event.setCancelled(true);
        }
    }
}

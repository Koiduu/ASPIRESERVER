package com.aspireserver.chameleon.listeners;

import com.aspireserver.chameleon.AspireChameleon;
import com.aspireserver.chameleon.config.ConfigManager;
import com.aspireserver.chameleon.config.ConfigManager.SkinEntry;
import com.aspireserver.chameleon.game.GameManager;
import com.aspireserver.chameleon.skin.SkinCache;
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

    private final GameManager gameManager;
    private final SkinCache skinCache;

    public GuiListener(GameManager gameManager, SkinCache skinCache) {
        this.gameManager = gameManager;
        this.skinCache = skinCache;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component title = event.getView().title();
        String titleStr = PlainTextComponentSerializer.plainText().serialize(title);

        // Handle Camo Skin Selection (in-game picking)
        if ("Camo Skin Selection".equals(titleStr)) {
            event.setCancelled(true);
            handleSkinSelection(player, event);
            return;
        }

        // Handle Skins admin GUI (remove skins)
        if (titleStr.startsWith("Skins: ")) {
            event.setCancelled(true);
            handleSkinsAdmin(player, event, titleStr.substring("Skins: ".length()));
        }
    }

    private void handleSkinSelection(Player player, InventoryClickEvent event) {
        if (!gameManager.isGameActive() || !gameManager.isGracePeriodActive()) {
            player.closeInventory();
            player.sendMessage(Component.text("Grace period has ended!", NamedTextColor.RED));
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null || lore.size() < 2) return;

        // Extract UUID from lore line 2
        String loreLine = PlainTextComponentSerializer.plainText().serialize(lore.get(1));
        if (!loreLine.startsWith("UUID: ")) return;
        String uuid = loreLine.substring(6).trim();

        CachedSkin skin = skinCache.getSkin(uuid);
        if (skin == null) {
            player.sendMessage(Component.text("Skin not cached yet! Try again in a moment.", NamedTextColor.RED));
            return;
        }

        gameManager.applySkin(player, skin);
        player.closeInventory();
    }

    private void handleSkinsAdmin(Player player, InventoryClickEvent event, String mapId) {
        if (!player.hasPermission("aspire.chameleon.admin")) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        String displayName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // If clicking "+ Add Skin" button, just show command help
        if ("+ Add Skin".equals(displayName)) {
            player.closeInventory();
            player.sendMessage(Component.text("Use: /camo addskin " + mapId + " <name> <uuid>", NamedTextColor.AQUA));
            player.sendMessage(Component.text("  name = display name (use _ for spaces)", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  uuid = UUID of Minecraft account with that skin", NamedTextColor.GRAY));
            return;
        }

        // Otherwise it's a skin entry — remove it on click
        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) return;

        // Confirm it has the "Click to REMOVE" lore
        boolean hasRemoveLore = false;
        for (Component line : lore) {
            String text = PlainTextComponentSerializer.plainText().serialize(line);
            if (text.contains("REMOVE")) {
                hasRemoveLore = true;
                break;
            }
        }
        if (!hasRemoveLore) return;

        ConfigManager cfg = AspireChameleon.getInstance().getConfigManager();
        if (cfg.removeSkin(mapId, displayName)) {
            player.sendMessage(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                    .append(Component.text("Removed skin '" + displayName + "' from map '" + mapId + "'.", NamedTextColor.YELLOW)));
        } else {
            player.sendMessage(Component.text("Failed to remove skin.", NamedTextColor.RED));
        }
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Component title = event.getView().title();
        String titleStr = PlainTextComponentSerializer.plainText().serialize(title);
        if ("Camo Skin Selection".equals(titleStr) || titleStr.startsWith("Skins: ")) {
            event.setCancelled(true);
        }
    }
}

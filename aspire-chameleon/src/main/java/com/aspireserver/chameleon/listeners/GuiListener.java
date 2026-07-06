package com.aspireserver.chameleon.listeners;

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
        if (!"Camo Skin Selection".equals(titleStr)) return;

        event.setCancelled(true);

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

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Component title = event.getView().title();
        String titleStr = PlainTextComponentSerializer.plainText().serialize(title);
        if ("Camo Skin Selection".equals(titleStr)) {
            event.setCancelled(true);
        }
    }
}

package com.aspireserver.smp.listeners;

import com.aspireserver.smp.commands.ShopCommand;
import com.aspireserver.smp.shop.ShopManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class ShopListener implements Listener {

    private final ShopManager shopManager;
    private final ShopCommand shopCommand;

    public ShopListener(ShopManager shopManager, ShopCommand shopCommand) {
        this.shopManager = shopManager;
        this.shopCommand = shopCommand;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component title = event.getView().title();
        String titleText = PlainTextComponentSerializer.plainText().serialize(title);
        if (!titleText.startsWith("Shop (Page")) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        // Extract page number from title
        int page = 0;
        try {
            String pageStr = titleText.replace("Shop (Page ", "").replace(")", "");
            page = Integer.parseInt(pageStr) - 1;
        } catch (NumberFormatException ignored) {}

        int globalIndex = page * 45 + slot;
        if (slot < 45) {
            shopCommand.handleBuy(player, globalIndex);
            player.closeInventory();
        }
    }
}

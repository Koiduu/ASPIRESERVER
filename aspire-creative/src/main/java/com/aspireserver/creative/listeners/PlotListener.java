package com.aspireserver.creative.listeners;

import com.aspireserver.creative.plot.CreativePlot;
import com.aspireserver.creative.plot.PlotManager;
import com.aspireserver.creative.plot.PlotTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class PlotListener implements Listener {

    private final PlotManager plotManager;

    public PlotListener(PlotManager plotManager) {
        this.plotManager = plotManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("aspire.admin.bypass")) return;

        String worldName = player.getWorld().getName();
        if (!worldName.startsWith("creative_")) return;

        CreativePlot plot = plotManager.getPlotAt(event.getBlock().getLocation());
        if (plot == null || !plot.canAccess(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("You cannot build here!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("aspire.admin.bypass")) return;

        String worldName = player.getWorld().getName();
        if (!worldName.startsWith("creative_")) return;

        CreativePlot plot = plotManager.getPlotAt(event.getBlock().getLocation());
        if (plot == null || !plot.canAccess(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("You cannot build here!", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component title = event.getView().title();
        String titleText = PlainTextComponentSerializer.plainText().serialize(title);
        if (!titleText.equals("Plot Menu")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        int slot = event.getSlot();
        PlotTier tier = switch (slot) {
            case 10 -> PlotTier.SMALL;
            case 12 -> PlotTier.MEDIUM;
            case 14 -> PlotTier.LARGE;
            default -> null;
        };

        if (tier != null) {
            player.closeInventory();
            player.performCommand("plot claim " + tier.name().toLowerCase());
        }
    }
}

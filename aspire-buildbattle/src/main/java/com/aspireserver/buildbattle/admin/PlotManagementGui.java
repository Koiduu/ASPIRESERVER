package com.aspireserver.buildbattle.admin;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.arena.Arena;
import com.aspireserver.buildbattle.plot.PlotRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlotManagementGui implements Listener {

    private final AspireBuildBattle plugin;
    private final Map<UUID, String> openArenas = new HashMap<>();

    public static final String GUI_TITLE = "Plot Manager";
    public static final String EDIT_TITLE = "Edit Plot";

    public PlotManagementGui(AspireBuildBattle plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player player, Arena arena) {
        int plotCount = arena.getPlots().size();
        int size = Math.min(54, ((plotCount / 9) + 1) * 9);
        if (size < 9) size = 9;

        Inventory gui = Bukkit.createInventory(null, size,
            Component.text(GUI_TITLE + " - " + arena.getId(), NamedTextColor.DARK_PURPLE));

        for (int i = 0; i < plotCount && i < size; i++) {
            PlotRegion plot = arena.getPlots().get(i);
            ItemStack item = new ItemStack(Material.GRASS_BLOCK);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Plot #" + i, NamedTextColor.GREEN, TextDecoration.BOLD));
            meta.lore(List.of(
                Component.text("World: " + plot.getWorldName(), NamedTextColor.GRAY),
                Component.text("Min: " + plot.getMinX() + ", " + plot.getMinY() + ", " + plot.getMinZ(), NamedTextColor.GRAY),
                Component.text("Max: " + plot.getMaxX() + ", " + plot.getMaxY() + ", " + plot.getMaxZ(), NamedTextColor.GRAY),
                Component.empty(),
                Component.text("LEFT-CLICK to DELETE", NamedTextColor.RED),
                Component.text("RIGHT-CLICK to TELEPORT", NamedTextColor.AQUA)
            ));
            item.setItemMeta(meta);
            gui.setItem(i, item);
        }

        openArenas.put(player.getUniqueId(), arena.getId());
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(event.getView().title());

        if (!title.startsWith(GUI_TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String arenaId = openArenas.get(player.getUniqueId());
        if (arenaId == null) return;

        Arena arena = plugin.getArenaManager().getArena(arenaId);
        if (arena == null) return;

        int slot = event.getSlot();
        if (slot < 0 || slot >= arena.getPlots().size()) return;

        if (event.getClick() == ClickType.LEFT) {
            PlotRegion plot = arena.getPlots().get(slot);
            arena.removePlot(slot);
            plugin.getArenaManager().saveArena(arena);
            player.sendMessage(Component.text("Deleted plot #" + slot + " from " + arenaId + ". (" + arena.getPlots().size() + " remaining)", NamedTextColor.GREEN));
            player.closeInventory();
            // Reopen with updated list
            Bukkit.getScheduler().runTaskLater(plugin, () -> openGui(player, arena), 2L);
        } else if (event.getClick() == ClickType.RIGHT) {
            PlotRegion plot = arena.getPlots().get(slot);
            player.closeInventory();
            player.teleport(plot.getSafeCenter());
            player.sendMessage(Component.text("Teleported to plot #" + slot, NamedTextColor.AQUA));
        }
    }
}

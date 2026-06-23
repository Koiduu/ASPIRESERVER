package com.aspireserver.buildbattle.plot;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.game.GameSession;
import com.aspireserver.buildbattle.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class FloorManager implements Listener {

    private final AspireBuildBattle plugin;

    public static final String FLOOR_GUI_TITLE = "Drop a Block \u2192 Set Floor";

    public FloorManager(AspireBuildBattle plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.NETHER_STAR) return;
        if (!item.hasItemMeta()) return;

        Component displayName = item.getItemMeta().displayName();
        if (displayName == null) return;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(displayName);
        if (!name.equals("Plot Customizer")) return;

        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session == null || session.getState() != GameState.BUILDING) return;

        event.setCancelled(true);
        openFloorGui(player);
    }

    private void openFloorGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9,
            Component.text(FLOOR_GUI_TITLE, NamedTextColor.LIGHT_PURPLE));

        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(Component.text("Floor Customizer", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        meta.lore(List.of(
            Component.text("Click any block from your", NamedTextColor.GRAY),
            Component.text("inventory to set as floor!", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("Click a block in your hotbar/inv", NamedTextColor.YELLOW),
            Component.text("while this GUI is open.", NamedTextColor.YELLOW)
        ));
        info.setItemMeta(meta);
        gui.setItem(4, info);

        player.openInventory(gui);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(event.getView().title());

        if (!title.equals(FLOOR_GUI_TITLE)) return;

        // If clicking in the top inventory (the GUI slots), cancel always
        if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            // Check if player has a block on cursor and clicked into the GUI
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && cursor.getType().isBlock()) {
                event.setCancelled(true);
                applyFloor(player, cursor.getType());
                return;
            }
            event.setCancelled(true);
            return;
        }

        // Player clicked in their own inventory (bottom) while GUI is open
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && !clicked.getType().isAir() && clicked.getType().isBlock()) {
            event.setCancelled(true);
            applyFloor(player, clicked.getType());
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(event.getView().title());

        if (!title.equals(FLOOR_GUI_TITLE)) return;

        event.setCancelled(true);

        // Check if the dragged item is a block
        ItemStack dragged = event.getOldCursor();
        if (dragged != null && !dragged.getType().isAir() && dragged.getType().isBlock()) {
            // Check if any of the drag slots are in the top inventory
            int topSize = event.getView().getTopInventory().getSize();
            for (int slot : event.getRawSlots()) {
                if (slot < topSize) {
                    applyFloor(player, dragged.getType());
                    return;
                }
            }
        }
    }

    private void applyFloor(Player player, Material floorMaterial) {
        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session == null || session.getState() != GameState.BUILDING) {
            player.closeInventory();
            return;
        }

        PlotRegion plot = session.getPlayerPlot(player.getUniqueId());
        if (plot == null) {
            player.closeInventory();
            return;
        }

        setPlotFloor(plot, floorMaterial);
        player.sendMessage(Component.text("Floor changed to " + formatMaterialName(floorMaterial) + "!", NamedTextColor.GREEN));
        player.closeInventory();
    }

    private void setPlotFloor(PlotRegion plot, Material material) {
        World world = Bukkit.getWorld(plot.getWorldName());
        if (world == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            int floorY = plot.getMinY();
            for (int x = plot.getMinX() + 1; x < plot.getMaxX(); x++) {
                for (int z = plot.getMinZ() + 1; z < plot.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, floorY, z);
                    block.setType(material, false);
                }
            }
        });
    }

    private String formatMaterialName(Material material) {
        String name = material.name().replace('_', ' ');
        StringBuilder formatted = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                formatted.append(' ');
                capitalize = true;
            } else if (capitalize) {
                formatted.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                formatted.append(Character.toLowerCase(c));
            }
        }
        return formatted.toString();
    }
}

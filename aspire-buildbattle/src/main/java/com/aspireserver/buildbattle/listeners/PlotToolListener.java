package com.aspireserver.buildbattle.listeners;

import com.aspireserver.buildbattle.AspireBuildBattle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlotToolListener implements Listener {

    private final AspireBuildBattle plugin;
    private final Map<UUID, Location> pos1Map;
    private final Map<UUID, Location> pos2Map;

    public PlotToolListener(AspireBuildBattle plugin) {
        this.plugin = plugin;
        this.pos1Map = new HashMap<>();
        this.pos2Map = new HashMap<>();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("aspire.buildbattle.admin")) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.CARROT) return;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            event.setCancelled(true);
            Location loc = event.getClickedBlock().getLocation();
            pos1Map.put(player.getUniqueId(), loc);
            player.sendMessage(Component.text("Pos 1 set: ", NamedTextColor.GREEN)
                .append(Component.text(formatLoc(loc), NamedTextColor.GRAY)));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            event.setCancelled(true);
            Location loc = event.getClickedBlock().getLocation();
            pos2Map.put(player.getUniqueId(), loc);
            player.sendMessage(Component.text("Pos 2 set: ", NamedTextColor.GREEN)
                .append(Component.text(formatLoc(loc), NamedTextColor.GRAY)));
        }
    }

    public Location getPos1(UUID player) {
        return pos1Map.get(player);
    }

    public Location getPos2(UUID player) {
        return pos2Map.get(player);
    }

    public void clearPositions(UUID player) {
        pos1Map.remove(player);
        pos2Map.remove(player);
    }

    private String formatLoc(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}

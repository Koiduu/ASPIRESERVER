package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import com.aspireserver.smp.graveyard.Graveyard;
import com.aspireserver.smp.graveyard.GraveyardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.HashMap;

public class DeathListener implements Listener {

    private final AspireSMP plugin;

    public DeathListener(AspireSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ItemStack[] inventory = player.getInventory().getContents().clone();

        Graveyard gy = plugin.getGraveyardManager().createGraveyard(
            player.getUniqueId(), player.getLocation(), inventory);

        if (gy != null) {
            event.getDrops().clear();
            event.setKeepInventory(false);

            Location loc = gy.getLocation();
            player.sendMessage(Component.text("Your items are in a graveyard at: ", NamedTextColor.GOLD)
                .append(Component.text(loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ(), NamedTextColor.YELLOW)));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.SOUL_LANTERN) return;

        Player player = event.getPlayer();
        Location blockLoc = event.getClickedBlock().getLocation();

        Graveyard graveyard = plugin.getGraveyardManager().getGraveyardAt(blockLoc, player.getUniqueId());
        if (graveyard == null) return;

        event.setCancelled(true);

        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(
            graveyard.getItems().toArray(new ItemStack[0]));

        if (!overflow.isEmpty()) {
            for (ItemStack item : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        plugin.getGraveyardManager().removeGraveyard(graveyard);
        player.sendMessage(Component.text("Items retrieved from graveyard!", NamedTextColor.GREEN));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.SOUL_LANTERN) return;

        Location blockLoc = event.getBlock().getLocation();
        // Check if this soul lantern is a graveyard
        for (var entry : getAllGraveyards()) {
            Location gyLoc = entry.getLocation();
            if (gyLoc.getWorld().equals(blockLoc.getWorld())
                && gyLoc.getBlockX() == blockLoc.getBlockX()
                && gyLoc.getBlockY() == blockLoc.getBlockY()
                && gyLoc.getBlockZ() == blockLoc.getBlockZ()) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(Component.text(
                    "This is a graveyard! Right-click to retrieve items.", NamedTextColor.RED));
                return;
            }
        }
    }

    private java.util.List<Graveyard> getAllGraveyards() {
        java.util.List<Graveyard> all = new java.util.ArrayList<>();
        // Access via GraveyardManager — iterate all graveyards
        plugin.getGraveyardManager().forEachGraveyard(all::add);
        return all;
    }
}

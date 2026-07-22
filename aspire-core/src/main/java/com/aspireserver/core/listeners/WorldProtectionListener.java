package com.aspireserver.core.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

public class WorldProtectionListener implements Listener {

    private final JavaPlugin plugin;

    public WorldProtectionListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isSmpWorld(World world) {
        String smpWorld = plugin.getConfig().getString("smp-world", "");
        if (!smpWorld.isEmpty() && world.getName().equalsIgnoreCase(smpWorld)) {
            return true;
        }
        var smpPlugin = Bukkit.getPluginManager().getPlugin("AspireSMP");
        if (smpPlugin != null && smpPlugin.isEnabled()) {
            String smpWorldName = smpPlugin.getConfig().getString("smp-world", "");
            if (!smpWorldName.isEmpty() && world.getName().equalsIgnoreCase(smpWorldName)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> getProtectedWorlds() {
        return Set.copyOf(plugin.getConfig().getStringList("protected-worlds"));
    }

    private boolean isProtectedWorld(World world) {
        Set<String> protectedWorlds = getProtectedWorlds();
        if (!protectedWorlds.isEmpty()) {
            return protectedWorlds.contains(world.getName());
        }
        return !isSmpWorld(world);
    }

    // --- TNT Protection ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onTntPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.TNT) return;
        if (isProtectedWorld(event.getBlock().getWorld())) {
            if (!event.getPlayer().hasPermission("aspire.admin.bypass")) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text(
                    "TNT is disabled in this world!", net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (isProtectedWorld(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (isProtectedWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    // --- Dragon Egg Protection ---
    // Use LOWEST priority to fire FIRST, no bypass check (egg never teleports in protected worlds)

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDragonEggInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_BLOCK
                && event.getAction() != Action.PHYSICAL) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.DRAGON_EGG) return;

        if (isProtectedWorld(block.getWorld())) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text(
                "Dragon eggs cannot be used here!", net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDragonEggTeleport(BlockFromToEvent event) {
        if (event.getBlock().getType() != Material.DRAGON_EGG) return;
        if (isProtectedWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }
}

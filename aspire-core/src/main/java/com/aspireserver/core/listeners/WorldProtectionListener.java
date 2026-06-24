package com.aspireserver.core.listeners;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
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
        return !smpWorld.isEmpty() && world.getName().equalsIgnoreCase(smpWorld);
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onDragonEggInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.DRAGON_EGG) return;

        if (isProtectedWorld(block.getWorld())) {
            if (!event.getPlayer().hasPermission("aspire.admin.bypass")) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text(
                    "Dragon eggs cannot be used here!", net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }
    }
}

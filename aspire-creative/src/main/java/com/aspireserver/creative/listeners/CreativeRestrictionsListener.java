package com.aspireserver.creative.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Set;

public class CreativeRestrictionsListener implements Listener {

    private static final Set<Material> REDSTONE_BLOCKS = Set.of(
        Material.REDSTONE, Material.REDSTONE_BLOCK, Material.REDSTONE_TORCH,
        Material.REDSTONE_WALL_TORCH, Material.REDSTONE_WIRE,
        Material.REPEATER, Material.COMPARATOR, Material.PISTON,
        Material.STICKY_PISTON, Material.OBSERVER, Material.DROPPER,
        Material.DISPENSER, Material.HOPPER, Material.DAYLIGHT_DETECTOR,
        Material.LEVER, Material.TRIPWIRE_HOOK, Material.TRIPWIRE,
        Material.TARGET, Material.SCULK_SENSOR, Material.CALIBRATED_SCULK_SENSOR
    );

    private boolean isCreativeWorld(String worldName) {
        return worldName.startsWith("creative_");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isCreativeWorld(event.getBlock().getWorld().getName())) return;
        if (event.getPlayer().hasPermission("aspire.admin.bypass")) return;

        Material type = event.getBlock().getType();
        if (REDSTONE_BLOCKS.contains(type) || type.name().contains("BUTTON")
                || type.name().contains("PRESSURE_PLATE")) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Redstone is disabled in Creative!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRedstone(BlockRedstoneEvent event) {
        if (!isCreativeWorld(event.getBlock().getWorld().getName())) return;
        event.setNewCurrent(0);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (!isCreativeWorld(event.getClickedBlock().getWorld().getName())) return;
        if (event.getPlayer().hasPermission("aspire.admin.bypass")) return;

        Material type = event.getClickedBlock().getType();
        if (type == Material.LEVER || type.name().contains("BUTTON")
                || type.name().contains("PRESSURE_PLATE")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isCreativeWorld(event.getEntity().getWorld().getName())) return;
        // Block all creature/entity spawns (mobs, armor stands, etc.)
        // Paintings and item frames use HangingPlaceEvent, not CreatureSpawnEvent, so they're unaffected
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnEggUse(PlayerInteractEvent event) {
        if (event.getItem() == null) return;
        if (!isCreativeWorld(event.getPlayer().getWorld().getName())) return;
        if (event.getPlayer().hasPermission("aspire.admin.bypass")) return;

        String itemName = event.getItem().getType().name();
        if (itemName.endsWith("_SPAWN_EGG") || event.getItem().getType() == Material.ARMOR_STAND) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Entity spawning is disabled in Creative!", NamedTextColor.RED));
        }
    }
}

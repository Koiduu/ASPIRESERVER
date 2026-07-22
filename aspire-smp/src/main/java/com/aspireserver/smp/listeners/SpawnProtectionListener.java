package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class SpawnProtectionListener implements Listener {

    private final AspireSMP plugin;

    public SpawnProtectionListener(AspireSMP plugin) {
        this.plugin = plugin;
    }

    private boolean isSmpWorld(World world) {
        String smpWorld = plugin.getConfig().getString("smp-world", "");
        return !smpWorld.isEmpty() && world.getName().equalsIgnoreCase(smpWorld);
    }

    private boolean isSpawnChunk(Location loc) {
        if (!isSmpWorld(loc.getWorld())) return false;
        World world = loc.getWorld();
        Location spawn = world.getSpawnLocation();
        Chunk spawnChunk = spawn.getChunk();
        Chunk blockChunk = loc.getChunk();
        return spawnChunk.getX() == blockChunk.getX() && spawnChunk.getZ() == blockChunk.getZ();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().hasPermission("aspire.admin.bypass")) return;
        if (isSpawnChunk(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Spawn is protected!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().hasPermission("aspire.admin.bypass")) return;
        if (isSpawnChunk(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Spawn is protected!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getPlayer().hasPermission("aspire.admin.bypass")) return;
        // Only block block-modifying interactions, not item use (throwing tridents, wind charges, etc)
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                && event.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) return;
        if (isSpawnChunk(event.getClickedBlock().getLocation())) {
            // Don't block throwing items (tridents, wind charges, etc) — only block containers/blocks
            org.bukkit.Material blockType = event.getClickedBlock().getType();
            if (blockType == org.bukkit.Material.CHEST || blockType == org.bukkit.Material.TRAPPED_CHEST
                    || blockType == org.bukkit.Material.BARREL || blockType.name().contains("SHULKER_BOX")
                    || blockType == org.bukkit.Material.FURNACE || blockType == org.bukkit.Material.CRAFTING_TABLE
                    || blockType == org.bukkit.Material.ANVIL || blockType.name().contains("ANVIL")
                    || blockType == org.bukkit.Material.ENCHANTING_TABLE || blockType == org.bukkit.Material.LEVER
                    || blockType.name().contains("BUTTON") || blockType.name().contains("DOOR")
                    || blockType.name().contains("GATE") || blockType == org.bukkit.Material.NOTE_BLOCK) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(Component.text("Spawn is protected!", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExplode(EntityExplodeEvent event) {
        if (!isSmpWorld(event.getEntity().getWorld())) return;
        event.blockList().removeIf(block -> isSpawnChunk(block.getLocation()));
    }
}

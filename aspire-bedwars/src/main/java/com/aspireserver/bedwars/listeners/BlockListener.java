package com.aspireserver.bedwars.listeners;

import com.aspireserver.bedwars.AspireBedwars;
import com.aspireserver.bedwars.team.BedwarsTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;

public class BlockListener implements Listener {

    private final AspireBedwars plugin;

    public BlockListener(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    private boolean inWorld(Block block) {
        return block.getWorld().getName().equalsIgnoreCase(plugin.getSetupConfig().getWorldName());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!inWorld(block)) return;
        if (plugin.getSetupMode().isInSetup(event.getPlayer())) return;
        if (!plugin.getGameManager().isRunning()) { event.setCancelled(true); return; }
        if (plugin.getSpectatorManager().isSpectator(event.getPlayer().getUniqueId())) { event.setCancelled(true); return; }
        plugin.getArenaReset().recordPlace(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!inWorld(block)) return;
        Player player = event.getPlayer();
        if (plugin.getSetupMode().isInSetup(player)) return;

        if (!plugin.getGameManager().isRunning()) { event.setCancelled(true); return; }
        if (plugin.getSpectatorManager().isSpectator(player.getUniqueId())) { event.setCancelled(true); return; }

        // Bed break?
        BedwarsTeam bedTeam = teamBedAt(block.getLocation());
        if (bedTeam != null) {
            BedwarsTeam own = plugin.getTeamManager().getTeam(player.getUniqueId());
            if (own != null && own == bedTeam) {
                event.setCancelled(true);
                player.sendActionBar(Component.text("You cannot break your own bed!", NamedTextColor.RED));
                return;
            }
            if (!bedTeam.isBedAlive()) { event.setCancelled(true); return; }
            event.setDropItems(false);
            plugin.getGameManager().destroyBed(bedTeam, player, false);
            // remove both bed halves
            removeBed(block, bedTeam);
            return;
        }

        // Only player-placed blocks can be broken
        if (!plugin.getArenaReset().isPlayerPlaced(block)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("You can only break player-placed blocks!", NamedTextColor.RED));
            return;
        }
        plugin.getArenaReset().recordBreak(block);
    }

    private void removeBed(Block block, BedwarsTeam team) {
        block.setType(Material.AIR, false);
        for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST}) {
            Block rel = block.getRelative(face);
            if (rel.getType().name().endsWith("_BED")) rel.setType(Material.AIR, false);
        }
    }

    private BedwarsTeam teamBedAt(Location loc) {
        for (BedwarsTeam team : plugin.getTeamManager().getTeams()) {
            Location bed = team.getBed();
            if (bed == null || bed.getWorld() == null) continue;
            if (!bed.getWorld().equals(loc.getWorld())) continue;
            if (bed.distanceSquared(loc) <= 2.5) return team;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!event.getEntity().getWorld().getName().equalsIgnoreCase(plugin.getSetupConfig().getWorldName())) return;
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (teamBedAt(b.getLocation()) != null) { it.remove(); continue; }
            if (plugin.getArenaReset().isPlayerPlaced(b)) {
                plugin.getArenaReset().recordBreak(b);
            } else {
                it.remove(); // protect map terrain from explosions
            }
        }
    }
}

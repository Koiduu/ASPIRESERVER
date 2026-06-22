package com.aspireserver.buildbattle.listeners;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.game.GameSession;
import com.aspireserver.buildbattle.game.GameState;
import com.aspireserver.buildbattle.plot.PlotRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.block.BlockExplodeEvent;

import java.util.*;

public class AntiGriefListener implements Listener {

    private final AspireBuildBattle plugin;
    private final Map<UUID, Integer> plotEntityCount;
    private final Map<UUID, Integer> plotHeadCount;

    private static final Set<EntityType> DISALLOWED_MOBS = Set.of(
        EntityType.WARDEN, EntityType.WITHER, EntityType.ENDERMAN,
        EntityType.ENDER_DRAGON, EntityType.ELDER_GUARDIAN,
        EntityType.RAVAGER, EntityType.VEX
    );

    private static final int MAX_ENTITIES_PER_PLOT = 5;
    private static final int MAX_HEADS_PER_PLOT = 15;

    public AntiGriefListener(AspireBuildBattle plugin) {
        this.plugin = plugin;
        this.plotEntityCount = new HashMap<>();
        this.plotHeadCount = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session == null) return;

        if (session.getState() != GameState.BUILDING) {
            event.setCancelled(true);
            return;
        }

        PlotRegion plot = session.getPlayerPlot(player.getUniqueId());
        if (plot == null || !plot.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                "You can only build within your plot!", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        if (plot.isOnBorder(event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Cannot modify plot borders!", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        Material type = event.getBlock().getType();
        if (type == Material.PLAYER_HEAD || type == Material.PLAYER_WALL_HEAD) {
            int heads = plotHeadCount.getOrDefault(player.getUniqueId(), 0);
            if (heads >= MAX_HEADS_PER_PLOT) {
                event.setCancelled(true);
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Max " + MAX_HEADS_PER_PLOT + " heads per plot!", net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }
            plotHeadCount.put(player.getUniqueId(), heads + 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session == null) return;

        if (session.getState() != GameState.BUILDING) {
            event.setCancelled(true);
            return;
        }

        PlotRegion plot = session.getPlayerPlot(player.getUniqueId());
        if (plot == null || !plot.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        if (plot.isOnBorder(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session == null) return;
        if (session.getState() != GameState.BUILDING) return;

        PlotRegion plot = session.getPlayerPlot(player.getUniqueId());
        if (plot == null) return;

        Location to = event.getTo();
        if (!plot.contains(to)) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Stay within your plot!", net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            for (var arena : plugin.getArenaManager().getArenas()) {
                for (PlotRegion plot : arena.getPlots()) {
                    if (plot.contains(block.getLocation())) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            for (var arena : plugin.getArenaManager().getArenas()) {
                for (PlotRegion plot : arena.getPlots()) {
                    if (plot.contains(block.getLocation())) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    @EventHandler
    public void onRedstone(BlockRedstoneEvent event) {
        Location loc = event.getBlock().getLocation();
        for (var arena : plugin.getArenaManager().getArenas()) {
            for (PlotRegion plot : arena.getPlots()) {
                if (plot.contains(loc)) {
                    event.setNewCurrent(0);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
            || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {

            if (DISALLOWED_MOBS.contains(event.getEntityType())) {
                event.setCancelled(true);
                return;
            }

            Location loc = event.getLocation();
            for (var arena : plugin.getArenaManager().getArenas()) {
                for (PlotRegion plot : arena.getPlots()) {
                    if (plot.contains(loc)) {
                        long entityCount = loc.getWorld().getEntities().stream()
                            .filter(e -> !(e instanceof Player) && plot.contains(e.getLocation()))
                            .count();
                        if (entityCount >= MAX_ENTITIES_PER_PLOT) {
                            event.setCancelled(true);
                        }
                        return;
                    }
                }
            }
        }
    }
}

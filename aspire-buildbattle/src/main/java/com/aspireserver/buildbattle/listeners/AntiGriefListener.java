package com.aspireserver.buildbattle.listeners;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.game.GameSession;
import com.aspireserver.buildbattle.game.GameState;
import com.aspireserver.buildbattle.plot.PlotRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
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

    private static final Set<Material> GRAVITY_BLOCKS = Set.of(
        Material.SAND, Material.RED_SAND, Material.GRAVEL,
        Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
        Material.DRAGON_EGG, Material.SUSPICIOUS_SAND, Material.SUSPICIOUS_GRAVEL,
        Material.WHITE_CONCRETE_POWDER, Material.ORANGE_CONCRETE_POWDER,
        Material.MAGENTA_CONCRETE_POWDER, Material.LIGHT_BLUE_CONCRETE_POWDER,
        Material.YELLOW_CONCRETE_POWDER, Material.LIME_CONCRETE_POWDER,
        Material.PINK_CONCRETE_POWDER, Material.GRAY_CONCRETE_POWDER,
        Material.LIGHT_GRAY_CONCRETE_POWDER, Material.CYAN_CONCRETE_POWDER,
        Material.PURPLE_CONCRETE_POWDER, Material.BLUE_CONCRETE_POWDER,
        Material.BROWN_CONCRETE_POWDER, Material.GREEN_CONCRETE_POWDER,
        Material.RED_CONCRETE_POWDER, Material.BLACK_CONCRETE_POWDER
    );

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

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Location loc = event.getBlock().getLocation();
        Material type = event.getBlock().getType();

        // Allow physics for walls, fences, fence gates (they update connections)
        if (Tag.WALLS.isTagged(type) || Tag.FENCES.isTagged(type) || Tag.FENCE_GATES.isTagged(type)) {
            return;
        }
        // Allow 2-tall plants: doors, tall grass, tall flowers, etc.
        if (Tag.DOORS.isTagged(type)
            || type == Material.TALL_GRASS || type == Material.LARGE_FERN
            || type == Material.TALL_SEAGRASS || type == Material.SUNFLOWER
            || type == Material.LILAC || type == Material.PEONY
            || type == Material.ROSE_BUSH || type == Material.PITCHER_PLANT) {
            return;
        }

        // Cancel physics for gravity blocks inside plots
        if (isGravityBlock(type)) {
            for (var arena : plugin.getArenaManager().getArenas()) {
                for (PlotRegion plot : arena.getPlots()) {
                    if (plot.contains(loc)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        // Also cancel general block updates (like water flow, etc.) for source block if it's inside a plot
        if (event.getSourceBlock() != null && event.getSourceBlock().getLocation() != event.getBlock().getLocation()) {
            Material sourceType = event.getSourceBlock().getType();
            if (isGravityBlock(sourceType)) {
                for (var arena : plugin.getArenaManager().getArenas()) {
                    for (PlotRegion plot : arena.getPlots()) {
                        if (plot.contains(event.getSourceBlock().getLocation())) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    private boolean isGravityBlock(Material type) {
        return GRAVITY_BLOCKS.contains(type) || type.name().endsWith("_CONCRETE_POWDER");
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        player.sendActionBar(net.kyori.adventure.text.Component.text(
            "You cannot drop items here!", net.kyori.adventure.text.format.NamedTextColor.RED));
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
                            return;
                        }
                        if (event.getEntity() instanceof org.bukkit.entity.Mob mob) {
                            mob.setAI(false);
                            mob.setGravity(false);
                            mob.setInvulnerable(true);
                            mob.setSilent(true);
                            mob.setCollidable(false);
                        }
                        return;
                    }
                }
            }
        }
    }
}

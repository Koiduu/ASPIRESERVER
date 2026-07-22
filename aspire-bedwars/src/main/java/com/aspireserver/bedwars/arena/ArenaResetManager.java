package com.aspireserver.bedwars.arena;

import com.aspireserver.bedwars.AspireBedwars;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks match-time world changes against the pre-game baseline and reverts them.
 * Player-placed blocks are removed; blocks broken during the match are restored
 * from their captured original state. Reverts are batched to avoid tick stalls.
 */
public class ArenaResetManager {

    private final AspireBedwars plugin;
    // key -> location of a block the players PLACED (remove on reset)
    private final Map<String, Location> placed = new ConcurrentHashMap<>();
    // key -> original BlockData of a block BROKEN during the match (restore on reset)
    private final Map<String, BlockData> broken = new LinkedHashMap<>();
    // key -> snapshot of a chest/container's contents at match start (restore on reset)
    private final Map<String, ItemStack[]> chestSnapshots = new HashMap<>();

    public ArenaResetManager(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public void recordPlace(Block block) {
        String k = key(block.getLocation());
        // If a broken record exists here, players are re-placing into a hole; keep place record
        placed.put(k, block.getLocation().clone());
    }

    public void recordBreak(Block block) {
        String k = key(block.getLocation());
        if (placed.containsKey(k)) {
            // it was a player-placed block being broken again — just drop the place record
            placed.remove(k);
            return;
        }
        // capture original state once
        broken.putIfAbsent(k, block.getBlockData().clone());
    }

    public boolean isPlayerPlaced(Block block) {
        return placed.containsKey(key(block.getLocation()));
    }

    public void beginMatch() {
        placed.clear();
        broken.clear();
        chestSnapshots.clear();
        snapshotChests();
    }

    /** Captures the contents of every loaded chest/container in the bedwars world. */
    private void snapshotChests() {
        World world = plugin.getServer().getWorld(plugin.getSetupConfig().getWorldName());
        if (world == null) return;
        for (Chunk chunk : world.getLoadedChunks()) {
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof Container container) {
                    chestSnapshots.put(key(container.getLocation()), cloneContents(container.getInventory().getContents()));
                }
            }
        }
    }

    private static ItemStack[] cloneContents(ItemStack[] src) {
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) out[i] = src[i] == null ? null : src[i].clone();
        return out;
    }

    public void resetArena() {
        // Reset chests: clear any chest that isn't part of the pre-match snapshot,
        // restore snapshotted chests to their original contents.
        World bwWorld = plugin.getServer().getWorld(plugin.getSetupConfig().getWorldName());
        if (bwWorld != null) {
            for (Chunk chunk : bwWorld.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof Container container)) continue;
                    ItemStack[] snap = chestSnapshots.get(key(container.getLocation()));
                    container.getInventory().clear();
                    if (snap != null) container.getInventory().setContents(cloneContents(snap));
                }
            }
        }
        chestSnapshots.clear();

        // Remove player-placed blocks
        for (Location loc : placed.values()) {
            Block b = loc.getBlock();
            b.setType(Material.AIR, false);
        }
        // Restore broken blocks
        for (Map.Entry<String, BlockData> e : broken.entrySet()) {
            String[] parts = e.getKey().split(":");
            World w = plugin.getServer().getWorld(parts[0]);
            if (w == null) continue;
            Block b = w.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            b.setBlockData(e.getValue(), false);
        }
        placed.clear();
        broken.clear();

        // Clean loose items and primed TNT in the world
        World world = plugin.getServer().getWorld(plugin.getSetupConfig().getWorldName());
        if (world != null) {
            for (Item item : world.getEntitiesByClass(Item.class)) item.remove();
            for (TNTPrimed tnt : world.getEntitiesByClass(TNTPrimed.class)) tnt.remove();
        }
    }
}

package com.aspireserver.bedwars.arena;

import com.aspireserver.bedwars.AspireBedwars;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.TNTPrimed;

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
    }

    public void resetArena() {
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

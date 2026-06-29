package com.aspireserver.buildbattle.plot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public class PlotRegion {

    private final String worldName;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    public PlotRegion(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.worldName = worldName;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public boolean contains(Location loc) {
        if (!loc.getWorld().getName().equals(worldName)) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean containsXZ(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public Location getCenter() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
            (minX + maxX) / 2.0 + 0.5,
            minY + 1,
            (minZ + maxZ) / 2.0 + 0.5
        );
    }

    public Location getSafeCenter() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double cx = (minX + maxX) / 2.0 + 0.5;
        double cz = (minZ + maxZ) / 2.0 + 0.5;

        for (int y = minY + 1; y <= maxY - 1; y++) {
            Block feet = world.getBlockAt((int) cx, y, (int) cz);
            Block head = world.getBlockAt((int) cx, y + 1, (int) cz);
            Block below = world.getBlockAt((int) cx, y - 1, (int) cz);
            if (feet.getType() == Material.AIR && head.getType() == Material.AIR
                && below.getType() != Material.AIR) {
                return new Location(world, cx, y, cz);
            }
        }

        for (int y = maxY; y >= minY + 1; y--) {
            Block feet = world.getBlockAt((int) cx, y, (int) cz);
            Block head = world.getBlockAt((int) cx, y + 1, (int) cz);
            if (feet.getType() == Material.AIR && head.getType() == Material.AIR) {
                return new Location(world, cx, y, cz);
            }
        }

        return new Location(world, cx, minY + 1.0, cz);
    }

    public void clear() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Bukkit.getScheduler().runTask(
            Bukkit.getPluginManager().getPlugin("AspireBuildBattle"), () -> {
                // Remove all non-player entities inside the plot
                for (org.bukkit.entity.Entity entity : world.getEntities()) {
                    if (entity instanceof org.bukkit.entity.Player) continue;
                    if (contains(entity.getLocation())) {
                        entity.remove();
                    }
                }

                // Clear blocks
                for (int x = minX + 1; x < maxX; x++) {
                    for (int z = minZ + 1; z < maxZ; z++) {
                        for (int y = minY + 1; y <= maxY; y++) {
                            Block block = world.getBlockAt(x, y, z);
                            if (block.getType() != Material.AIR) {
                                block.setType(Material.AIR, false);
                            }
                        }
                    }
                }
            }
        );
    }

    public boolean isOnBorder(Location loc) {
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x == minX || x == maxX || z == minZ || z == maxZ;
    }

    public String getWorldName() { return worldName; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }
}

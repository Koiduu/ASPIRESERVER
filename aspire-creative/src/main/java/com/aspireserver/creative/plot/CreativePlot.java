package com.aspireserver.creative.plot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CreativePlot {

    private final int id;
    private final PlotTier tier;
    private final int gridX;
    private final int gridZ;
    private UUID owner;
    private final Set<UUID> members;

    public CreativePlot(int id, PlotTier tier, int gridX, int gridZ) {
        this.id = id;
        this.tier = tier;
        this.gridX = gridX;
        this.gridZ = gridZ;
        this.members = new HashSet<>();
    }

    public int getId() { return id; }
    public PlotTier getTier() { return tier; }
    public int getGridX() { return gridX; }
    public int getGridZ() { return gridZ; }
    public UUID getOwner() { return owner; }
    public Set<UUID> getMembers() { return members; }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public void addMember(UUID member) {
        members.add(member);
    }

    public void removeMember(UUID member) {
        members.remove(member);
    }

    public boolean canAccess(UUID player) {
        if (owner == null) return false;
        return owner.equals(player) || members.contains(player);
    }

    public boolean isClaimed() {
        return owner != null;
    }

    public int getMinX() {
        return gridX * tier.getPlotSpacing();
    }

    public int getMinZ() {
        return gridZ * tier.getPlotSpacing();
    }

    public int getMaxX() {
        return getMinX() + tier.getSize() - 1;
    }

    public int getMaxZ() {
        return getMinZ() + tier.getSize() - 1;
    }

    public Location getSpawnLocation() {
        World world = Bukkit.getWorld(tier.getWorldName());
        if (world == null) return null;
        int centerX = getMinX() + tier.getSize() / 2;
        int centerZ = getMinZ() + tier.getSize() / 2;
        return new Location(world, centerX + 0.5, 65, centerZ + 0.5);
    }

    public boolean contains(Location loc) {
        if (!loc.getWorld().getName().equals(tier.getWorldName())) return false;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= getMinX() && x <= getMaxX() && z >= getMinZ() && z <= getMaxZ();
    }
}

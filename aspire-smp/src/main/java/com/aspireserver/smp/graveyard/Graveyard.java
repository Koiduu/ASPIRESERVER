package com.aspireserver.smp.graveyard;

import com.aspireserver.smp.AspireSMP;
import com.aspireserver.smp.claim.Claim;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class Graveyard {

    private final UUID owner;
    private final String worldName;
    private final double x, y, z;
    private final List<ItemStack> items;
    private final long createdAt;

    public Graveyard(UUID owner, String worldName, double x, double y, double z, List<ItemStack> items) {
        this(owner, worldName, x, y, z, items, System.currentTimeMillis());
    }

    public Graveyard(UUID owner, String worldName, double x, double y, double z, List<ItemStack> items, long createdAt) {
        this.owner = owner;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.items = items;
        this.createdAt = createdAt;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    /**
     * Returns a Location if the world is loaded, or null if not yet available.
     */
    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isExpired(long maxAgeMillis) {
        return System.currentTimeMillis() - createdAt > maxAgeMillis;
    }

    public boolean canAccess(UUID accessor) {
        if (owner.equals(accessor)) return true;

        AspireSMP smp = AspireSMP.getInstance();
        if (smp == null) return false;

        for (Claim claim : smp.getClaimManager().getPlayerClaims(owner)) {
            if (claim.isTrusted(accessor)) {
                return true;
            }
        }
        return false;
    }
}

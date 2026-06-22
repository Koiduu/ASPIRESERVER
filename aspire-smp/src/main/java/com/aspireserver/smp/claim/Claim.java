package com.aspireserver.smp.claim;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Claim {

    private final UUID owner;
    private final String worldName;
    private final int minX, minZ, maxX, maxZ;
    private final Set<UUID> trustedPlayers;

    public Claim(UUID owner, String worldName, int minX, int minZ, int maxX, int maxZ) {
        this.owner = owner;
        this.worldName = worldName;
        this.minX = Math.min(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxZ = Math.max(minZ, maxZ);
        this.trustedPlayers = new HashSet<>();
    }

    public boolean contains(Location loc) {
        if (!loc.getWorld().getName().equals(worldName)) return false;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean overlaps(int oMinX, int oMinZ, int oMaxX, int oMaxZ) {
        return !(oMaxX < minX || oMinX > maxX || oMaxZ < minZ || oMinZ > maxZ);
    }

    public int getArea() {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    public UUID getOwner() { return owner; }
    public String getWorldName() { return worldName; }
    public int getMinX() { return minX; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxZ() { return maxZ; }
    public Set<UUID> getTrustedPlayers() { return trustedPlayers; }

    public void addTrusted(UUID player) {
        trustedPlayers.add(player);
    }

    public void removeTrusted(UUID player) {
        trustedPlayers.remove(player);
    }

    public boolean isTrusted(UUID player) {
        return trustedPlayers.contains(player);
    }

    public boolean canAccess(UUID player) {
        return owner.equals(player) || trustedPlayers.contains(player);
    }
}

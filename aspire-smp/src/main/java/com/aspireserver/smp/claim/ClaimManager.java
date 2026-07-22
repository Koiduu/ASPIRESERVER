package com.aspireserver.smp.claim;

import com.aspireserver.smp.AspireSMP;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ClaimManager {

    private final AspireSMP plugin;
    private final File claimsFile;
    private FileConfiguration claimsConfig;
    private final List<Claim> claims;
    private final Map<UUID, Integer> claimLimits;
    private final Map<UUID, Integer> claimTiers;
    private final Map<Long, List<Claim>> chunkIndex;

    private static final int DEFAULT_CLAIM_LIMIT = 500;
    private static final int TIER_1_LIMIT = 1500;
    private static final int TIER_2_LIMIT = 3500;

    public ClaimManager(AspireSMP plugin) {
        this.plugin = plugin;
        this.claimsFile = new File(plugin.getDataFolder(), "claims.yml");
        this.claims = new ArrayList<>();
        this.claimLimits = new HashMap<>();
        this.claimTiers = new HashMap<>();
        this.chunkIndex = new HashMap<>();
        loadClaims();
    }

    private void loadClaims() {
        if (!claimsFile.exists()) {
            try {
                claimsFile.getParentFile().mkdirs();
                claimsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create claims.yml");
            }
        }
        claimsConfig = YamlConfiguration.loadConfiguration(claimsFile);

        ConfigurationSection section = claimsConfig.getConfigurationSection("claims");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            UUID owner = UUID.fromString(section.getString(key + ".owner"));
            String world = section.getString(key + ".world");
            int minX = section.getInt(key + ".minX");
            int minZ = section.getInt(key + ".minZ");
            int maxX = section.getInt(key + ".maxX");
            int maxZ = section.getInt(key + ".maxZ");

            Claim claim = new Claim(owner, world, minX, minZ, maxX, maxZ);

            List<String> trusted = section.getStringList(key + ".trusted");
            for (String t : trusted) {
                claim.addTrusted(UUID.fromString(t));
            }
            claims.add(claim);
            indexClaim(claim);
        }

        ConfigurationSection tiers = claimsConfig.getConfigurationSection("tiers");
        if (tiers != null) {
            for (String key : tiers.getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                int tier = tiers.getInt(key);
                claimTiers.put(uuid, tier);
                claimLimits.put(uuid, tier == 2 ? TIER_2_LIMIT : TIER_1_LIMIT);
            }
        }
    }

    public void saveAllClaims() {
        claimsConfig = new YamlConfiguration();
        int i = 0;
        for (Claim claim : claims) {
            String path = "claims." + i;
            claimsConfig.set(path + ".owner", claim.getOwner().toString());
            claimsConfig.set(path + ".world", claim.getWorldName());
            claimsConfig.set(path + ".minX", claim.getMinX());
            claimsConfig.set(path + ".minZ", claim.getMinZ());
            claimsConfig.set(path + ".maxX", claim.getMaxX());
            claimsConfig.set(path + ".maxZ", claim.getMaxZ());
            List<String> trusted = new ArrayList<>();
            for (UUID t : claim.getTrustedPlayers()) {
                trusted.add(t.toString());
            }
            claimsConfig.set(path + ".trusted", trusted);
            i++;
        }

        for (Map.Entry<UUID, Integer> entry : claimTiers.entrySet()) {
            claimsConfig.set("tiers." + entry.getKey().toString(), entry.getValue());
        }

        try {
            claimsConfig.save(claimsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save claims.yml");
        }
    }

    public boolean createClaim(UUID owner, String world, int minX, int minZ, int maxX, int maxZ) {
        int realMinX = Math.min(minX, maxX);
        int realMinZ = Math.min(minZ, maxZ);
        int realMaxX = Math.max(minX, maxX);
        int realMaxZ = Math.max(minZ, maxZ);

        int area = (realMaxX - realMinX + 1) * (realMaxZ - realMinZ + 1);
        int usedBlocks = getUsedBlocks(owner);
        int limit = getClaimLimit(owner);

        if (usedBlocks + area > limit) {
            return false;
        }

        for (Claim existing : claims) {
            if (existing.getWorldName().equals(world) && existing.overlaps(realMinX, realMinZ, realMaxX, realMaxZ)) {
                if (!existing.getOwner().equals(owner)) {
                    return false;
                }
            }
        }

        Claim claim = new Claim(owner, world, realMinX, realMinZ, realMaxX, realMaxZ);
        claims.add(claim);
        indexClaim(claim);
        saveAllClaims();
        return true;
    }

    public boolean removeClaim(UUID player, Location location) {
        Claim toRemove = null;
        for (Claim claim : claims) {
            if (claim.getOwner().equals(player) && claim.contains(location)) {
                toRemove = claim;
                break;
            }
        }
        if (toRemove != null) {
            claims.remove(toRemove);
            deindexClaim(toRemove);
            saveAllClaims();
            return true;
        }
        return false;
    }

    public Claim getClaimAt(Location location) {
        long key = chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        List<Claim> candidates = chunkIndex.get(key);
        if (candidates == null) return null;
        for (Claim claim : candidates) {
            if (claim.contains(location)) {
                return claim;
            }
        }
        return null;
    }

    public List<Claim> getPlayerClaims(UUID player) {
        List<Claim> playerClaims = new ArrayList<>();
        for (Claim claim : claims) {
            if (claim.getOwner().equals(player)) {
                playerClaims.add(claim);
            }
        }
        return playerClaims;
    }

    public int getUsedBlocks(UUID player) {
        int total = 0;
        for (Claim claim : claims) {
            if (claim.getOwner().equals(player)) {
                total += claim.getArea();
            }
        }
        return total;
    }

    public int getClaimLimit(UUID player) {
        return claimLimits.getOrDefault(player, DEFAULT_CLAIM_LIMIT);
    }

    public int getClaimTier(UUID player) {
        return claimTiers.getOrDefault(player, 0);
    }

    public boolean upgradeTier(UUID player) {
        int currentTier = getClaimTier(player);
        if (currentTier >= 2) return false;

        int newTier = currentTier + 1;
        claimTiers.put(player, newTier);
        claimLimits.put(player, newTier == 1 ? TIER_1_LIMIT : TIER_2_LIMIT);
        saveAllClaims();
        return true;
    }

    public void addTrusted(UUID owner, UUID trusted) {
        for (Claim claim : claims) {
            if (claim.getOwner().equals(owner)) {
                claim.addTrusted(trusted);
            }
        }
        saveAllClaims();
    }

    public boolean canBuild(UUID player, Location location) {
        if (player == null) return false;
        Claim claim = getClaimAt(location);
        if (claim == null) return true;
        return claim.canAccess(player);
    }

    public List<Claim> getAllClaims() {
        return Collections.unmodifiableList(claims);
    }

    private void indexClaim(Claim claim) {
        int minCX = claim.getMinX() >> 4;
        int maxCX = claim.getMaxX() >> 4;
        int minCZ = claim.getMinZ() >> 4;
        int maxCZ = claim.getMaxZ() >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                chunkIndex.computeIfAbsent(chunkKey(cx, cz), k -> new ArrayList<>()).add(claim);
            }
        }
    }

    private void deindexClaim(Claim claim) {
        int minCX = claim.getMinX() >> 4;
        int maxCX = claim.getMaxX() >> 4;
        int minCZ = claim.getMinZ() >> 4;
        int maxCZ = claim.getMaxZ() >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                List<Claim> list = chunkIndex.get(chunkKey(cx, cz));
                if (list != null) {
                    list.remove(claim);
                    if (list.isEmpty()) chunkIndex.remove(chunkKey(cx, cz));
                }
            }
        }
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}

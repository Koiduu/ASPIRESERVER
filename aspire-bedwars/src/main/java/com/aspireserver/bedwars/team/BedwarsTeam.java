package com.aspireserver.bedwars.team;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BedwarsTeam {

    private final TeamColor color;
    private Location spawn;
    private Location bed;
    private boolean bedAlive = true;

    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> eliminated = new HashSet<>();

    // Team upgrades
    private int sharpnessLevel = 0;       // passive team sharpness
    private int protectionLevel = 0;
    private int hasteLevel = 0;
    private int forgeLevel = 0;           // generator speed upgrade tier
    private boolean healPool = false;
    private boolean endstoneHardened = false; // not used mechanic placeholder
    private final Set<String> purchasedTraps = new HashSet<>();

    public BedwarsTeam(TeamColor color) {
        this.color = color;
    }

    public TeamColor getColor() { return color; }
    public Location getSpawn() { return spawn; }
    public void setSpawn(Location spawn) { this.spawn = spawn; }
    public Location getBed() { return bed; }
    public void setBed(Location bed) { this.bed = bed; }

    public boolean isBedAlive() { return bedAlive; }
    public void setBedAlive(boolean bedAlive) { this.bedAlive = bedAlive; }

    public Set<UUID> getMembers() { return members; }
    public void addMember(UUID uuid) { members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); }
    public boolean isMember(UUID uuid) { return members.contains(uuid); }

    public void eliminate(UUID uuid) { eliminated.add(uuid); }
    public boolean isEliminated(UUID uuid) { return eliminated.contains(uuid); }
    public void resetEliminated() { eliminated.clear(); }

    /** A team is still in the game if it has at least one non-permanently-eliminated member. */
    public boolean isAlive() {
        for (UUID uuid : members) {
            if (!eliminated.contains(uuid)) return true;
        }
        return false;
    }

    public int aliveCount(java.util.function.Predicate<UUID> onlineCheck) {
        int count = 0;
        for (UUID uuid : members) {
            if (!eliminated.contains(uuid) && onlineCheck.test(uuid)) count++;
        }
        return count;
    }

    public int getSharpnessLevel() { return sharpnessLevel; }
    public void setSharpnessLevel(int v) { this.sharpnessLevel = v; }
    public int getProtectionLevel() { return protectionLevel; }
    public void setProtectionLevel(int v) { this.protectionLevel = v; }
    public int getHasteLevel() { return hasteLevel; }
    public void setHasteLevel(int v) { this.hasteLevel = v; }
    public int getForgeLevel() { return forgeLevel; }
    public void setForgeLevel(int v) { this.forgeLevel = v; }
    public boolean hasHealPool() { return healPool; }
    public void setHealPool(boolean v) { this.healPool = v; }
    public Set<String> getPurchasedTraps() { return purchasedTraps; }

    public void reset() {
        bedAlive = true;
        members.clear();
        eliminated.clear();
        sharpnessLevel = 0;
        protectionLevel = 0;
        hasteLevel = 0;
        forgeLevel = 0;
        healPool = false;
        purchasedTraps.clear();
    }
}

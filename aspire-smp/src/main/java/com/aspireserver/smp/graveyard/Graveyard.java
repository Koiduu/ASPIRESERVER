package com.aspireserver.smp.graveyard;

import com.aspireserver.smp.AspireSMP;
import com.aspireserver.smp.claim.Claim;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class Graveyard {

    private final UUID owner;
    private final Location location;
    private final List<ItemStack> items;

    public Graveyard(UUID owner, Location location, List<ItemStack> items) {
        this.owner = owner;
        this.location = location;
        this.items = items;
    }

    public UUID getOwner() {
        return owner;
    }

    public Location getLocation() {
        return location;
    }

    public List<ItemStack> getItems() {
        return items;
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

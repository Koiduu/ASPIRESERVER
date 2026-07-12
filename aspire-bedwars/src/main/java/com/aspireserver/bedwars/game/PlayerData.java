package com.aspireserver.bedwars.game;

import java.util.UUID;

/**
 * Per-player match state that must survive death/respawn (tool tiers, armor
 * tier, permanent purchases).
 */
public class PlayerData {
    public final UUID uuid;

    public int armorTier = 0;     // 0 leather, 1 chain, 2 iron, 3 diamond
    public int pickaxeTier = 0;   // 0 none, 1 wood, 2 iron, 3 gold(=efficiency), 4 diamond
    public int axeTier = 0;       // 0 none, 1 wood, 2 iron, 3 gold, 4 diamond
    public boolean shears = false;

    public int kills = 0;
    public int finalKills = 0;
    public int bedsBroken = 0;
    public boolean spectator = false;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    /** Tools downgrade by one tier on death (Hypixel behavior), min tier 1 once bought. */
    public void downgradeToolsOnDeath() {
        if (pickaxeTier > 1) pickaxeTier--;
        if (axeTier > 1) axeTier--;
    }
}

package com.aspireserver.bedwars.shop;

import com.aspireserver.bedwars.AspireBedwars;
import com.aspireserver.bedwars.config.NpcPoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop/upgrade NPCs implemented as stationary vanilla Villagers (real entities,
 * so PlayerInteractEntityEvent fires reliably). Swap-in point if FancyNpcs is
 * added later.
 */
public class NPCManager {

    public static final String NPC_META = "bw_npc";

    private final AspireBedwars plugin;
    private final List<Villager> spawned = new ArrayList<>();

    public NPCManager(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    public void spawnAll() {
        despawnAll();
        for (NpcPoint np : plugin.getSetupConfig().getNpcs()) {
            spawn(np);
        }
    }

    private void spawn(NpcPoint np) {
        Location loc = np.location;
        if (loc.getWorld() == null) return;
        Villager villager = loc.getWorld().spawn(loc, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setCollidable(false);
            v.setProfession(np.type == NpcPoint.NpcType.SHOP ? Villager.Profession.WEAPONSMITH : Villager.Profession.LIBRARIAN);
            v.setCustomNameVisible(true);
            if (np.type == NpcPoint.NpcType.SHOP) {
                v.customName(Component.text("Item Shop", NamedTextColor.GREEN));
            } else {
                v.customName(Component.text("Team Upgrades", NamedTextColor.AQUA));
            }
            v.setMetadata(NPC_META, new FixedMetadataValue(plugin, np.type.name()));
            v.setRemoveWhenFarAway(false);
        });
        spawned.add(villager);
    }

    public NpcPoint.NpcType typeOf(org.bukkit.entity.Entity entity) {
        if (entity.getType() != EntityType.VILLAGER) return null;
        if (!entity.hasMetadata(NPC_META)) return null;
        String type = entity.getMetadata(NPC_META).get(0).asString();
        return NpcPoint.NpcType.valueOf(type);
    }

    public void despawnAll() {
        for (Villager v : spawned) {
            if (v != null && !v.isDead()) v.remove();
        }
        spawned.clear();
        // also clean any stray tagged villagers in the world
        String worldName = plugin.getSetupConfig().getWorldName();
        var world = plugin.getServer().getWorld(worldName);
        if (world != null) {
            for (Villager v : world.getEntitiesByClass(Villager.class)) {
                if (v.hasMetadata(NPC_META)) v.remove();
            }
        }
    }
}

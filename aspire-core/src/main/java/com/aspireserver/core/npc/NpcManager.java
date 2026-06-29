package com.aspireserver.core.npc;

import com.aspireserver.core.AspireCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class NpcManager {

    private final AspireCore plugin;
    private final File npcFile;
    private FileConfiguration npcConfig;
    private final Map<String, NpcData> npcs;
    private final Map<UUID, String> entityNpcMap;

    public NpcManager(AspireCore plugin) {
        this.plugin = plugin;
        this.npcFile = new File(plugin.getDataFolder(), "npcs.yml");
        this.npcs = new HashMap<>();
        this.entityNpcMap = new HashMap<>();
        loadNpcs();
    }

    private void loadNpcs() {
        if (!npcFile.exists()) {
            try {
                npcFile.getParentFile().mkdirs();
                npcFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create npcs.yml");
            }
        }
        npcConfig = YamlConfiguration.loadConfiguration(npcFile);

        ConfigurationSection section = npcConfig.getConfigurationSection("npcs");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String displayName = section.getString(id + ".name", "NPC");
            String actionStr = section.getString(id + ".action", "WARP_LOBBY");
            String worldName = section.getString(id + ".world", "world");
            double x = section.getDouble(id + ".x");
            double y = section.getDouble(id + ".y");
            double z = section.getDouble(id + ".z");
            float yaw = (float) section.getDouble(id + ".yaw", 0);
            float pitch = (float) section.getDouble(id + ".pitch", 0);
            String skinName = section.getString(id + ".skin", null);

            String customCommand = section.getString(id + ".customCommand", null);

            NpcAction action;
            try {
                action = NpcAction.valueOf(actionStr);
            } catch (IllegalArgumentException e) {
                action = NpcAction.WARP_LOBBY;
            }

            var world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("NPC '" + id + "' world '" + worldName + "' not loaded yet, will retry on spawn.");
            }

            Location loc = world != null
                ? new Location(world, x, y, z, yaw, pitch)
                : new Location(Bukkit.getWorlds().get(0), x, y, z, yaw, pitch);
            NpcData npc = new NpcData(id, displayName, action, loc);
            npc.setWorldName(worldName);
            npc.setSkinName(skinName);
            npc.setCustomCommand(customCommand);
            npcs.put(id, npc);
        }
    }

    public void spawnAllNpcs() {
        for (NpcData npc : npcs.values()) {
            spawnNpc(npc);
        }
        plugin.getLogger().info("Spawned " + npcs.size() + " NPC(s).");
    }

    public void spawnNpc(NpcData npc) {
        Location loc = npc.getLocation();
        if (loc == null) return;

        // Re-resolve world at spawn time (Multiverse may have loaded it since config read)
        if (npc.getWorldName() != null) {
            var resolvedWorld = Bukkit.getWorld(npc.getWorldName());
            if (resolvedWorld != null && !resolvedWorld.equals(loc.getWorld())) {
                loc = new Location(resolvedWorld, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
                npc.setLocation(loc);
            }
        }

        if (loc.getWorld() == null) return;

        removeNpcEntity(npc);

        loc.getChunk().load();

        Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCollidable(false);
        villager.setGravity(false);
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.customName(Component.text(npc.getDisplayName(), NamedTextColor.GOLD));
        villager.setCustomNameVisible(true);
        villager.setProfession(Villager.Profession.NITWIT);
        villager.addScoreboardTag("aspire_npc_" + npc.getId());

        npc.setEntityUuid(villager.getUniqueId());
        entityNpcMap.put(villager.getUniqueId(), npc.getId());

        ArmorStand label = (ArmorStand) loc.getWorld().spawnEntity(
            loc.clone().add(0, -0.3, 0), EntityType.ARMOR_STAND);
        label.setVisible(false);
        label.setGravity(false);
        label.setInvulnerable(true);
        label.setMarker(true);
        label.customName(Component.text("[" + npc.getAction().getDisplayName() + "]", NamedTextColor.YELLOW));
        label.setCustomNameVisible(true);
        label.addScoreboardTag("aspire_npc_label_" + npc.getId());
    }

    private void removeNpcEntity(NpcData npc) {
        Location loc = npc.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        if (npc.getEntityUuid() != null) {
            entityNpcMap.remove(npc.getEntityUuid());
        }

        for (Entity entity : loc.getWorld().getEntities()) {
            if (npc.getEntityUuid() != null && entity.getUniqueId().equals(npc.getEntityUuid())) {
                entity.remove();
                continue;
            }
            if (entity.getScoreboardTags().contains("aspire_npc_" + npc.getId())) {
                entity.remove();
                continue;
            }
            if (entity.getScoreboardTags().contains("aspire_npc_label_" + npc.getId())) {
                entity.remove();
            }
        }
    }

    public NpcData createNpc(String id, String displayName, NpcAction action, Location location) {
        NpcData npc = new NpcData(id, displayName, action, location);
        npcs.put(id, npc);
        spawnNpc(npc);
        saveNpcs();
        return npc;
    }

    public boolean removeNpc(String id) {
        NpcData npc = npcs.remove(id);
        if (npc == null) return false;
        removeNpcEntity(npc);
        saveNpcs();
        return true;
    }

    public NpcData getNpcByEntity(UUID entityUuid) {
        String id = entityNpcMap.get(entityUuid);
        if (id != null) return npcs.get(id);

        Entity entity = Bukkit.getEntity(entityUuid);
        if (entity != null) {
            for (String tag : entity.getScoreboardTags()) {
                if (tag.startsWith("aspire_npc_") && !tag.startsWith("aspire_npc_label_")) {
                    String npcId = tag.substring("aspire_npc_".length());
                    NpcData npc = npcs.get(npcId);
                    if (npc != null) {
                        npc.setEntityUuid(entityUuid);
                        entityNpcMap.put(entityUuid, npcId);
                        return npc;
                    }
                }
            }
        }
        return null;
    }

    public NpcData getNpc(String id) {
        return npcs.get(id);
    }

    public Collection<NpcData> getAllNpcs() {
        return npcs.values();
    }

    public void saveNpcs() {
        npcConfig = new YamlConfiguration();
        for (Map.Entry<String, NpcData> entry : npcs.entrySet()) {
            String path = "npcs." + entry.getKey();
            NpcData npc = entry.getValue();
            npcConfig.set(path + ".name", npc.getDisplayName());
            npcConfig.set(path + ".action", npc.getAction().name());
            npcConfig.set(path + ".world", npc.getLocation().getWorld().getName());
            npcConfig.set(path + ".x", npc.getLocation().getX());
            npcConfig.set(path + ".y", npc.getLocation().getY());
            npcConfig.set(path + ".z", npc.getLocation().getZ());
            npcConfig.set(path + ".yaw", npc.getLocation().getYaw());
            npcConfig.set(path + ".pitch", npc.getLocation().getPitch());
            if (npc.getSkinName() != null) {
                npcConfig.set(path + ".skin", npc.getSkinName());
            }
            if (npc.getCustomCommand() != null) {
                npcConfig.set(path + ".customCommand", npc.getCustomCommand());
            }
        }
        try {
            npcConfig.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save npcs.yml");
        }
    }

    public void despawnAll() {
        for (NpcData npc : npcs.values()) {
            removeNpcEntity(npc);
        }
    }
}

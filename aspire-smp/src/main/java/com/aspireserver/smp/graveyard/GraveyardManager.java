package com.aspireserver.smp.graveyard;

import com.aspireserver.smp.AspireSMP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GraveyardManager {

    private final AspireSMP plugin;
    private final File dataFile;
    private FileConfiguration data;
    private final Map<UUID, List<Graveyard>> graveyards;

    private static final long EXPIRY_MILLIS = 2L * 24 * 60 * 60 * 1000; // 2 days

    public GraveyardManager(AspireSMP plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "graveyards.yml");
        this.graveyards = new HashMap<>();
        loadData();
        startExpiryTask();
    }

    private void startExpiryTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                purgeExpired();
            }
        }.runTaskTimer(plugin, 6000L, 6000L); // every 5 minutes
    }

    public void purgeExpired() {
        boolean changed = false;
        for (Map.Entry<UUID, List<Graveyard>> entry : graveyards.entrySet()) {
            Iterator<Graveyard> it = entry.getValue().iterator();
            while (it.hasNext()) {
                Graveyard gy = it.next();
                if (gy.isExpired(EXPIRY_MILLIS)) {
                    cleanupTombstone(gy);
                    it.remove();
                    changed = true;
                }
            }
        }
        graveyards.values().removeIf(List::isEmpty);
        if (changed) saveAll();
    }

    private void cleanupTombstone(Graveyard gy) {
        Location loc = gy.getLocation();
        Block block = loc.getBlock();
        if (block.getType() == Material.SOUL_LANTERN) {
            block.setType(Material.AIR);
        }
        loc.getWorld().getEntities().stream()
            .filter(e -> e instanceof ArmorStand && e.getLocation().distanceSquared(loc.clone().add(0.5, 1.2, 0.5)) < 1)
            .forEach(org.bukkit.entity.Entity::remove);
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create graveyards.yml");
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (data.contains("graveyards")) {
            for (String key : data.getConfigurationSection("graveyards").getKeys(false)) {
                UUID owner = UUID.fromString(key);
                List<Graveyard> list = new ArrayList<>();

                var section = data.getConfigurationSection("graveyards." + key);
                if (section != null) {
                    for (String gKey : section.getKeys(false)) {
                        String world = section.getString(gKey + ".world");
                        double x = section.getDouble(gKey + ".x");
                        double y = section.getDouble(gKey + ".y");
                        double z = section.getDouble(gKey + ".z");

                        World w = Bukkit.getWorld(world);
                        if (w == null) continue;

                        long createdAt = section.getLong(gKey + ".createdAt", System.currentTimeMillis());
                        Location loc = new Location(w, x, y, z);
                        List<ItemStack> items = new ArrayList<>();
                        var itemSection = section.getConfigurationSection(gKey + ".items");
                        if (itemSection != null) {
                            for (String iKey : itemSection.getKeys(false)) {
                                ItemStack item = itemSection.getItemStack(iKey);
                                if (item != null) items.add(item);
                            }
                        }

                        Graveyard gy = new Graveyard(owner, loc, items, createdAt);
                        list.add(gy);
                    }
                }
                graveyards.put(owner, list);
            }
        }
    }

    public void saveAll() {
        data = new YamlConfiguration();
        for (Map.Entry<UUID, List<Graveyard>> entry : graveyards.entrySet()) {
            String ownerPath = "graveyards." + entry.getKey().toString();
            int i = 0;
            for (Graveyard gy : entry.getValue()) {
                String path = ownerPath + "." + i;
                data.set(path + ".world", gy.getLocation().getWorld().getName());
                data.set(path + ".x", gy.getLocation().getX());
                data.set(path + ".y", gy.getLocation().getY());
                data.set(path + ".z", gy.getLocation().getZ());
                data.set(path + ".createdAt", gy.getCreatedAt());
                int j = 0;
                for (ItemStack item : gy.getItems()) {
                    data.set(path + ".items." + j, item);
                    j++;
                }
                i++;
            }
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save graveyards.yml");
        }
    }

    public Graveyard createGraveyard(UUID owner, Location deathLoc, ItemStack[] inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : inventory) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }

        if (items.isEmpty()) return null;

        Location loc = deathLoc.clone();
        loc.setY(findSafeY(loc));

        Graveyard graveyard = new Graveyard(owner, loc, items);
        graveyards.computeIfAbsent(owner, k -> new ArrayList<>()).add(graveyard);

        spawnTombstone(loc, owner);
        saveAll();
        return graveyard;
    }

    private void spawnTombstone(Location loc, UUID owner) {
        Block block = loc.getBlock();
        block.setType(Material.SOUL_LANTERN);

        ArmorStand hologram = (ArmorStand) loc.getWorld().spawnEntity(
            loc.clone().add(0.5, 1.2, 0.5), EntityType.ARMOR_STAND);
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setInvulnerable(true);
        hologram.setMarker(true);

        String playerName = Bukkit.getOfflinePlayer(owner).getName();
        hologram.customName(net.kyori.adventure.text.Component.text(
            (playerName != null ? playerName : "Unknown") + "'s Grave",
            net.kyori.adventure.text.format.NamedTextColor.GOLD));
        hologram.setCustomNameVisible(true);
    }

    public Graveyard getGraveyardAt(Location location, UUID accessor) {
        for (Map.Entry<UUID, List<Graveyard>> entry : graveyards.entrySet()) {
            for (Graveyard gy : entry.getValue()) {
                Location gyLoc = gy.getLocation();
                if (gyLoc.getWorld().equals(location.getWorld())
                    && gyLoc.getBlockX() == location.getBlockX()
                    && gyLoc.getBlockY() == location.getBlockY()
                    && gyLoc.getBlockZ() == location.getBlockZ()) {

                    if (gy.canAccess(accessor)) {
                        return gy;
                    }
                }
            }
        }
        return null;
    }

    public void removeGraveyard(Graveyard graveyard) {
        UUID owner = graveyard.getOwner();
        List<Graveyard> list = graveyards.get(owner);
        if (list != null) {
            list.remove(graveyard);
        }
        cleanupTombstone(graveyard);
        saveAll();
    }

    private double findSafeY(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        for (int y = loc.getBlockY(); y >= world.getMinHeight(); y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return y + 1;
            }
        }
        return loc.getY();
    }
}

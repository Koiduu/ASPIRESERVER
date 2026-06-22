package com.aspireserver.creative.plot;

import com.aspireserver.creative.AspireCreative;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlotManager {

    private final AspireCreative plugin;
    private final File plotsFile;
    private FileConfiguration plotsConfig;
    private final Map<String, CreativePlot> plots;
    private final Map<UUID, List<String>> playerPlots;
    private int nextPlotId;

    private static final int MAX_WORLDEDIT_VOLUME = 250000;

    public PlotManager(AspireCreative plugin) {
        this.plugin = plugin;
        this.plotsFile = new File(plugin.getDataFolder(), "plots.yml");
        this.plots = new ConcurrentHashMap<>();
        this.playerPlots = new ConcurrentHashMap<>();
        this.nextPlotId = 1;
    }

    public void loadPlots() {
        if (!plotsFile.exists()) {
            try {
                plotsFile.getParentFile().mkdirs();
                plotsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create plots.yml");
            }
        }
        plotsConfig = YamlConfiguration.loadConfiguration(plotsFile);

        nextPlotId = plotsConfig.getInt("next-id", 1);

        ConfigurationSection section = plotsConfig.getConfigurationSection("plots");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            int id = section.getInt(key + ".id");
            PlotTier tier = PlotTier.valueOf(section.getString(key + ".tier", "SMALL"));
            int gridX = section.getInt(key + ".gridX");
            int gridZ = section.getInt(key + ".gridZ");

            CreativePlot plot = new CreativePlot(id, tier, gridX, gridZ);

            String ownerStr = section.getString(key + ".owner");
            if (ownerStr != null && !ownerStr.isEmpty()) {
                UUID owner = UUID.fromString(ownerStr);
                plot.setOwner(owner);
                playerPlots.computeIfAbsent(owner, k -> new ArrayList<>()).add(key);
            }

            List<String> members = section.getStringList(key + ".members");
            for (String m : members) {
                plot.addMember(UUID.fromString(m));
            }

            plots.put(key, plot);
        }

        plugin.getLogger().info("Loaded " + plots.size() + " creative plots.");
    }

    public void savePlots() {
        plotsConfig = new YamlConfiguration();
        plotsConfig.set("next-id", nextPlotId);

        for (Map.Entry<String, CreativePlot> entry : plots.entrySet()) {
            String path = "plots." + entry.getKey();
            CreativePlot plot = entry.getValue();
            plotsConfig.set(path + ".id", plot.getId());
            plotsConfig.set(path + ".tier", plot.getTier().name());
            plotsConfig.set(path + ".gridX", plot.getGridX());
            plotsConfig.set(path + ".gridZ", plot.getGridZ());
            plotsConfig.set(path + ".owner", plot.getOwner() != null ? plot.getOwner().toString() : "");
            List<String> members = new ArrayList<>();
            for (UUID m : plot.getMembers()) {
                members.add(m.toString());
            }
            plotsConfig.set(path + ".members", members);
        }

        try {
            plotsConfig.save(plotsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save plots.yml");
        }
    }

    public CreativePlot claimPlot(UUID player, PlotTier tier) {
        int[] coords = findNextAvailableCoords(tier);
        int id = nextPlotId++;

        CreativePlot plot = new CreativePlot(id, tier, coords[0], coords[1]);
        plot.setOwner(player);

        String key = tier.name().toLowerCase() + "_" + coords[0] + "_" + coords[1];
        plots.put(key, plot);
        playerPlots.computeIfAbsent(player, k -> new ArrayList<>()).add(key);

        savePlots();
        return plot;
    }

    private int[] findNextAvailableCoords(PlotTier tier) {
        Set<String> usedCoords = new HashSet<>();
        for (Map.Entry<String, CreativePlot> entry : plots.entrySet()) {
            CreativePlot p = entry.getValue();
            if (p.getTier() == tier) {
                usedCoords.add(p.getGridX() + "," + p.getGridZ());
            }
        }

        int radius = 0;
        while (true) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) == radius || Math.abs(z) == radius) {
                        if (!usedCoords.contains(x + "," + z)) {
                            return new int[]{x, z};
                        }
                    }
                }
            }
            radius++;
            if (radius > 1000) break;
        }
        return new int[]{0, 0};
    }

    public void clearPlot(CreativePlot plot) {
        new BukkitRunnable() {
            @Override
            public void run() {
                World world = org.bukkit.Bukkit.getWorld(plot.getTier().getWorldName());
                if (world == null) return;

                int minX = plot.getMinX();
                int minZ = plot.getMinZ();
                int maxX = plot.getMaxX();
                int maxZ = plot.getMaxZ();

                int batchSize = 5000;
                int count = 0;

                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int y = 65; y < world.getMaxHeight(); y++) {
                            Block block = world.getBlockAt(x, y, z);
                            if (block.getType() != Material.AIR) {
                                block.setType(Material.AIR, false);
                                count++;
                                if (count >= batchSize) {
                                    count = 0;
                                    try {
                                        Thread.sleep(50);
                                    } catch (InterruptedException ignored) {}
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public boolean disposePlot(UUID player, CreativePlot plot) {
        if (!plot.getOwner().equals(player)) return false;

        clearPlot(plot);

        String keyToRemove = null;
        for (Map.Entry<String, CreativePlot> entry : plots.entrySet()) {
            if (entry.getValue() == plot) {
                keyToRemove = entry.getKey();
                break;
            }
        }

        if (keyToRemove != null) {
            plots.remove(keyToRemove);
            List<String> pPlots = playerPlots.get(player);
            if (pPlots != null) {
                pPlots.remove(keyToRemove);
            }
        }

        savePlots();
        return true;
    }

    public CreativePlot getPlotAt(Location location) {
        for (CreativePlot plot : plots.values()) {
            if (plot.contains(location)) {
                return plot;
            }
        }
        return null;
    }

    public List<CreativePlot> getPlayerPlots(UUID player) {
        List<CreativePlot> result = new ArrayList<>();
        List<String> keys = playerPlots.get(player);
        if (keys == null) return result;
        for (String key : keys) {
            CreativePlot plot = plots.get(key);
            if (plot != null) {
                result.add(plot);
            }
        }
        return result;
    }

    public int getMaxWorldEditVolume() {
        return MAX_WORLDEDIT_VOLUME;
    }

    public Collection<CreativePlot> getAllPlots() {
        return plots.values();
    }
}

package com.aspireserver.buildbattle.arena;

import com.aspireserver.buildbattle.plot.PlotRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class Arena {

    private final String id;
    private final String worldName;
    private final List<PlotRegion> plots;
    private Location lobbySpawn;
    private boolean inUse;

    public Arena(String id, String worldName) {
        this.id = id;
        this.worldName = worldName;
        this.plots = new ArrayList<>();
        this.inUse = false;
    }

    public void addPlot(PlotRegion plot) {
        plots.add(plot);
    }

    public String getId() {
        return id;
    }

    public String getWorldName() {
        return worldName;
    }

    public List<PlotRegion> getPlots() {
        return plots;
    }

    public Location getLobbySpawn() {
        if (lobbySpawn == null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                return world.getSpawnLocation();
            }
        }
        return lobbySpawn;
    }

    public void setLobbySpawn(Location lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
    }

    public boolean isInUse() {
        return inUse;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }

    public int getMaxPlayers() {
        return plots.size();
    }
}

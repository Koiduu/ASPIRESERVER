package com.aspireserver.buildbattle.arena;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.game.GameMode;
import com.aspireserver.buildbattle.game.GameSession;
import com.aspireserver.buildbattle.game.GameState;
import com.aspireserver.buildbattle.plot.PlotRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaManager {

    private final AspireBuildBattle plugin;
    private final Map<String, Arena> arenas;
    private final Map<UUID, GameSession> playerSessions;
    private final List<GameSession> activeSessions;
    private Location waitingLobby;
    private File arenasFile;
    private FileConfiguration arenasConfig;

    public ArenaManager(AspireBuildBattle plugin) {
        this.plugin = plugin;
        this.arenas = new HashMap<>();
        this.playerSessions = new ConcurrentHashMap<>();
        this.activeSessions = Collections.synchronizedList(new ArrayList<>());
    }

    public void loadArenas() {
        arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        if (!arenasFile.exists()) {
            try {
                arenasFile.getParentFile().mkdirs();
                arenasFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create arenas.yml");
            }
        }
        arenasConfig = YamlConfiguration.loadConfiguration(arenasFile);

        ConfigurationSection section = arenasConfig.getConfigurationSection("arenas");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String worldName = section.getString(id + ".world", "world");
            Arena arena = new Arena(id, worldName);
            arena.setPlotType(section.getString(id + ".plotType", "solo"));

            if (section.contains(id + ".lobby")) {
                double lx = section.getDouble(id + ".lobby.x");
                double ly = section.getDouble(id + ".lobby.y");
                double lz = section.getDouble(id + ".lobby.z");
                arena.setLobbySpawn(new Location(
                    Bukkit.getWorld(worldName), lx, ly, lz));
            }

            ConfigurationSection plots = section.getConfigurationSection(id + ".plots");
            if (plots != null) {
                for (String plotKey : plots.getKeys(false)) {
                    int minX = plots.getInt(plotKey + ".minX");
                    int minY = plots.getInt(plotKey + ".minY");
                    int minZ = plots.getInt(plotKey + ".minZ");
                    int maxX = plots.getInt(plotKey + ".maxX");
                    int maxY = plots.getInt(plotKey + ".maxY");
                    int maxZ = plots.getInt(plotKey + ".maxZ");
                    arena.addPlot(new PlotRegion(worldName, minX, minY, minZ, maxX, maxY, maxZ));
                }
            }
            arenas.put(id, arena);
        }

        loadWaitingLobby();
        plugin.getLogger().info("Loaded " + arenas.size() + " arena(s).");
    }

    public void loadWaitingLobby() {
        String worldName = plugin.getConfig().getString("waiting-lobby.world");
        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                double x = plugin.getConfig().getDouble("waiting-lobby.x");
                double y = plugin.getConfig().getDouble("waiting-lobby.y");
                double z = plugin.getConfig().getDouble("waiting-lobby.z");
                float yaw = (float) plugin.getConfig().getDouble("waiting-lobby.yaw", 0);
                float pitch = (float) plugin.getConfig().getDouble("waiting-lobby.pitch", 0);
                waitingLobby = new Location(world, x, y, z, yaw, pitch);
            }
        }
    }

    public Location getWaitingLobby() {
        return waitingLobby;
    }

    public void saveArena(Arena arena) {
        String path = "arenas." + arena.getId();
        arenasConfig.set(path, null);
        arenasConfig.set(path + ".world", arena.getWorldName());
        arenasConfig.set(path + ".plotType", arena.getPlotType());

        if (arena.getLobbySpawn() != null) {
            arenasConfig.set(path + ".lobby.x", arena.getLobbySpawn().getX());
            arenasConfig.set(path + ".lobby.y", arena.getLobbySpawn().getY());
            arenasConfig.set(path + ".lobby.z", arena.getLobbySpawn().getZ());
        }

        for (int i = 0; i < arena.getPlots().size(); i++) {
            PlotRegion plot = arena.getPlots().get(i);
            String plotPath = path + ".plots." + i;
            arenasConfig.set(plotPath + ".minX", plot.getMinX());
            arenasConfig.set(plotPath + ".minY", plot.getMinY());
            arenasConfig.set(plotPath + ".minZ", plot.getMinZ());
            arenasConfig.set(plotPath + ".maxX", plot.getMaxX());
            arenasConfig.set(plotPath + ".maxY", plot.getMaxY());
            arenasConfig.set(plotPath + ".maxZ", plot.getMaxZ());
        }

        try {
            arenasConfig.save(arenasFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save arenas.yml");
        }
        arenas.put(arena.getId(), arena);
    }

    public Arena getAvailableArena() {
        for (Arena arena : arenas.values()) {
            if (!arena.isInUse() && !arena.getPlots().isEmpty()) {
                return arena;
            }
        }
        return null;
    }

    public Arena getAvailableArenaForMode(GameMode mode) {
        String requiredType = getPlotTypeForMode(mode);
        for (Arena arena : arenas.values()) {
            if (!arena.isInUse() && !arena.getPlots().isEmpty()
                && arena.getPlotType().equals(requiredType)) {
                return arena;
            }
        }
        return null;
    }

    private String getPlotTypeForMode(GameMode mode) {
        return switch (mode) {
            case SOLO -> "solo";
            case TEAMS -> "teams";
            case PRO_SOLO, PRO_TEAMS -> "pro";
        };
    }

    public GameSession createSession(Arena arena, GameMode mode) {
        arena.setInUse(true);
        GameSession session = new GameSession(plugin, arena, mode);
        activeSessions.add(session);
        return session;
    }

    public void joinSession(UUID player, GameSession session) {
        session.addPlayer(player);
        playerSessions.put(player, session);
    }

    public void leaveSession(UUID player) {
        GameSession session = playerSessions.remove(player);
        if (session != null) {
            session.removePlayer(player);
            if (session.getPlayers().isEmpty() && session.getState() != GameState.ENDING) {
                session.forceEnd();
                releaseArena(session.getArena());
            }
        }
    }

    public GameSession getPlayerSession(UUID player) {
        return playerSessions.get(player);
    }

    public void releaseArena(Arena arena) {
        arena.setInUse(false);
        activeSessions.removeIf(s -> s.getArena().equals(arena));
    }

    public Arena getArena(String id) {
        return arenas.get(id);
    }

    public Collection<Arena> getArenas() {
        return arenas.values();
    }

    public List<GameSession> getActiveSessions() {
        return activeSessions;
    }

    public void shutdown() {
        for (GameSession session : new ArrayList<>(activeSessions)) {
            session.forceEnd();
        }
        activeSessions.clear();
        playerSessions.clear();
    }
}

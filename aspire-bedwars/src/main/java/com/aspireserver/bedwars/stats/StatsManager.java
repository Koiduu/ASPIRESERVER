package com.aspireserver.bedwars.stats;

import com.aspireserver.bedwars.AspireBedwars;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Persistent per-player Bedwars stats (SQLite by default). All DB access runs
 * off the main thread; callers should treat writes as fire-and-forget.
 */
public class StatsManager {

    private final AspireBedwars plugin;
    private Connection connection;
    private boolean enabled = false;

    public StatsManager(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            File dbFile = new File(plugin.getDataFolder(), "stats.db");
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS stats (" +
                    "uuid TEXT PRIMARY KEY, name TEXT, coins INTEGER DEFAULT 0, " +
                    "wins INTEGER DEFAULT 0, losses INTEGER DEFAULT 0, kills INTEGER DEFAULT 0, " +
                    "final_kills INTEGER DEFAULT 0, beds_broken INTEGER DEFAULT 0, level INTEGER DEFAULT 1)")) {
                ps.executeUpdate();
            }
            enabled = true;
            plugin.getLogger().info("[Bedwars] Stats DB initialized (SQLite).");
        } catch (ClassNotFoundException | SQLException e) {
            enabled = false;
            plugin.getLogger().warning("[Bedwars] Stats disabled — could not init DB: " + e.getMessage());
        }
    }

    public void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
        }
    }

    public void ensurePlayer(UUID uuid, String name) {
        if (!enabled) return;
        runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO stats (uuid, name) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET name = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setString(3, name);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[Bedwars] stats ensurePlayer failed: " + e.getMessage());
            }
        });
    }

    public void increment(UUID uuid, String column, int amount) {
        if (!enabled) return;
        if (!isValidColumn(column)) return;
        runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE stats SET " + column + " = " + column + " + ? WHERE uuid = ?")) {
                ps.setInt(1, amount);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[Bedwars] stats increment failed: " + e.getMessage());
            }
        });
    }

    public int getStat(UUID uuid, String column) {
        if (!enabled || !isValidColumn(column)) return 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + column + " FROM stats WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Bedwars] stats read failed: " + e.getMessage());
        }
        return 0;
    }

    private boolean isValidColumn(String column) {
        return switch (column) {
            case "coins", "wins", "losses", "kills", "final_kills", "beds_broken", "level" -> true;
            default -> false;
        };
    }

    private void runAsync(Runnable r) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
    }

    public boolean isEnabled() { return enabled; }
}

package com.aspireserver.core.admin;

import com.aspireserver.core.AspireCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AdminManager {

    private final AspireCore plugin;
    private final File dataFile;
    private FileConfiguration data;
    private final Map<UUID, BanEntry> bans;
    private final Map<UUID, Long> mutes;
    private final Map<UUID, List<String>> warnings;

    public AdminManager(AspireCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "punishments.yml");
        this.bans = new HashMap<>();
        this.mutes = new HashMap<>();
        this.warnings = new HashMap<>();
        loadData();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create punishments.yml");
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (data.contains("bans")) {
            for (String key : data.getConfigurationSection("bans").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                String reason = data.getString("bans." + key + ".reason", "No reason");
                long expiry = data.getLong("bans." + key + ".expiry", -1);
                bans.put(uuid, new BanEntry(reason, expiry));
            }
        }

        if (data.contains("mutes")) {
            for (String key : data.getConfigurationSection("mutes").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                long expiry = data.getLong("mutes." + key);
                mutes.put(uuid, expiry);
            }
        }

        if (data.contains("warnings")) {
            for (String key : data.getConfigurationSection("warnings").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                List<String> warns = data.getStringList("warnings." + key);
                warnings.put(uuid, new ArrayList<>(warns));
            }
        }
    }

    public void saveData() {
        for (Map.Entry<UUID, BanEntry> entry : bans.entrySet()) {
            data.set("bans." + entry.getKey().toString() + ".reason", entry.getValue().reason());
            data.set("bans." + entry.getKey().toString() + ".expiry", entry.getValue().expiry());
        }
        for (Map.Entry<UUID, Long> entry : mutes.entrySet()) {
            data.set("mutes." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, List<String>> entry : warnings.entrySet()) {
            data.set("warnings." + entry.getKey().toString(), entry.getValue());
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save punishments.yml");
        }
    }

    public void ban(UUID uuid, String reason) {
        bans.put(uuid, new BanEntry(reason, -1));
        saveData();
    }

    public void tempBan(UUID uuid, String reason, long durationMs) {
        bans.put(uuid, new BanEntry(reason, System.currentTimeMillis() + durationMs));
        saveData();
    }

    public boolean isBanned(UUID uuid) {
        BanEntry entry = bans.get(uuid);
        if (entry == null) return false;
        if (entry.expiry() != -1 && System.currentTimeMillis() > entry.expiry()) {
            bans.remove(uuid);
            saveData();
            return false;
        }
        return true;
    }

    public String getBanReason(UUID uuid) {
        BanEntry entry = bans.get(uuid);
        return entry != null ? entry.reason() : "No reason";
    }

    public void unban(UUID uuid) {
        bans.remove(uuid);
        saveData();
    }

    public void mute(UUID uuid, long durationMs) {
        mutes.put(uuid, System.currentTimeMillis() + durationMs);
        saveData();
    }

    public boolean isMuted(UUID uuid) {
        Long expiry = mutes.get(uuid);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            mutes.remove(uuid);
            saveData();
            return false;
        }
        return true;
    }

    public void unmute(UUID uuid) {
        mutes.remove(uuid);
        saveData();
    }

    public void warn(UUID uuid, String reason) {
        warnings.computeIfAbsent(uuid, k -> new ArrayList<>()).add(reason);
        saveData();
    }

    public List<String> getWarnings(UUID uuid) {
        return warnings.getOrDefault(uuid, List.of());
    }

    public record BanEntry(String reason, long expiry) {}
}

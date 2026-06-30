package com.aspireserver.core.title;

import com.aspireserver.core.AspireCore;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TitleManager {

    private final AspireCore plugin;
    private final File dataFile;
    private FileConfiguration data;
    private final Map<UUID, BbTitle> playerTitles = new ConcurrentHashMap<>();

    public TitleManager(AspireCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "titles.yml");
        loadData();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create titles.yml");
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (data.contains("titles")) {
            for (String key : data.getConfigurationSection("titles").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                String titleName = data.getString("titles." + key, "ROOKIE");
                BbTitle title = BbTitle.fromString(titleName);
                if (title != null) {
                    playerTitles.put(uuid, title);
                }
            }
        }
    }

    public void saveData() {
        for (Map.Entry<UUID, BbTitle> entry : playerTitles.entrySet()) {
            data.set("titles." + entry.getKey().toString(), entry.getValue().name());
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save titles.yml");
        }
    }

    public BbTitle getTitle(UUID uuid) {
        return playerTitles.get(uuid);
    }

    public void setTitle(UUID uuid, BbTitle title) {
        if (title == null) {
            playerTitles.remove(uuid);
        } else {
            playerTitles.put(uuid, title);
        }
        saveData();
    }

    public void removeTitle(UUID uuid) {
        playerTitles.remove(uuid);
        saveData();
    }
}

package com.aspireserver.core.rank;

import com.aspireserver.core.AspireCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager {

    private final AspireCore plugin;
    private final File dataFile;
    private FileConfiguration data;
    private final Map<UUID, Rank> playerRanks = new ConcurrentHashMap<>();
    private final Map<UUID, TextColor> playerPlusColors = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNicks = new ConcurrentHashMap<>();

    public RankManager(AspireCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "ranks.yml");
        loadData();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create ranks.yml");
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (data.contains("ranks")) {
            for (String key : data.getConfigurationSection("ranks").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                String rankName = data.getString("ranks." + key + ".rank", "DEFAULT");
                playerRanks.put(uuid, Rank.fromString(rankName));

                String plusColorHex = data.getString("ranks." + key + ".plus-color", null);
                if (plusColorHex != null) {
                    playerPlusColors.put(uuid, TextColor.fromHexString(plusColorHex));
                }

                String nick = data.getString("ranks." + key + ".nick", null);
                if (nick != null) {
                    playerNicks.put(uuid, nick);
                }
            }
        }
    }

    public void saveData() {
        for (Map.Entry<UUID, Rank> entry : playerRanks.entrySet()) {
            String path = "ranks." + entry.getKey().toString();
            data.set(path + ".rank", entry.getValue().name());

            TextColor plusColor = playerPlusColors.get(entry.getKey());
            if (plusColor != null) {
                data.set(path + ".plus-color", plusColor.asHexString());
            }

            String nick = playerNicks.get(entry.getKey());
            if (nick != null) {
                data.set(path + ".nick", nick);
            }
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save ranks.yml");
        }
    }

    public Rank getRank(UUID uuid) {
        return playerRanks.getOrDefault(uuid, Rank.DEFAULT);
    }

    public void setRank(UUID uuid, Rank rank) {
        playerRanks.put(uuid, rank);
        saveData();
    }

    public TextColor getPlusColor(UUID uuid) {
        return playerPlusColors.get(uuid);
    }

    public void setPlusColor(UUID uuid, TextColor color) {
        playerPlusColors.put(uuid, color);
        saveData();
    }

    public String getNick(UUID uuid) {
        return playerNicks.get(uuid);
    }

    public void setNick(UUID uuid, String nick) {
        if (nick == null) {
            playerNicks.remove(uuid);
        } else {
            playerNicks.put(uuid, nick);
        }
        saveData();
    }

    public Component getDisplayName(Player player) {
        UUID uuid = player.getUniqueId();
        Rank rank = getRank(uuid);
        TextColor plusColor = getPlusColor(uuid);
        String nick = getNick(uuid);
        String name = nick != null ? nick : player.getName();

        Component tag = rank.formatTag(plusColor);
        TextColor nameColor = rank == Rank.DEFAULT ? NamedTextColor.GRAY : rank.getBaseColor();

        return tag.append(Component.text(name, nameColor));
    }

    public Component getChatFormat(Player player, String message) {
        return getDisplayName(player)
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(Component.text(message, NamedTextColor.WHITE));
    }
}

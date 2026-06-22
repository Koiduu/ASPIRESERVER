package com.aspireserver.core.friends;

import com.aspireserver.core.AspireCore;
import com.aspireserver.core.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FriendManager {

    private final AspireCore plugin;
    private final File dataFile;
    private FileConfiguration data;
    private final Map<UUID, Set<UUID>> friends;
    private final Map<UUID, Set<UUID>> pendingRequests;

    public FriendManager(AspireCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "friends.yml");
        this.friends = new HashMap<>();
        this.pendingRequests = new HashMap<>();
        loadData();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create friends.yml");
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (data.contains("friends")) {
            for (String key : data.getConfigurationSection("friends").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                List<String> friendList = data.getStringList("friends." + key);
                Set<UUID> set = new HashSet<>();
                for (String s : friendList) {
                    set.add(UUID.fromString(s));
                }
                friends.put(uuid, set);
            }
        }
    }

    public void saveData() {
        for (Map.Entry<UUID, Set<UUID>> entry : friends.entrySet()) {
            List<String> list = new ArrayList<>();
            for (UUID uuid : entry.getValue()) {
                list.add(uuid.toString());
            }
            data.set("friends." + entry.getKey().toString(), list);
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save friends.yml");
        }
    }

    public Set<UUID> getFriends(UUID player) {
        return friends.getOrDefault(player, new HashSet<>());
    }

    public boolean areFriends(UUID a, UUID b) {
        return getFriends(a).contains(b);
    }

    public void sendRequest(Player sender, Player target) {
        UUID sUuid = sender.getUniqueId();
        UUID tUuid = target.getUniqueId();

        if (sUuid.equals(tUuid)) {
            MessageUtil.sendError(sender, "You cannot friend yourself!");
            return;
        }

        if (areFriends(sUuid, tUuid)) {
            MessageUtil.sendError(sender, "You are already friends with " + target.getName() + "!");
            return;
        }

        Set<UUID> pending = pendingRequests.getOrDefault(tUuid, new HashSet<>());
        if (pending.contains(sUuid)) {
            MessageUtil.sendError(sender, "You already sent a request to " + target.getName() + "!");
            return;
        }

        Set<UUID> myPending = pendingRequests.getOrDefault(sUuid, new HashSet<>());
        if (myPending.contains(tUuid)) {
            acceptRequest(sender, target);
            return;
        }

        pending.add(sUuid);
        pendingRequests.put(tUuid, pending);
        MessageUtil.sendSuccess(sender, "Friend request sent to " + target.getName() + "!");
        MessageUtil.sendInfo(target, sender.getName() + " sent you a friend request! Use /friend " + sender.getName() + " to accept.");
    }

    public void acceptRequest(Player accepter, Player requester) {
        UUID aUuid = accepter.getUniqueId();
        UUID rUuid = requester.getUniqueId();

        friends.computeIfAbsent(aUuid, k -> new HashSet<>()).add(rUuid);
        friends.computeIfAbsent(rUuid, k -> new HashSet<>()).add(aUuid);

        pendingRequests.getOrDefault(aUuid, new HashSet<>()).remove(rUuid);

        MessageUtil.sendSuccess(accepter, "You are now friends with " + requester.getName() + "!");
        MessageUtil.sendSuccess(requester, accepter.getName() + " accepted your friend request!");
        saveData();
    }

    public void removeFriend(Player player, UUID targetUuid) {
        UUID pUuid = player.getUniqueId();
        if (!areFriends(pUuid, targetUuid)) {
            MessageUtil.sendError(player, "That player is not your friend!");
            return;
        }

        friends.getOrDefault(pUuid, new HashSet<>()).remove(targetUuid);
        friends.getOrDefault(targetUuid, new HashSet<>()).remove(pUuid);
        MessageUtil.sendSuccess(player, "Friend removed.");
        saveData();
    }

    public void listFriends(Player player) {
        Set<UUID> friendSet = getFriends(player.getUniqueId());
        if (friendSet.isEmpty()) {
            MessageUtil.send(player, "You have no friends yet.");
            return;
        }
        MessageUtil.sendInfo(player, "--- Friends (" + friendSet.size() + ") ---");
        for (UUID uuid : friendSet) {
            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : Bukkit.getOfflinePlayer(uuid).getName();
            String status = p != null ? " (Online)" : " (Offline)";
            MessageUtil.send(player, "  " + (name != null ? name : uuid.toString()) + status);
        }
    }
}

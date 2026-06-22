package com.aspireserver.core.friends;

import com.aspireserver.core.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FriendCommand implements CommandExecutor, TabCompleter {

    private final FriendManager friendManager;

    public FriendCommand(FriendManager friendManager) {
        this.friendManager = friendManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "remove" -> {
                if (args.length < 2) {
                    MessageUtil.sendError(player, "Usage: /friend remove <player>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                friendManager.removeFriend(player, target.getUniqueId());
            }
            case "list" -> friendManager.listFriends(player);
            default -> {
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    MessageUtil.sendError(player, "Player not online!");
                    return true;
                }
                friendManager.sendRequest(player, target);
            }
        }
        return true;
    }

    private void sendUsage(Player player) {
        MessageUtil.sendInfo(player, "--- Friend Commands ---");
        MessageUtil.send(player, "/friend <player> - Send/accept request");
        MessageUtil.send(player, "/friend remove <player> - Remove friend");
        MessageUtil.send(player, "/friend list - View friends");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("remove", "list"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
            return completions.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}

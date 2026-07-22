package com.aspireserver.core.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class NickCommand implements CommandExecutor, TabCompleter {

    private final RankManager rankManager;

    public NickCommand(RankManager rankManager) {
        this.rankManager = rankManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Console cannot use this command.");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage(Component.text("Only ops can use /nick.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /nick <name|reset>", NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("off")) {
            rankManager.setNick(player.getUniqueId(), null);
            Component displayName = rankManager.getDisplayName(player);
            player.displayName(displayName);
            player.playerListName(displayName);
            player.sendMessage(Component.text("Nickname reset.", NamedTextColor.GREEN));
            return true;
        }

        String nick = String.join(" ", args);
        if (nick.length() > 16) {
            player.sendMessage(Component.text("Nickname too long (max 16 chars).", NamedTextColor.RED));
            return true;
        }

        rankManager.setNick(player.getUniqueId(), nick);
        Component displayName = rankManager.getDisplayName(player);
        player.displayName(displayName);
        player.playerListName(displayName);
        player.sendMessage(Component.text("Nickname set to: ", NamedTextColor.GREEN).append(displayName));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reset");
        }
        return List.of();
    }
}

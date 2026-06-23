package com.aspireserver.buildbattle.commands;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.arena.Arena;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LobbyPlotCommand implements CommandExecutor, TabCompleter {

    private final AspireBuildBattle plugin;

    public LobbyPlotCommand(AspireBuildBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("aspire.buildbattle.admin")) {
            player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        String type = switch (label.toLowerCase()) {
            case "soloplot" -> "solo";
            case "teamplot" -> "teams";
            case "proplot" -> "pro";
            default -> "solo";
        };

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /" + label + " <number>", NamedTextColor.RED));
            player.sendMessage(Component.text("Example: /" + label + " 1 — manages lobby '" + type + "-1'", NamedTextColor.GRAY));
            return true;
        }

        int lobbyNum;
        try {
            lobbyNum = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid lobby number!", NamedTextColor.RED));
            return true;
        }

        String arenaId = type + "-" + lobbyNum;
        Arena arena = plugin.getArenaManager().getArena(arenaId);

        if (arena == null) {
            arena = new Arena(arenaId, player.getWorld().getName());
            arena.setPlotType(type);
            plugin.getArenaManager().saveArena(arena);
            player.sendMessage(Component.text("Created new arena: " + arenaId, NamedTextColor.GREEN));
        }

        player.sendMessage(Component.text("--- Arena: " + arenaId + " [" + type + "] ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Plots: " + arena.getPlots().size(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("In use: " + arena.isInUse(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("Use /plotset " + arenaId + " to add plots to this arena.", NamedTextColor.YELLOW));

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return List.of("1", "2", "3", "4", "5").stream()
                .filter(s -> s.startsWith(args[0])).toList();
        }
        return List.of();
    }
}

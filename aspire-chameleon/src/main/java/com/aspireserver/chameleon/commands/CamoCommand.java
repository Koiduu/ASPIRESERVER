package com.aspireserver.chameleon.commands;

import com.aspireserver.chameleon.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CamoCommand implements CommandExecutor, TabCompleter {

    private final GameManager gameManager;

    public CamoCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (!player.hasPermission("aspire.chameleon.admin")) {
                    player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /camo start <map_name>", NamedTextColor.RED));
                    return true;
                }
                if (gameManager.isGameActive()) {
                    player.sendMessage(Component.text("A game is already active! Use /camo stop first.", NamedTextColor.RED));
                    return true;
                }
                String mapName = args[1].toLowerCase();
                if (gameManager.startGame(mapName)) {
                    player.sendMessage(Component.text("Game started on map: " + mapName, NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Failed to start! Check if map '" + mapName + "' exists in maps.yml with skins configured.", NamedTextColor.RED));
                }
            }
            case "stop" -> {
                if (!player.hasPermission("aspire.chameleon.admin")) {
                    player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                if (!gameManager.isGameActive()) {
                    player.sendMessage(Component.text("No game is currently active.", NamedTextColor.RED));
                    return true;
                }
                gameManager.stopGame();
                player.sendMessage(Component.text("Game stopped.", NamedTextColor.GREEN));
            }
            case "menu" -> {
                if (!gameManager.isGameActive()) {
                    player.sendMessage(Component.text("No game is currently active.", NamedTextColor.RED));
                    return true;
                }
                if (!gameManager.isGracePeriodActive()) {
                    player.sendMessage(Component.text("The grace period has ended! You can no longer change skins.", NamedTextColor.RED));
                    return true;
                }
                gameManager.openSkinMenu(player);
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== Chameleon Commands ===", NamedTextColor.GREEN));
        player.sendMessage(Component.text("/camo start <map> - Start a game", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/camo stop - Stop the current game", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/camo menu - Open skin selection (during grace period)", NamedTextColor.AQUA));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("start");
            completions.add("stop");
            completions.add("menu");
        } else if (args.length == 2 && "start".equalsIgnoreCase(args[0])) {
            completions.addAll(gameManager.isGameActive() ? List.of() :
                    com.aspireserver.chameleon.AspireChameleon.getInstance().getConfigManager().getMapNames());
        }
        String prefix = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(prefix));
        return completions;
    }
}

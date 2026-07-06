package com.aspireserver.chameleon.commands;

import com.aspireserver.chameleon.config.ConfigManager;
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

public class SpawnCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;

    public SpawnCommand(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("aspire.chameleon.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("setchameleonspawn")) {
            if (args.length < 1) {
                player.sendMessage(Component.text("Usage: /setchameleonspawn <mapId>", NamedTextColor.RED));
                return true;
            }
            String mapId = args[0].toLowerCase();
            configManager.setMapSpawn(mapId, player.getLocation());
            player.sendMessage(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                    .append(Component.text("Spawn set for map '" + mapId + "' at your location.", NamedTextColor.YELLOW)));
        } else if (cmdName.equals("removechameleonspawn")) {
            if (args.length < 1) {
                player.sendMessage(Component.text("Usage: /removechameleonspawn <mapId>", NamedTextColor.RED));
                return true;
            }
            String mapId = args[0].toLowerCase();
            if (configManager.getMapSpawn(mapId) == null) {
                player.sendMessage(Component.text("No spawn set for map '" + mapId + "'.", NamedTextColor.RED));
                return true;
            }
            configManager.removeMapSpawn(mapId);
            player.sendMessage(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                    .append(Component.text("Spawn removed for map '" + mapId + "'.", NamedTextColor.YELLOW)));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(configManager.getAllMapIds());
        }
        String prefix = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(prefix));
        return completions;
    }
}

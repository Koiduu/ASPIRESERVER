package com.aspireserver.buildbattle.commands;

import com.aspireserver.buildbattle.AspireBuildBattle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SpawnSetWaitingCommand implements CommandExecutor, TabCompleter {

    private final AspireBuildBattle plugin;

    public SpawnSetWaitingCommand(AspireBuildBattle plugin) {
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

        Location loc = player.getLocation();

        if (args.length >= 1) {
            String mode = args[0].toLowerCase();
            if (!mode.equals("solo") && !mode.equals("teams") && !mode.equals("pro")) {
                player.sendMessage(Component.text("Usage: /spawnwaiting <solo|teams|pro> or /spawnwaiting (for global)", NamedTextColor.RED));
                return true;
            }
            String path = "waiting-lobby." + mode;
            plugin.getConfig().set(path + ".world", loc.getWorld().getName());
            plugin.getConfig().set(path + ".x", loc.getX());
            plugin.getConfig().set(path + ".y", loc.getY());
            plugin.getConfig().set(path + ".z", loc.getZ());
            plugin.getConfig().set(path + ".yaw", loc.getYaw());
            plugin.getConfig().set(path + ".pitch", loc.getPitch());
            plugin.saveConfig();
            plugin.getArenaManager().loadWaitingLobbies();
            player.sendMessage(Component.text("Waiting lobby for " + mode.toUpperCase() + " set!", NamedTextColor.GREEN));
        } else {
            plugin.getConfig().set("waiting-lobby.global.world", loc.getWorld().getName());
            plugin.getConfig().set("waiting-lobby.global.x", loc.getX());
            plugin.getConfig().set("waiting-lobby.global.y", loc.getY());
            plugin.getConfig().set("waiting-lobby.global.z", loc.getZ());
            plugin.getConfig().set("waiting-lobby.global.yaw", loc.getYaw());
            plugin.getConfig().set("waiting-lobby.global.pitch", loc.getPitch());
            plugin.saveConfig();
            plugin.getArenaManager().loadWaitingLobbies();
            player.sendMessage(Component.text("Global waiting lobby spawn set!", NamedTextColor.GREEN));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return List.of("solo", "teams", "pro").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}

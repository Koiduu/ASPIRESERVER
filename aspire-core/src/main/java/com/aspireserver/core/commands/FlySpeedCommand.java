package com.aspireserver.core.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FlySpeedCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /flyspeed <1-5>", NamedTextColor.RED));
            return true;
        }

        int speed;
        try {
            speed = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Speed must be a number between 1 and 5!", NamedTextColor.RED));
            return true;
        }

        if (speed < 1 || speed > 5) {
            player.sendMessage(Component.text("Speed must be between 1 and 5!", NamedTextColor.RED));
            return true;
        }

        // Minecraft fly speed: 0.1 = normal (1), max 1.0 = very fast
        // Map 1-5 to 0.1, 0.25, 0.5, 0.75, 1.0
        float flySpeed = switch (speed) {
            case 1 -> 0.1f;
            case 2 -> 0.25f;
            case 3 -> 0.5f;
            case 4 -> 0.75f;
            case 5 -> 1.0f;
            default -> 0.1f;
        };

        player.setFlySpeed(flySpeed);
        player.sendMessage(Component.text("Fly speed set to " + speed + "!", NamedTextColor.GREEN));
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

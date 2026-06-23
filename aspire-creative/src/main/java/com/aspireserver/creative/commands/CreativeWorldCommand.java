package com.aspireserver.creative.commands;

import com.aspireserver.creative.AspireCreative;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CreativeWorldCommand implements CommandExecutor, TabCompleter {

    private final AspireCreative plugin;

    public CreativeWorldCommand(AspireCreative plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("aspire.admin.creative")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Creative world config:", NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  Small: " + plugin.getConfig().getString("worlds.small", "creative_small"), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Medium: " + plugin.getConfig().getString("worlds.medium", "creative_medium"), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Large: " + plugin.getConfig().getString("worlds.large", "creative_large"), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Usage: /creativeworld <small|medium|large> <worldName>", NamedTextColor.GRAY));
            return true;
        }

        String size = args[0].toLowerCase();
        if (!size.equals("small") && !size.equals("medium") && !size.equals("large")) {
            sender.sendMessage(Component.text("Size must be small, medium, or large!", NamedTextColor.RED));
            return true;
        }

        String worldName = args[1];
        plugin.getConfig().set("worlds." + size, worldName);
        plugin.saveConfig();

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            sender.sendMessage(Component.text("Creative " + size + " world set to '" + worldName + "' (loaded)", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Creative " + size + " world set to '" + worldName + "' — world not loaded yet. It will be used when available.", NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return List.of("small", "medium", "large").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2) {
            return Bukkit.getWorlds().stream()
                .map(World::getName)
                .filter(n -> n.startsWith(args[1].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}

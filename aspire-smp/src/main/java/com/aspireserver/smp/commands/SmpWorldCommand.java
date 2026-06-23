package com.aspireserver.smp.commands;

import com.aspireserver.smp.AspireSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SmpWorldCommand implements CommandExecutor, TabCompleter {

    private final AspireSMP plugin;

    public SmpWorldCommand(AspireSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("aspire.admin.smp")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            String current = plugin.getConfig().getString("smp-world", "not set");
            sender.sendMessage(Component.text("Current SMP world: " + current, NamedTextColor.AQUA));
            sender.sendMessage(Component.text("Usage: /smpworld <worldName> — set the SMP world", NamedTextColor.GRAY));
            return true;
        }

        String worldName = args[0];
        plugin.getConfig().set("smp-world", worldName);
        plugin.saveConfig();

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            sender.sendMessage(Component.text("SMP world set to '" + worldName + "' (loaded)", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("SMP world set to '" + worldName + "' — world not loaded yet. It will be used when available.", NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return Bukkit.getWorlds().stream()
                .map(World::getName)
                .filter(n -> n.startsWith(args[0].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}

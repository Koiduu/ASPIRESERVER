package com.aspireserver.buildbattle.commands;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.arena.Arena;
import com.aspireserver.buildbattle.listeners.PlotToolListener;
import com.aspireserver.buildbattle.plot.PlotRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PlotSetCommand implements CommandExecutor, TabCompleter {

    private final AspireBuildBattle plugin;

    public PlotSetCommand(AspireBuildBattle plugin) {
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

        if (args.length < 1) {
            String currentType = TogglePlotTypeCommand.getPlotType(player.getUniqueId());
            player.sendMessage(Component.text("Usage: /plotset <arenaId>", NamedTextColor.RED));
            player.sendMessage(Component.text("Current plot type: " + currentType.toUpperCase(), NamedTextColor.GRAY));
            player.sendMessage(Component.text("Use /togglesolo, /toggleteams, /togglepro to change type", NamedTextColor.GRAY));
            player.sendMessage(Component.text("Example: /plotset solo-1  (adds a plot to arena 'solo-1')", NamedTextColor.GRAY));
            return true;
        }

        String arenaId = args[0];

        PlotToolListener toolListener = null;
        for (var listener : HandlerList.getRegisteredListeners(plugin)) {
            if (listener.getListener() instanceof PlotToolListener ptl) {
                toolListener = ptl;
                break;
            }
        }

        if (toolListener == null) {
            player.sendMessage(Component.text("Internal error: tool listener not found!", NamedTextColor.RED));
            return true;
        }

        Location pos1 = toolListener.getPos1(player.getUniqueId());
        Location pos2 = toolListener.getPos2(player.getUniqueId());

        if (pos1 == null || pos2 == null) {
            player.sendMessage(Component.text("Set both positions first! Use a Carrot.", NamedTextColor.RED));
            return true;
        }

        if (!pos1.getWorld().equals(pos2.getWorld())) {
            player.sendMessage(Component.text("Both positions must be in the same world!", NamedTextColor.RED));
            return true;
        }

        String plotType = TogglePlotTypeCommand.getPlotType(player.getUniqueId());

        Arena arena = plugin.getArenaManager().getArena(arenaId);
        if (arena == null) {
            arena = new Arena(arenaId, pos1.getWorld().getName());
            arena.setPlotType(plotType);
        }

        PlotRegion plot = new PlotRegion(
            pos1.getWorld().getName(),
            pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
            pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ()
        );

        arena.addPlot(plot);
        plugin.getArenaManager().saveArena(arena);

        toolListener.clearPositions(player.getUniqueId());

        player.sendMessage(Component.text("Plot added to arena '" + arenaId + "' [" + arena.getPlotType() + "]! ("
            + arena.getPlots().size() + " total plots)", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return plugin.getArenaManager().getArenas().stream()
                .map(a -> a.getId())
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}

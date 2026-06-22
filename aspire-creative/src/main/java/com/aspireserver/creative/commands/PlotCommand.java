package com.aspireserver.creative.commands;

import com.aspireserver.creative.plot.CreativePlot;
import com.aspireserver.creative.plot.PlotManager;
import com.aspireserver.creative.plot.PlotTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PlotCommand implements CommandExecutor, TabCompleter {

    private final PlotManager plotManager;

    public PlotCommand(PlotManager plotManager) {
        this.plotManager = plotManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            openPlotMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "claim" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /plot claim <small|medium|large>", NamedTextColor.RED));
                    return true;
                }
                PlotTier tier = parseTier(args[1]);
                if (tier == null) {
                    player.sendMessage(Component.text("Invalid tier! Use: small, medium, large", NamedTextColor.RED));
                    return true;
                }
                handleClaim(player, tier);
            }
            case "home", "tp" -> handleTeleport(player, args);
            case "clear" -> handleClear(player);
            case "dispose", "delete" -> handleDispose(player);
            case "add" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /plot add <player>", NamedTextColor.RED));
                    return true;
                }
                handleAddMember(player, args[1]);
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /plot remove <player>", NamedTextColor.RED));
                    return true;
                }
                handleRemoveMember(player, args[1]);
            }
            case "list" -> handleList(player);
            case "info" -> handleInfo(player);
            default -> openPlotMenu(player);
        }
        return true;
    }

    private void handleClaim(Player player, PlotTier tier) {
        List<CreativePlot> existing = plotManager.getPlayerPlots(player.getUniqueId());
        int maxPlots = 3;
        if (existing.size() >= maxPlots) {
            player.sendMessage(Component.text("You have reached the max plot limit (" + maxPlots + ")!", NamedTextColor.RED));
            return;
        }

        CreativePlot plot = plotManager.claimPlot(player.getUniqueId(), tier);
        player.sendMessage(Component.text("Claimed a " + tier.getDisplayName() + " plot! (" + tier.getSize() + "x" + tier.getSize() + ")", NamedTextColor.GREEN));

        if (plot.getSpawnLocation() != null) {
            player.teleport(plot.getSpawnLocation());
        }
    }

    private void handleTeleport(Player player, String[] args) {
        List<CreativePlot> plots = plotManager.getPlayerPlots(player.getUniqueId());
        if (plots.isEmpty()) {
            player.sendMessage(Component.text("You have no plots! Use /plot claim <tier>", NamedTextColor.RED));
            return;
        }

        int index = 0;
        if (args.length >= 2) {
            try {
                index = Integer.parseInt(args[1]) - 1;
            } catch (NumberFormatException ignored) {}
        }

        if (index < 0 || index >= plots.size()) {
            player.sendMessage(Component.text("Invalid plot number!", NamedTextColor.RED));
            return;
        }

        CreativePlot plot = plots.get(index);
        if (plot.getSpawnLocation() != null) {
            player.teleport(plot.getSpawnLocation());
            player.sendMessage(Component.text("Teleported to plot #" + (index + 1), NamedTextColor.GREEN));
        }
    }

    private void handleClear(Player player) {
        CreativePlot plot = plotManager.getPlotAt(player.getLocation());
        if (plot == null || !plot.canAccess(player.getUniqueId())) {
            player.sendMessage(Component.text("You must be standing on your plot!", NamedTextColor.RED));
            return;
        }

        plotManager.clearPlot(plot);
        player.sendMessage(Component.text("Plot cleared! (async)", NamedTextColor.GREEN));
    }

    private void handleDispose(Player player) {
        CreativePlot plot = plotManager.getPlotAt(player.getLocation());
        if (plot == null || !plot.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You must be standing on a plot you own!", NamedTextColor.RED));
            return;
        }

        plotManager.disposePlot(player.getUniqueId(), plot);
        player.sendMessage(Component.text("Plot disposed and cleared.", NamedTextColor.GREEN));
    }

    private void handleAddMember(Player player, String targetName) {
        CreativePlot plot = plotManager.getPlotAt(player.getLocation());
        if (plot == null || !plot.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You must be standing on a plot you own!", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
            return;
        }

        plot.addMember(target.getUniqueId());
        plotManager.savePlots();
        player.sendMessage(Component.text("Added " + target.getName() + " to your plot!", NamedTextColor.GREEN));
    }

    private void handleRemoveMember(Player player, String targetName) {
        CreativePlot plot = plotManager.getPlotAt(player.getLocation());
        if (plot == null || !plot.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You must be standing on a plot you own!", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
            return;
        }

        plot.removeMember(target.getUniqueId());
        plotManager.savePlots();
        player.sendMessage(Component.text("Removed " + target.getName() + " from your plot.", NamedTextColor.GREEN));
    }

    private void handleList(Player player) {
        List<CreativePlot> plots = plotManager.getPlayerPlots(player.getUniqueId());
        if (plots.isEmpty()) {
            player.sendMessage(Component.text("You have no plots.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("--- Your Plots ---", NamedTextColor.GOLD));
        for (int i = 0; i < plots.size(); i++) {
            CreativePlot p = plots.get(i);
            player.sendMessage(Component.text((i + 1) + ". " + p.getTier().getDisplayName()
                + " (" + p.getTier().getSize() + "x" + p.getTier().getSize() + ")", NamedTextColor.GRAY));
        }
    }

    private void handleInfo(Player player) {
        CreativePlot plot = plotManager.getPlotAt(player.getLocation());
        if (plot == null) {
            player.sendMessage(Component.text("Not standing on a plot.", NamedTextColor.GRAY));
            return;
        }
        String ownerName = plot.getOwner() != null ?
            Bukkit.getOfflinePlayer(plot.getOwner()).getName() : "Unclaimed";
        player.sendMessage(Component.text("--- Plot Info ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Owner: " + ownerName, NamedTextColor.GRAY));
        player.sendMessage(Component.text("Tier: " + plot.getTier().getDisplayName(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("Size: " + plot.getTier().getSize() + "x" + plot.getTier().getSize(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("Members: " + plot.getMembers().size(), NamedTextColor.GRAY));
    }

    private void openPlotMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Plot Menu", NamedTextColor.GOLD));

        gui.setItem(10, createItem(Material.OAK_PLANKS, "Small Plot (75x75)", NamedTextColor.GREEN,
            "Click to claim a small plot"));
        gui.setItem(12, createItem(Material.STONE_BRICKS, "Medium Plot (251x251)", NamedTextColor.YELLOW,
            "Click to claim a medium plot"));
        gui.setItem(14, createItem(Material.DIAMOND_BLOCK, "Large Plot (501x501)", NamedTextColor.AQUA,
            "Click to claim a large plot"));
        gui.setItem(16, createItem(Material.COMPASS, "Teleport to Plot", NamedTextColor.WHITE,
            "Use /plot home"));

        player.openInventory(gui);
    }

    private ItemStack createItem(Material material, String name, NamedTextColor color, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private PlotTier parseTier(String input) {
        return switch (input.toLowerCase()) {
            case "small", "s" -> PlotTier.SMALL;
            case "medium", "m" -> PlotTier.MEDIUM;
            case "large", "l" -> PlotTier.LARGE;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return List.of("claim", "home", "clear", "dispose", "add", "remove", "list", "info").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("claim")) {
            return List.of("small", "medium", "large").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}

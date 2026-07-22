package com.aspireserver.core.loadout;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LoadoutCommand implements CommandExecutor, TabCompleter {

    private final LoadoutManager manager;

    public LoadoutCommand(LoadoutManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Loadout Commands:", NamedTextColor.GOLD));
            player.sendMessage(Component.text("  /loadout save <name> — Save current inventory", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /loadout load <name> — Load a saved inventory", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /loadout delete <name> — Delete a loadout", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /loadout list — List all loadouts", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /loadout auto <name> — Auto-load on join", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /loadout auto off — Disable auto-load", NamedTextColor.GRAY));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "save" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /loadout save <name>", NamedTextColor.RED));
                    return true;
                }
                String name = args[1].toLowerCase();
                Set<String> existing = manager.getLoadoutNames(player.getUniqueId());
                if (existing.size() >= 5 && !existing.contains(name)) {
                    player.sendMessage(Component.text("Max 5 loadouts! Delete one first.", NamedTextColor.RED));
                    return true;
                }
                manager.saveLoadout(player.getUniqueId(), name, player.getInventory().getContents());
                player.sendMessage(Component.text("Loadout '" + name + "' saved!", NamedTextColor.GREEN));
            }
            case "load" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /loadout load <name>", NamedTextColor.RED));
                    return true;
                }
                String name = args[1].toLowerCase();
                ItemStack[] contents = manager.getLoadout(player.getUniqueId(), name);
                if (contents == null) {
                    player.sendMessage(Component.text("Loadout '" + name + "' not found!", NamedTextColor.RED));
                    return true;
                }
                player.getInventory().setContents(contents);
                player.sendMessage(Component.text("Loadout '" + name + "' loaded!", NamedTextColor.GREEN));
            }
            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /loadout delete <name>", NamedTextColor.RED));
                    return true;
                }
                String name = args[1].toLowerCase();
                if (manager.deleteLoadout(player.getUniqueId(), name)) {
                    player.sendMessage(Component.text("Loadout '" + name + "' deleted!", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Loadout '" + name + "' not found!", NamedTextColor.RED));
                }
            }
            case "list" -> {
                Set<String> names = manager.getLoadoutNames(player.getUniqueId());
                if (names.isEmpty()) {
                    player.sendMessage(Component.text("No saved loadouts. Use /loadout save <name>", NamedTextColor.GRAY));
                } else {
                    player.sendMessage(Component.text("Your Loadouts:", NamedTextColor.GOLD));
                    String autoLoadout = manager.getAutoLoadout(player.getUniqueId());
                    for (String n : names) {
                        String suffix = n.equals(autoLoadout) ? " (auto)" : "";
                        player.sendMessage(Component.text("  - " + n + suffix, NamedTextColor.AQUA));
                    }
                }
            }
            case "auto" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /loadout auto <name|off>", NamedTextColor.RED));
                    return true;
                }
                String name = args[1].toLowerCase();
                if (name.equals("off")) {
                    manager.clearAutoLoadout(player.getUniqueId());
                    player.sendMessage(Component.text("Auto-loadout disabled.", NamedTextColor.YELLOW));
                } else {
                    ItemStack[] contents = manager.getLoadout(player.getUniqueId(), name);
                    if (contents == null) {
                        player.sendMessage(Component.text("Loadout '" + name + "' not found! Save it first.", NamedTextColor.RED));
                        return true;
                    }
                    manager.setAutoLoadout(player.getUniqueId(), name);
                    player.sendMessage(Component.text("Loadout '" + name + "' will auto-load on join!", NamedTextColor.GREEN));
                }
            }
            default -> {
                player.sendMessage(Component.text("Unknown sub-command. Use /loadout for help.", NamedTextColor.RED));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            return List.of("save", "load", "delete", "list", "auto").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("load") || sub.equals("delete") || sub.equals("auto")) {
                List<String> names = new ArrayList<>(manager.getLoadoutNames(player.getUniqueId()));
                if (sub.equals("auto")) names.add("off");
                return names.stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
            }
        }
        return List.of();
    }
}

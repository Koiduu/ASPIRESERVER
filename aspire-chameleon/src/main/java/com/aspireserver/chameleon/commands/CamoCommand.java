package com.aspireserver.chameleon.commands;

import com.aspireserver.chameleon.AspireChameleon;
import com.aspireserver.chameleon.config.ConfigManager;
import com.aspireserver.chameleon.config.ConfigManager.SkinEntry;
import com.aspireserver.chameleon.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
                    player.sendMessage(Component.text("Failed to start! A game may already be running.", NamedTextColor.RED));
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
            case "skins" -> {
                if (!player.hasPermission("aspire.chameleon.admin")) {
                    player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /camo skins <map_name>", NamedTextColor.RED));
                    return true;
                }
                openSkinsGui(player, args[1].toLowerCase());
            }
            case "addskin" -> {
                if (!player.hasPermission("aspire.chameleon.admin")) {
                    player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 4) {
                    player.sendMessage(Component.text("Usage: /camo addskin <map_name> <skinName> <playerUUID>", NamedTextColor.RED));
                    player.sendMessage(Component.text("  skinName = display name (e.g. \"Oak_Planks\")", NamedTextColor.GRAY));
                    player.sendMessage(Component.text("  playerUUID = UUID of MC account with that skin", NamedTextColor.GRAY));
                    return true;
                }
                String map = args[1].toLowerCase();
                String skinDisplayName = args[2].replace("_", " ");
                String uuid = args[3].replace("-", "");
                ConfigManager cfg = AspireChameleon.getInstance().getConfigManager();
                cfg.addSkin(map, skinDisplayName, uuid);
                AspireChameleon.getInstance().getSkinCache().fetchSkinByUuid(uuid, skinDisplayName);
                player.sendMessage(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                        .append(Component.text("Added skin '" + skinDisplayName + "' to map '" + map + "'.", NamedTextColor.YELLOW)));
            }
            case "removeskin" -> {
                if (!player.hasPermission("aspire.chameleon.admin")) {
                    player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /camo removeskin <map_name> <skinName>", NamedTextColor.RED));
                    return true;
                }
                String map = args[1].toLowerCase();
                String skinName = args[2].replace("_", " ");
                ConfigManager cfg = AspireChameleon.getInstance().getConfigManager();
                if (cfg.removeSkin(map, skinName)) {
                    player.sendMessage(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                            .append(Component.text("Removed skin '" + skinName + "' from map '" + map + "'.", NamedTextColor.YELLOW)));
                } else {
                    player.sendMessage(Component.text("Skin not found in that map.", NamedTextColor.RED));
                }
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void openSkinsGui(Player player, String mapId) {
        ConfigManager cfg = AspireChameleon.getInstance().getConfigManager();
        List<SkinEntry> skins = cfg.getSkinsForMap(mapId);

        int size = Math.max(9, ((skins.size() + 2 + 8) / 9) * 9);
        size = Math.min(54, size);
        Inventory gui = Bukkit.createInventory(null, size,
                Component.text("Skins: " + mapId, NamedTextColor.DARK_GREEN));

        for (int i = 0; i < skins.size() && i < size - 1; i++) {
            SkinEntry entry = skins.get(i);
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(entry.name(), NamedTextColor.GREEN));
            meta.lore(List.of(
                    Component.text("UUID: " + entry.uuid(), NamedTextColor.DARK_GRAY),
                    Component.text("", NamedTextColor.WHITE),
                    Component.text("Click to REMOVE this skin", NamedTextColor.RED)
            ));
            item.setItemMeta(meta);
            gui.setItem(i, item);
        }

        // Add skin button in last slot
        ItemStack addBtn = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta addMeta = addBtn.getItemMeta();
        addMeta.displayName(Component.text("+ Add Skin", NamedTextColor.GREEN));
        addMeta.lore(List.of(
                Component.text("Use: /camo addskin " + mapId + " <name> <uuid>", NamedTextColor.GRAY)
        ));
        addBtn.setItemMeta(addMeta);
        gui.setItem(size - 1, addBtn);

        player.openInventory(gui);
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== Chameleon Commands ===", NamedTextColor.GREEN));
        player.sendMessage(Component.text("/camo start <map> - Start a game", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/camo stop - Stop the current game", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/camo menu - Open skin selection (during grace period)", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/camo skins <map> - View/manage skins for a map", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/camo addskin <map> <name> <uuid> - Add a skin to a map", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/camo removeskin <map> <name> - Remove a skin from a map", NamedTextColor.AQUA));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("start", "stop", "menu", "skins", "addskin", "removeskin"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("start".equals(sub) || "skins".equals(sub) || "addskin".equals(sub) || "removeskin".equals(sub)) {
                completions.addAll(AspireChameleon.getInstance().getConfigManager().getAllMapIds());
            }
        } else if (args.length == 3 && "removeskin".equalsIgnoreCase(args[0])) {
            String map = args[1].toLowerCase();
            List<SkinEntry> skins = AspireChameleon.getInstance().getConfigManager().getSkinsForMap(map);
            for (SkinEntry s : skins) {
                completions.add(s.name().replace(" ", "_"));
            }
        }
        String prefix = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(prefix));
        return completions;
    }
}

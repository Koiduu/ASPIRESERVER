package com.aspireserver.core.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RankCommand implements CommandExecutor, TabCompleter, Listener {

    private final RankManager rankManager;
    private static final String GUI_TITLE_RANK = "Select Rank";
    private static final String GUI_TITLE_COLOR = "Select + Color";

    public RankCommand(RankManager rankManager) {
        this.rankManager = rankManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Console cannot use this command.");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage(Component.text("Only ops can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /rankgive <player>", NamedTextColor.RED));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }

        openRankGui(player, target.getUniqueId(), target.getName());
        return true;
    }

    private void openRankGui(Player admin, UUID targetUuid, String targetName) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text(GUI_TITLE_RANK + " - " + targetName));

        // Row 1: Ranks
        gui.setItem(10, createRankItem(Material.COAL, "Default", "§7[Default]", targetUuid));
        gui.setItem(11, createRankItem(Material.EMERALD, "VIP", "§a[VIP]", targetUuid));
        gui.setItem(12, createRankItem(Material.EMERALD_BLOCK, "VIP+", "§a[VIP§6+§a]", targetUuid));
        gui.setItem(13, createRankItem(Material.DIAMOND, "MVP", "§b[MVP]", targetUuid));
        gui.setItem(14, createRankItem(Material.DIAMOND_BLOCK, "MVP+", "§b[MVP§c+§b]", targetUuid));
        gui.setItem(15, createRankItem(Material.GOLD_BLOCK, "MVP++", "§6[MVP§c++§6]", targetUuid));

        // Info item
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Current: " + rankManager.getRank(targetUuid).getDisplayName(), NamedTextColor.WHITE));
        info.setItemMeta(infoMeta);
        gui.setItem(4, info);

        admin.openInventory(gui);
    }

    private ItemStack createRankItem(Material mat, String rankName, String display, UUID targetUuid) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(display));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Click to set rank to " + rankName, NamedTextColor.GRAY));
        lore.add(Component.text("Target: " + targetUuid.toString().substring(0, 8), NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = getTitle(event);
        if (title == null) return;

        if (title.startsWith(GUI_TITLE_RANK)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            if (clicked.getItemMeta().lore() == null || clicked.getItemMeta().lore().isEmpty()) return;

            // Extract target UUID from lore
            List<Component> lore = clicked.getItemMeta().lore();
            if (lore.size() < 2) return;
            String targetInfo = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(lore.get(1));
            if (!targetInfo.startsWith("Target: ")) return;

            String targetName = title.replace(GUI_TITLE_RANK + " - ", "");
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            UUID targetUuid = target.getUniqueId();

            Rank selectedRank = switch (event.getSlot()) {
                case 10 -> Rank.DEFAULT;
                case 11 -> Rank.VIP;
                case 12 -> Rank.VIP_PLUS;
                case 13 -> Rank.MVP;
                case 14 -> Rank.MVP_PLUS;
                case 15 -> Rank.MVP_PLUS_PLUS;
                default -> null;
            };

            if (selectedRank == null) return;

            rankManager.setRank(targetUuid, selectedRank);
            player.sendMessage(Component.text("Set " + targetName + "'s rank to ", NamedTextColor.GREEN)
                    .append(selectedRank.formatTag(null)));

            if (selectedRank.getPlusSymbol() != null) {
                player.closeInventory();
                openColorGui(player, targetUuid, targetName);
            } else {
                player.closeInventory();
                updateDisplayName(target);
            }
        } else if (title.startsWith(GUI_TITLE_COLOR)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            String targetName = title.replace(GUI_TITLE_COLOR + " - ", "");
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            UUID targetUuid = target.getUniqueId();

            TextColor color = switch (event.getSlot()) {
                case 0 -> NamedTextColor.RED;
                case 1 -> NamedTextColor.GOLD;
                case 2 -> NamedTextColor.YELLOW;
                case 3 -> NamedTextColor.GREEN;
                case 4 -> NamedTextColor.DARK_GREEN;
                case 5 -> NamedTextColor.AQUA;
                case 6 -> NamedTextColor.DARK_AQUA;
                case 7 -> NamedTextColor.BLUE;
                case 8 -> NamedTextColor.LIGHT_PURPLE;
                case 9 -> NamedTextColor.DARK_PURPLE;
                case 10 -> NamedTextColor.WHITE;
                case 11 -> NamedTextColor.BLACK;
                case 12 -> NamedTextColor.DARK_RED;
                case 13 -> NamedTextColor.DARK_BLUE;
                default -> null;
            };

            if (color == null) return;

            rankManager.setPlusColor(targetUuid, color);
            player.sendMessage(Component.text("Set + color for " + targetName, NamedTextColor.GREEN));
            player.closeInventory();
            updateDisplayName(target);
        }
    }

    private void openColorGui(Player admin, UUID targetUuid, String targetName) {
        Inventory gui = Bukkit.createInventory(null, 18, Component.text(GUI_TITLE_COLOR + " - " + targetName));

        gui.setItem(0, createColorItem(Material.RED_WOOL, "Red", NamedTextColor.RED));
        gui.setItem(1, createColorItem(Material.ORANGE_WOOL, "Gold", NamedTextColor.GOLD));
        gui.setItem(2, createColorItem(Material.YELLOW_WOOL, "Yellow", NamedTextColor.YELLOW));
        gui.setItem(3, createColorItem(Material.LIME_WOOL, "Green", NamedTextColor.GREEN));
        gui.setItem(4, createColorItem(Material.GREEN_WOOL, "Dark Green", NamedTextColor.DARK_GREEN));
        gui.setItem(5, createColorItem(Material.LIGHT_BLUE_WOOL, "Aqua", NamedTextColor.AQUA));
        gui.setItem(6, createColorItem(Material.CYAN_WOOL, "Dark Aqua", NamedTextColor.DARK_AQUA));
        gui.setItem(7, createColorItem(Material.BLUE_WOOL, "Blue", NamedTextColor.BLUE));
        gui.setItem(8, createColorItem(Material.PINK_WOOL, "Light Purple", NamedTextColor.LIGHT_PURPLE));
        gui.setItem(9, createColorItem(Material.PURPLE_WOOL, "Dark Purple", NamedTextColor.DARK_PURPLE));
        gui.setItem(10, createColorItem(Material.WHITE_WOOL, "White", NamedTextColor.WHITE));
        gui.setItem(11, createColorItem(Material.BLACK_WOOL, "Black", NamedTextColor.BLACK));
        gui.setItem(12, createColorItem(Material.RED_TERRACOTTA, "Dark Red", NamedTextColor.DARK_RED));
        gui.setItem(13, createColorItem(Material.BLUE_TERRACOTTA, "Dark Blue", NamedTextColor.DARK_BLUE));

        admin.openInventory(gui);
    }

    private ItemStack createColorItem(Material mat, String name, TextColor color) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        item.setItemMeta(meta);
        return item;
    }

    private void updateDisplayName(OfflinePlayer target) {
        if (target.isOnline()) {
            Player online = target.getPlayer();
            Component displayName = rankManager.getDisplayName(online);
            online.displayName(displayName);
            online.playerListName(displayName);
        }
    }

    private String getTitle(InventoryClickEvent event) {
        if (event.getView().title() == null) return null;
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}

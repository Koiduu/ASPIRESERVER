package com.aspireserver.core.title;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class TitleCommand implements CommandExecutor, TabCompleter, Listener {

    private final TitleManager titleManager;
    private static final String GUI_TITLE = "Select BB Title";

    public TitleCommand(TitleManager titleManager) {
        this.titleManager = titleManager;
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
            player.sendMessage(Component.text("Usage: /titlegive <player> [title]", NamedTextColor.RED));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }

        if (args.length >= 2) {
            BbTitle title = BbTitle.fromString(args[1]);
            if (title == null) {
                player.sendMessage(Component.text("Unknown title: " + args[1], NamedTextColor.RED));
                player.sendMessage(Component.text("Available: " + String.join(", ",
                        Arrays.stream(BbTitle.values()).map(BbTitle::getDisplayName).toList()), NamedTextColor.GRAY));
                return true;
            }
            titleManager.setTitle(target.getUniqueId(), title);
            player.sendMessage(Component.text("Set " + target.getName() + "'s title to ", NamedTextColor.GREEN)
                    .append(title.format()));
            updateDisplayName(target);
            return true;
        }

        openTitleGui(player, target.getUniqueId(), target.getName());
        return true;
    }

    private void openTitleGui(Player admin, UUID targetUuid, String targetName) {
        Inventory gui = Bukkit.createInventory(null, 36, Component.text(GUI_TITLE + " - " + targetName));

        BbTitle[] titles = BbTitle.values();
        int slot = 0;
        for (BbTitle title : titles) {
            if (slot >= 36) break;
            gui.setItem(slot, createTitleItem(title, targetUuid));
            slot++;
        }

        // Remove title item
        ItemStack removeItem = new ItemStack(Material.BARRIER);
        ItemMeta removeMeta = removeItem.getItemMeta();
        removeMeta.displayName(Component.text("Remove Title", NamedTextColor.RED));
        List<Component> removeLore = new ArrayList<>();
        removeLore.add(Component.text("Click to remove current title", NamedTextColor.GRAY));
        removeMeta.lore(removeLore);
        removeItem.setItemMeta(removeMeta);
        gui.setItem(35, removeItem);

        // Current title info
        BbTitle current = titleManager.getTitle(targetUuid);
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        String currentText = current != null ? current.getDisplayName() : "None";
        infoMeta.displayName(Component.text("Current: " + currentText, NamedTextColor.WHITE));
        info.setItemMeta(infoMeta);
        gui.setItem(31, info);

        admin.openInventory(gui);
    }

    private ItemStack createTitleItem(BbTitle title, UUID targetUuid) {
        Material mat = title.isBold() ? Material.ENCHANTED_GOLDEN_APPLE : Material.GOLDEN_APPLE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(title.format());
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Click to set this title", NamedTextColor.GRAY));
        lore.add(Component.text("ID: " + title.name(), NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = getTitle(event);
        if (title == null || !title.startsWith(GUI_TITLE)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String targetName = title.replace(GUI_TITLE + " - ", "");
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID targetUuid = target.getUniqueId();

        if (clicked.getType() == Material.BARRIER) {
            titleManager.removeTitle(targetUuid);
            player.sendMessage(Component.text("Removed " + targetName + "'s title.", NamedTextColor.GREEN));
            player.closeInventory();
            updateDisplayName(target);
            return;
        }

        if (clicked.getType() == Material.PAPER) return;

        if (clicked.getItemMeta().lore() == null || clicked.getItemMeta().lore().size() < 2) return;
        String idLine = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().lore().get(1));
        if (!idLine.startsWith("ID: ")) return;

        String titleId = idLine.substring(4);
        BbTitle bbTitle = BbTitle.fromString(titleId);
        if (bbTitle == null) return;

        titleManager.setTitle(targetUuid, bbTitle);
        player.sendMessage(Component.text("Set " + targetName + "'s title to ", NamedTextColor.GREEN)
                .append(bbTitle.format()));
        player.closeInventory();
        updateDisplayName(target);
    }

    private void updateDisplayName(OfflinePlayer target) {
        if (target.isOnline()) {
            Player online = target.getPlayer();
            if (online != null) {
                com.aspireserver.core.AspireCore core = com.aspireserver.core.AspireCore.getInstance();
                if (core != null && core.getRankManager() != null) {
                    Component displayName = core.getRankManager().getDisplayName(online);
                    online.displayName(displayName);
                    online.playerListName(displayName);
                }
            }
        }
    }

    private String getTitle(InventoryClickEvent event) {
        if (event.getView().title() == null) return null;
        return PlainTextComponentSerializer.plainText().serialize(event.getView().title());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2) {
            return Arrays.stream(BbTitle.values())
                    .map(BbTitle::getDisplayName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}

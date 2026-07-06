package com.aspireserver.smp.commands;

import com.aspireserver.smp.shop.ShopListing;
import com.aspireserver.smp.shop.ShopManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

import java.util.List;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final ShopManager shopManager;

    public ShopCommand(ShopManager shopManager) {
        this.shopManager = shopManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            openShopGui(player, 0);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "sell" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /shop sell <price in diamonds>", NamedTextColor.RED));
                    player.sendMessage(Component.text("Hold the item you want to sell!", NamedTextColor.GRAY));
                    return true;
                }
                int price;
                try {
                    price = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Invalid price!", NamedTextColor.RED));
                    return true;
                }
                if (price <= 0 || price > 640) {
                    player.sendMessage(Component.text("Price must be 1-640 diamonds!", NamedTextColor.RED));
                    return true;
                }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() == Material.AIR) {
                    player.sendMessage(Component.text("Hold an item to sell!", NamedTextColor.RED));
                    return true;
                }
                shopManager.addListing(player.getUniqueId(), held, price);
                player.getInventory().setItemInMainHand(null);
                player.sendMessage(Component.text("Listed for sale! Price: " + price + " diamond(s)", NamedTextColor.GREEN));
            }
            case "cancel" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /shop cancel <number>", NamedTextColor.RED));
                    return true;
                }
                int idx;
                try {
                    idx = Integer.parseInt(args[1]) - 1;
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Invalid number!", NamedTextColor.RED));
                    return true;
                }
                List<ShopListing> myListings = shopManager.getPlayerListings(player.getUniqueId());
                if (idx < 0 || idx >= myListings.size()) {
                    player.sendMessage(Component.text("Invalid listing number!", NamedTextColor.RED));
                    return true;
                }
                ShopListing listing = myListings.get(idx);
                player.getInventory().addItem(listing.getItem().clone());
                shopManager.removeListing(listing);
                player.sendMessage(Component.text("Listing cancelled. Item returned.", NamedTextColor.GREEN));
            }
            case "my" -> {
                List<ShopListing> myListings = shopManager.getPlayerListings(player.getUniqueId());
                if (myListings.isEmpty()) {
                    player.sendMessage(Component.text("You have no active listings.", NamedTextColor.GRAY));
                    return true;
                }
                player.sendMessage(Component.text("--- Your Listings ---", NamedTextColor.GOLD));
                for (int i = 0; i < myListings.size(); i++) {
                    ShopListing l = myListings.get(i);
                    String itemName = l.getItem().getType().name().toLowerCase().replace('_', ' ');
                    player.sendMessage(Component.text((i + 1) + ". " + itemName + " x" + l.getItem().getAmount()
                            + " — " + l.getPrice() + " diamond(s)", NamedTextColor.GRAY));
                }
            }
            case "buy" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Click items in /shop GUI to buy!", NamedTextColor.RED));
                    return true;
                }
                int idx;
                try {
                    idx = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Invalid number!", NamedTextColor.RED));
                    return true;
                }
                handleBuy(player, idx);
            }
            default -> {
                player.sendMessage(Component.text("--- Shop Commands ---", NamedTextColor.GOLD));
                player.sendMessage(Component.text("/shop - Browse shop", NamedTextColor.GRAY));
                player.sendMessage(Component.text("/shop sell <price> - Sell held item", NamedTextColor.GRAY));
                player.sendMessage(Component.text("/shop my - Your listings", NamedTextColor.GRAY));
                player.sendMessage(Component.text("/shop cancel <#> - Cancel listing", NamedTextColor.GRAY));
            }
        }
        return true;
    }

    private void openShopGui(Player player, int page) {
        List<ShopListing> allListings = shopManager.getAllListings();
        int perPage = 45;
        int start = page * perPage;
        int end = Math.min(start + perPage, allListings.size());

        Inventory gui = Bukkit.createInventory(null, 54,
                Component.text("Shop (Page " + (page + 1) + ")", NamedTextColor.GOLD));

        for (int i = start; i < end; i++) {
            ShopListing listing = allListings.get(i);
            ItemStack display = listing.getItem().clone();
            ItemMeta meta = display.getItemMeta();
            String sellerName = Bukkit.getOfflinePlayer(listing.getSeller()).getName();
            meta.lore(List.of(
                    Component.text("Price: " + listing.getPrice() + " diamond(s)", NamedTextColor.GOLD),
                    Component.text("Seller: " + (sellerName != null ? sellerName : "???"), NamedTextColor.GRAY),
                    Component.text("Click to buy!", NamedTextColor.GREEN)
            ));
            display.setItemMeta(meta);
            gui.setItem(i - start, display);
        }

        // Navigation
        if (page > 0) {
            gui.setItem(45, createNavItem(Material.ARROW, "Previous Page"));
        }
        if (end < allListings.size()) {
            gui.setItem(53, createNavItem(Material.ARROW, "Next Page"));
        }

        player.openInventory(gui);
    }

    public void handleBuy(Player buyer, int globalIndex) {
        ShopListing listing = shopManager.getListing(globalIndex);
        if (listing == null) {
            buyer.sendMessage(Component.text("Listing no longer available!", NamedTextColor.RED));
            return;
        }
        if (listing.getSeller().equals(buyer.getUniqueId())) {
            buyer.sendMessage(Component.text("You can't buy your own listing!", NamedTextColor.RED));
            return;
        }

        int price = listing.getPrice();
        if (!hasDiamonds(buyer, price)) {
            buyer.sendMessage(Component.text("Not enough diamonds! Need " + price, NamedTextColor.RED));
            return;
        }

        removeDiamonds(buyer, price);
        buyer.getInventory().addItem(listing.getItem().clone());
        shopManager.removeListing(listing);
        buyer.sendMessage(Component.text("Purchased! -" + price + " diamond(s)", NamedTextColor.GREEN));

        // Give diamonds to seller when they come online (or now if online)
        Player seller = Bukkit.getPlayer(listing.getSeller());
        if (seller != null) {
            seller.getInventory().addItem(new ItemStack(Material.DIAMOND, price));
            seller.sendMessage(Component.text(buyer.getName() + " bought your item! +" + price + " diamond(s)", NamedTextColor.GOLD));
        } else {
            // Store pending payment — for simplicity, drop diamonds at world spawn
            // In a full system you'd use a pending payments file
        }
    }

    private boolean hasDiamonds(Player player, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    private void removeDiamonds(Player player, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getSize() && remaining > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.DIAMOND) {
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
            }
        }
    }

    private ItemStack createNavItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return List.of("sell", "buy", "my", "cancel").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}

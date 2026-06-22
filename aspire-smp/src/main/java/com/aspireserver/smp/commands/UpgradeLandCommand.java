package com.aspireserver.smp.commands;

import com.aspireserver.smp.claim.ClaimManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class UpgradeLandCommand implements CommandExecutor {

    private final ClaimManager claimManager;

    public UpgradeLandCommand(ClaimManager claimManager) {
        this.claimManager = claimManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        int currentTier = claimManager.getClaimTier(player.getUniqueId());

        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Upgrade Land", NamedTextColor.GOLD));

        if (currentTier < 1) {
            ItemStack tier1 = new ItemStack(Material.NETHERITE_INGOT);
            ItemMeta meta1 = tier1.getItemMeta();
            meta1.displayName(Component.text("Tier 1 Upgrade", NamedTextColor.GOLD));
            meta1.lore(List.of(
                Component.text("Cost: 1 Netherite Ingot", NamedTextColor.GRAY),
                Component.text("Increases claim limit to 250 blocks", NamedTextColor.GREEN),
                Component.text("", NamedTextColor.WHITE),
                Component.text("Click to upgrade!", NamedTextColor.YELLOW)
            ));
            tier1.setItemMeta(meta1);
            gui.setItem(11, tier1);
        } else {
            ItemStack done = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta doneMeta = done.getItemMeta();
            doneMeta.displayName(Component.text("Tier 1 - UNLOCKED", NamedTextColor.GREEN));
            done.setItemMeta(doneMeta);
            gui.setItem(11, done);
        }

        if (currentTier < 2) {
            ItemStack tier2 = new ItemStack(Material.NETHERITE_BLOCK);
            ItemMeta meta2 = tier2.getItemMeta();
            meta2.displayName(Component.text("Tier 2 Upgrade", NamedTextColor.LIGHT_PURPLE));
            meta2.lore(List.of(
                Component.text("Cost: 5 Netherite Ingots", NamedTextColor.GRAY),
                Component.text("Increases claim limit to 500 blocks (MAX)", NamedTextColor.GREEN),
                Component.text("Requires Tier 1", NamedTextColor.RED),
                Component.text("", NamedTextColor.WHITE),
                Component.text("Click to upgrade!", NamedTextColor.YELLOW)
            ));
            tier2.setItemMeta(meta2);
            gui.setItem(15, tier2);
        } else {
            ItemStack done = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta doneMeta = done.getItemMeta();
            doneMeta.displayName(Component.text("Tier 2 - UNLOCKED (MAX)", NamedTextColor.GREEN));
            done.setItemMeta(doneMeta);
            gui.setItem(15, done);
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Current Status", NamedTextColor.AQUA));
        infoMeta.lore(List.of(
            Component.text("Tier: " + currentTier, NamedTextColor.GRAY),
            Component.text("Limit: " + claimManager.getClaimLimit(player.getUniqueId()) + " blocks", NamedTextColor.GRAY),
            Component.text("Used: " + claimManager.getUsedBlocks(player.getUniqueId()) + " blocks", NamedTextColor.GRAY)
        ));
        info.setItemMeta(infoMeta);
        gui.setItem(13, info);

        player.openInventory(gui);
        return true;
    }
}

package com.aspireserver.smp.listeners;

import com.aspireserver.smp.claim.Claim;
import com.aspireserver.smp.claim.ClaimManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class ClaimListener implements Listener {

    private final ClaimManager claimManager;

    public ClaimListener(ClaimManager claimManager) {
        this.claimManager = claimManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("aspire.admin.bypass")) return;

        if (!claimManager.canBuild(player.getUniqueId(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("This land is claimed!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("aspire.admin.bypass")) return;

        if (!claimManager.canBuild(player.getUniqueId(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("This land is claimed!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        if (player.hasPermission("aspire.admin.bypass")) return;

        Material type = event.getClickedBlock().getType();
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST
            || type == Material.BARREL || type == Material.SHULKER_BOX
            || type.name().contains("SHULKER_BOX")) {

            if (!claimManager.canBuild(player.getUniqueId(), event.getClickedBlock().getLocation())) {
                event.setCancelled(true);
                player.sendActionBar(Component.text("This container is protected!", NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component title = event.getView().title();
        String titleText = PlainTextComponentSerializer.plainText().serialize(title);
        if (!titleText.equals("Upgrade Land")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        int slot = event.getSlot();
        int currentTier = claimManager.getClaimTier(player.getUniqueId());

        if (slot == 11 && currentTier < 1) {
            if (hasNetherite(player, 1)) {
                removeNetherite(player, 1);
                claimManager.upgradeTier(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Upgraded to Tier 1! Claim limit: 250 blocks", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("You need 1 Netherite Ingot!", NamedTextColor.RED));
            }
        } else if (slot == 15 && currentTier == 1) {
            if (hasNetherite(player, 5)) {
                removeNetherite(player, 5);
                claimManager.upgradeTier(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Upgraded to Tier 2! Claim limit: 500 blocks (MAX)", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("You need 5 Netherite Ingots!", NamedTextColor.RED));
            }
        } else if (slot == 15 && currentTier < 1) {
            player.sendMessage(Component.text("You need Tier 1 first!", NamedTextColor.RED));
        }
    }

    private boolean hasNetherite(Player player, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.NETHERITE_INGOT) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    private void removeNetherite(Player player, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getSize() && remaining > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.NETHERITE_INGOT) {
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
            }
        }
    }
}

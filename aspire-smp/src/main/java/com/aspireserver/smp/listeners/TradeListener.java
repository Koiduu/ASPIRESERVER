package com.aspireserver.smp.listeners;

import com.aspireserver.smp.commands.TradeGui;
import com.aspireserver.smp.trade.TradeManager;
import com.aspireserver.smp.trade.TradeSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public class TradeListener implements Listener {

    private final TradeManager tradeManager;

    public TradeListener(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component title = event.getView().title();
        String titleText = PlainTextComponentSerializer.plainText().serialize(title);
        if (!titleText.equals(TradeGui.TRADE_TITLE)) return;

        TradeSession session = tradeManager.getSession(player.getUniqueId());
        if (session == null) {
            event.setCancelled(true);
            return;
        }

        int slot = event.getRawSlot();

        // Clicking in player inventory (bottom) — allow picking up items to place in offer
        if (slot >= 54) return;

        boolean isP1 = player.getUniqueId().equals(session.getPlayer1());

        // Cancel button
        if (slot == TradeGui.CANCEL_SLOT) {
            event.setCancelled(true);
            cancelTrade(session);
            return;
        }

        // Confirm button
        if (slot == TradeGui.CONFIRM_SLOT) {
            event.setCancelled(true);
            session.setConfirmed(player.getUniqueId(), true);
            player.sendMessage(Component.text("Trade confirmed! Waiting for other player...", NamedTextColor.GREEN));

            Player other = Bukkit.getPlayer(session.getOther(player.getUniqueId()));
            if (other != null) {
                other.sendMessage(Component.text(player.getName() + " confirmed the trade!", NamedTextColor.GREEN));
            }

            if (session.bothConfirmed()) {
                executeTrade(session);
            } else {
                refreshBothGuis(session);
            }
            return;
        }

        // My offer slots — allow placing/removing items
        if (TradeGui.isMyOfferSlot(slot, isP1)) {
            // Allow the click — Bukkit will handle the item swap
            // After the click, we need to update the session
            Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("AspireSMP"), () -> {
                    int offerIdx = TradeGui.getOfferIndex(slot, isP1);
                    ItemStack item = event.getView().getTopInventory().getItem(slot);
                    session.setOfferSlot(player.getUniqueId(), offerIdx, item);
                    refreshBothGuis(session);
                }, 1L);
            return;
        }

        // Everything else is locked
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Component title = event.getView().title();
        String titleText = PlainTextComponentSerializer.plainText().serialize(title);
        if (!titleText.equals(TradeGui.TRADE_TITLE)) return;

        TradeSession session = tradeManager.getSession(player.getUniqueId());
        if (session == null) return;

        // Return items to the player who closed
        returnItems(player, session);
        tradeManager.endSession(session);

        Player other = Bukkit.getPlayer(session.getOther(player.getUniqueId()));
        if (other != null) {
            other.sendMessage(Component.text(player.getName() + " cancelled the trade.", NamedTextColor.RED));
            other.closeInventory();
        }
    }

    private void cancelTrade(TradeSession session) {
        Player p1 = Bukkit.getPlayer(session.getPlayer1());
        Player p2 = Bukkit.getPlayer(session.getPlayer2());

        if (p1 != null) {
            returnItems(p1, session);
            p1.closeInventory();
            p1.sendMessage(Component.text("Trade cancelled.", NamedTextColor.RED));
        }
        if (p2 != null) {
            returnItems(p2, session);
            p2.closeInventory();
            p2.sendMessage(Component.text("Trade cancelled.", NamedTextColor.RED));
        }
        tradeManager.endSession(session);
    }

    private void executeTrade(TradeSession session) {
        Player p1 = Bukkit.getPlayer(session.getPlayer1());
        Player p2 = Bukkit.getPlayer(session.getPlayer2());

        if (p1 == null || p2 == null) {
            cancelTrade(session);
            return;
        }

        ItemStack[] p1Offer = session.getOffer(session.getPlayer1());
        ItemStack[] p2Offer = session.getOffer(session.getPlayer2());

        // Give P1's items to P2 and vice versa
        for (ItemStack item : p1Offer) {
            if (item != null) {
                p2.getInventory().addItem(item.clone());
            }
        }
        for (ItemStack item : p2Offer) {
            if (item != null) {
                p1.getInventory().addItem(item.clone());
            }
        }

        tradeManager.endSession(session);

        p1.closeInventory();
        p2.closeInventory();
        p1.sendMessage(Component.text("Trade completed!", NamedTextColor.GREEN));
        p2.sendMessage(Component.text("Trade completed!", NamedTextColor.GREEN));
    }

    private void returnItems(Player player, TradeSession session) {
        ItemStack[] offer = session.getOffer(player.getUniqueId());
        for (ItemStack item : offer) {
            if (item != null) {
                player.getInventory().addItem(item.clone());
            }
        }
    }

    private void refreshBothGuis(TradeSession session) {
        Player p1 = Bukkit.getPlayer(session.getPlayer1());
        Player p2 = Bukkit.getPlayer(session.getPlayer2());
        if (p1 != null) TradeGui.openTradeGui(p1, session);
        if (p2 != null) TradeGui.openTradeGui(p2, session);
    }
}

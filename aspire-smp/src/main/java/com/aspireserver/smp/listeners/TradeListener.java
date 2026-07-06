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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public class TradeListener implements Listener {

    private final TradeManager tradeManager;

    public TradeListener(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
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

        // My offer slots — allow placing/removing items via any click type
        if (TradeGui.isMyOfferSlot(slot, isP1)) {
            // Reset confirmations when offers change
            session.setConfirmed(session.getPlayer1(), false);
            session.setConfirmed(session.getPlayer2(), false);

            // Allow the click to go through — schedule sync after
            Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("AspireSMP"), () -> {
                    // Sync all offer slots from the inventory state
                    int[] mySlots = isP1 ? TradeGui.P1_SLOTS : TradeGui.P2_SLOTS;
                    for (int i = 0; i < mySlots.length; i++) {
                        ItemStack item = event.getView().getTopInventory().getItem(mySlots[i]);
                        session.setOfferSlot(player.getUniqueId(), i, item != null ? item.clone() : null);
                    }
                    refreshBothGuis(session);
                }, 1L);
            return;
        }

        // Clicking in player's own inventory (bottom half)
        if (slot >= 54) {
            if (event.isShiftClick() && event.getCurrentItem() != null) {
                event.setCancelled(true);
                // Find first empty offer slot
                ItemStack[] myOffer = session.getOffer(player.getUniqueId());
                for (int i = 0; i < myOffer.length; i++) {
                    if (myOffer[i] == null) {
                        ItemStack moving = event.getCurrentItem().clone();
                        session.setOfferSlot(player.getUniqueId(), i, moving);
                        event.setCurrentItem(null);
                        session.setConfirmed(session.getPlayer1(), false);
                        session.setConfirmed(session.getPlayer2(), false);
                        refreshBothGuis(session);
                        break;
                    }
                }
            }
            // Normal clicks in bottom inventory are fine (picking up items to place)
            return;
        }

        // Everything else in top inventory is locked
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component title = event.getView().title();
        String titleText = PlainTextComponentSerializer.plainText().serialize(title);
        if (!titleText.equals(TradeGui.TRADE_TITLE)) return;

        TradeSession session = tradeManager.getSession(player.getUniqueId());
        if (session == null) {
            event.setCancelled(true);
            return;
        }

        boolean isP1 = player.getUniqueId().equals(session.getPlayer1());

        // Only allow dragging into own offer slots
        for (int slot : event.getRawSlots()) {
            if (slot < 54 && !TradeGui.isMyOfferSlot(slot, isP1)) {
                event.setCancelled(true);
                return;
            }
        }

        // If drag went into offer slots, update session
        boolean touchedOfferSlots = false;
        for (int slot : event.getRawSlots()) {
            if (slot < 54 && TradeGui.isMyOfferSlot(slot, isP1)) {
                touchedOfferSlots = true;
                break;
            }
        }
        if (touchedOfferSlots) {
            session.setConfirmed(session.getPlayer1(), false);
            session.setConfirmed(session.getPlayer2(), false);
            Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("AspireSMP"), () -> {
                    int[] mySlots = isP1 ? TradeGui.P1_SLOTS : TradeGui.P2_SLOTS;
                    for (int i = 0; i < mySlots.length; i++) {
                        ItemStack item = event.getView().getTopInventory().getItem(mySlots[i]);
                        session.setOfferSlot(player.getUniqueId(), i, item);
                    }
                    refreshBothGuis(session);
                }, 1L);
        }
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
            returnItems(other, session);
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

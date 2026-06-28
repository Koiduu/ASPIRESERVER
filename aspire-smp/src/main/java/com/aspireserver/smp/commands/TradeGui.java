package com.aspireserver.smp.commands;

import com.aspireserver.smp.trade.TradeSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class TradeGui {

    public static final String TRADE_TITLE = "Trade";
    // Layout (6 rows = 54 slots):
    // Row 0: P1 label(0), divider(1-3), P2 label(4), divider(5-8)
    // Slots 9-12: P1 offer (4 slots)
    // Slot 13: divider
    // Slots 14-17: P2 offer (4 slots)
    // Slots 18-21: P1 offer cont
    // Slot 22: divider
    // Slots 23-26: P2 offer cont
    //
    // Simplified layout:
    // P1 offer slots: 10, 11, 19, 20
    // P2 offer slots: 14, 15, 23, 24
    // Divider column: 12, 13 (glass panes)
    // Confirm buttons: 48 (P1 confirm), 50 (P2 confirm)
    // Cancel: 49

    public static final int[] P1_SLOTS = {10, 11, 19, 20};
    public static final int[] P2_SLOTS = {14, 15, 23, 24};
    public static final int CONFIRM_SLOT = 49;
    public static final int CANCEL_SLOT = 45;

    public static void openTradeGui(Player player, TradeSession session) {
        Player other = Bukkit.getPlayer(session.getOther(player.getUniqueId()));
        String otherName = other != null ? other.getName() : "???";

        Inventory gui = Bukkit.createInventory(null, 54,
            Component.text(TRADE_TITLE, NamedTextColor.GOLD));

        // Fill with glass panes
        ItemStack divider = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, divider);
        }

        // Clear offer slots
        for (int slot : P1_SLOTS) gui.setItem(slot, null);
        for (int slot : P2_SLOTS) gui.setItem(slot, null);

        // Labels
        boolean isP1 = player.getUniqueId().equals(session.getPlayer1());
        gui.setItem(1, createItem(Material.LIME_STAINED_GLASS_PANE, (isP1 ? "Your" : otherName + "'s") + " Offer"));
        gui.setItem(7, createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, (isP1 ? otherName + "'s" : "Your") + " Offer"));

        // Confirm button
        gui.setItem(CONFIRM_SLOT, createItem(Material.LIME_WOOL, "Confirm Trade"));

        // Cancel button
        gui.setItem(CANCEL_SLOT, createItem(Material.RED_WOOL, "Cancel Trade"));

        // Load existing offers
        int[] mySlots = isP1 ? P1_SLOTS : P2_SLOTS;
        int[] theirSlots = isP1 ? P2_SLOTS : P1_SLOTS;
        ItemStack[] myOffer = session.getOffer(player.getUniqueId());
        ItemStack[] theirOffer = session.getOtherOffer(player.getUniqueId());
        for (int i = 0; i < 4; i++) {
            if (myOffer[i] != null) gui.setItem(mySlots[i], myOffer[i]);
            if (theirOffer[i] != null) gui.setItem(theirSlots[i], theirOffer[i]);
        }

        // Status indicators
        if (session.isConfirmed(player.getUniqueId())) {
            gui.setItem(CONFIRM_SLOT, createItem(Material.EMERALD_BLOCK, "You confirmed!"));
        }
        if (session.isConfirmed(session.getOther(player.getUniqueId()))) {
            gui.setItem(50, createItem(Material.EMERALD_BLOCK, otherName + " confirmed!"));
        } else {
            gui.setItem(50, createItem(Material.YELLOW_WOOL, "Waiting for " + otherName));
        }

        player.openInventory(gui);
    }

    private static ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE));
        meta.lore(List.of());
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isMyOfferSlot(int slot, boolean isP1) {
        int[] mySlots = isP1 ? P1_SLOTS : P2_SLOTS;
        for (int s : mySlots) {
            if (s == slot) return true;
        }
        return false;
    }

    public static int getOfferIndex(int slot, boolean isP1) {
        int[] mySlots = isP1 ? P1_SLOTS : P2_SLOTS;
        for (int i = 0; i < mySlots.length; i++) {
            if (mySlots[i] == slot) return i;
        }
        return -1;
    }
}

package com.aspireserver.smp.trade;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class TradeSession {

    private final UUID player1;
    private final UUID player2;
    private final ItemStack[] player1Offer = new ItemStack[9];
    private final ItemStack[] player2Offer = new ItemStack[9];
    private boolean player1Confirmed;
    private boolean player2Confirmed;

    public TradeSession(UUID player1, UUID player2) {
        this.player1 = player1;
        this.player2 = player2;
    }

    public UUID getPlayer1() { return player1; }
    public UUID getPlayer2() { return player2; }

    public UUID getOther(UUID player) {
        return player.equals(player1) ? player2 : player1;
    }

    public ItemStack[] getOffer(UUID player) {
        return player.equals(player1) ? player1Offer : player2Offer;
    }

    public ItemStack[] getOtherOffer(UUID player) {
        return player.equals(player1) ? player2Offer : player1Offer;
    }

    public void setOfferSlot(UUID player, int slot, ItemStack item) {
        ItemStack[] offer = getOffer(player);
        if (slot >= 0 && slot < offer.length) {
            offer[slot] = item;
        }
        player1Confirmed = false;
        player2Confirmed = false;
    }

    public boolean isConfirmed(UUID player) {
        return player.equals(player1) ? player1Confirmed : player2Confirmed;
    }

    public void setConfirmed(UUID player, boolean confirmed) {
        if (player.equals(player1)) {
            player1Confirmed = confirmed;
        } else {
            player2Confirmed = confirmed;
        }
    }

    public boolean bothConfirmed() {
        return player1Confirmed && player2Confirmed;
    }
}

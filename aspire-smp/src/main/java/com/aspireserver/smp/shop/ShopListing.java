package com.aspireserver.smp.shop;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class ShopListing {

    private final UUID seller;
    private final ItemStack item;
    private final int price; // in diamonds

    public ShopListing(UUID seller, ItemStack item, int price) {
        this.seller = seller;
        this.item = item;
        this.price = price;
    }

    public UUID getSeller() { return seller; }
    public ItemStack getItem() { return item; }
    public int getPrice() { return price; }
}

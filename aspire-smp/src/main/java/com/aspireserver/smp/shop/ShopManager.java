package com.aspireserver.smp.shop;

import com.aspireserver.smp.AspireSMP;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ShopManager {

    private final AspireSMP plugin;
    private final File dataFile;
    private final List<ShopListing> listings = new ArrayList<>();

    public ShopManager(AspireSMP plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "shop.yml");
        loadListings();
    }

    public boolean addListing(UUID seller, ItemStack item, int priceInDiamonds) {
        if (item == null) return false;
        ShopListing listing = new ShopListing(seller, item.clone(), priceInDiamonds);
        listings.add(listing);
        saveListings();
        return true;
    }

    public boolean removeListing(UUID seller, int index) {
        List<ShopListing> playerListings = getPlayerListings(seller);
        if (index < 0 || index >= playerListings.size()) return false;
        ShopListing toRemove = playerListings.get(index);
        listings.remove(toRemove);
        saveListings();
        return true;
    }

    public ShopListing getListing(int globalIndex) {
        if (globalIndex < 0 || globalIndex >= listings.size()) return null;
        return listings.get(globalIndex);
    }

    public List<ShopListing> getAllListings() {
        return Collections.unmodifiableList(listings);
    }

    public List<ShopListing> getPlayerListings(UUID player) {
        List<ShopListing> result = new ArrayList<>();
        for (ShopListing listing : listings) {
            if (listing.getSeller().equals(player)) {
                result.add(listing);
            }
        }
        return result;
    }

    public void removeListing(ShopListing listing) {
        listings.remove(listing);
        saveListings();
    }

    private void loadListings() {
        if (!dataFile.exists()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        if (!config.contains("listings")) return;

        for (String key : config.getConfigurationSection("listings").getKeys(false)) {
            String path = "listings." + key;
            UUID seller = UUID.fromString(config.getString(path + ".seller"));
            ItemStack item = config.getItemStack(path + ".item");
            int price = config.getInt(path + ".price");
            if (item != null) {
                listings.add(new ShopListing(seller, item, price));
            }
        }
    }

    public void saveListings() {
        FileConfiguration config = new YamlConfiguration();
        for (int i = 0; i < listings.size(); i++) {
            ShopListing listing = listings.get(i);
            String path = "listings." + i;
            config.set(path + ".seller", listing.getSeller().toString());
            config.set(path + ".item", listing.getItem());
            config.set(path + ".price", listing.getPrice());
        }
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save shop.yml");
        }
    }
}

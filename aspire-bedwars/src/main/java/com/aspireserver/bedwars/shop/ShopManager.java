package com.aspireserver.bedwars.shop;

import com.aspireserver.bedwars.AspireBedwars;
import com.aspireserver.bedwars.game.PlayerData;
import com.aspireserver.bedwars.team.BedwarsTeam;
import com.aspireserver.bedwars.util.ItemBuilder;
import com.aspireserver.bedwars.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ShopManager {

    public static final Component SHOP_TITLE = Component.text("Item Shop", NamedTextColor.DARK_GREEN);
    public static final Component UPGRADE_TITLE = Component.text("Team Upgrades", NamedTextColor.DARK_AQUA);

    private final AspireBedwars plugin;
    private final List<ShopItem> catalog = new ArrayList<>();
    private final List<String> quickBuyIds = new ArrayList<>();

    public ShopManager(AspireBedwars plugin) {
        this.plugin = plugin;
        buildCatalog();
    }

    private void buildCatalog() {
        // BLOCKS
        catalog.add(ShopItem.special("wool", ShopCategory.BLOCKS, "Team Wool", Material.WHITE_WOOL, 16, Material.IRON_INGOT, 4));
        catalog.add(ShopItem.give("wood", ShopCategory.BLOCKS, "Oak Planks", Material.OAK_PLANKS, 16, Material.GOLD_INGOT, 4));
        catalog.add(ShopItem.give("endstone", ShopCategory.BLOCKS, "End Stone", Material.END_STONE, 12, Material.IRON_INGOT, 24));
        catalog.add(ShopItem.give("glass", ShopCategory.BLOCKS, "Blast-Proof Glass", Material.WHITE_STAINED_GLASS, 4, Material.IRON_INGOT, 12));
        catalog.add(ShopItem.give("ladder", ShopCategory.BLOCKS, "Ladder", Material.LADDER, 8, Material.IRON_INGOT, 4));
        catalog.add(ShopItem.give("obsidian", ShopCategory.BLOCKS, "Obsidian", Material.OBSIDIAN, 4, Material.EMERALD, 4));

        // MELEE
        catalog.add(ShopItem.give("stick_kb", ShopCategory.MELEE, "Knockback Stick", Material.STICK, 1, Material.GOLD_INGOT, 5));
        catalog.add(ShopItem.give("stone_sword", ShopCategory.MELEE, "Stone Sword", Material.STONE_SWORD, 1, Material.IRON_INGOT, 20));
        catalog.add(ShopItem.give("iron_sword", ShopCategory.MELEE, "Iron Sword", Material.IRON_SWORD, 1, Material.GOLD_INGOT, 7));
        catalog.add(ShopItem.give("diamond_sword", ShopCategory.MELEE, "Diamond Sword", Material.DIAMOND_SWORD, 1, Material.EMERALD, 4));

        // ARMOR (special tier handlers)
        catalog.add(ShopItem.special("armor_chain", ShopCategory.ARMOR, "Chainmail Armor", Material.CHAINMAIL_BOOTS, 1, Material.IRON_INGOT, 40));
        catalog.add(ShopItem.special("armor_iron", ShopCategory.ARMOR, "Iron Armor", Material.IRON_BOOTS, 1, Material.GOLD_INGOT, 12));
        catalog.add(ShopItem.special("armor_diamond", ShopCategory.ARMOR, "Diamond Armor", Material.DIAMOND_BOOTS, 1, Material.EMERALD, 6));

        // TOOLS (special tier handlers)
        catalog.add(ShopItem.special("pickaxe", ShopCategory.TOOLS, "Pickaxe (upgradeable)", Material.IRON_PICKAXE, 1, Material.IRON_INGOT, 10));
        catalog.add(ShopItem.special("axe", ShopCategory.TOOLS, "Axe (upgradeable)", Material.IRON_AXE, 1, Material.IRON_INGOT, 10));
        catalog.add(ShopItem.special("shears", ShopCategory.TOOLS, "Shears", Material.SHEARS, 1, Material.IRON_INGOT, 20));

        // RANGED
        catalog.add(ShopItem.give("arrow", ShopCategory.RANGED, "Arrows", Material.ARROW, 6, Material.GOLD_INGOT, 2));
        catalog.add(ShopItem.give("bow", ShopCategory.RANGED, "Bow", Material.BOW, 1, Material.GOLD_INGOT, 12));
        catalog.add(ShopItem.special("bow_power", ShopCategory.RANGED, "Bow (Power I)", Material.BOW, 1, Material.GOLD_INGOT, 24));
        catalog.add(ShopItem.special("bow_punch", ShopCategory.RANGED, "Bow (Power I, Punch I)", Material.BOW, 1, Material.EMERALD, 6));

        // POTIONS (special — splash-less quick effects)
        catalog.add(ShopItem.special("pot_speed", ShopCategory.POTIONS, "Speed II (45s)", Material.POTION, 1, Material.EMERALD, 1));
        catalog.add(ShopItem.special("pot_jump", ShopCategory.POTIONS, "Jump Boost V (45s)", Material.POTION, 1, Material.EMERALD, 1));
        catalog.add(ShopItem.special("pot_invis", ShopCategory.POTIONS, "Invisibility (30s)", Material.POTION, 1, Material.EMERALD, 2));

        // UTILITY
        catalog.add(ShopItem.special("golden_apple", ShopCategory.UTILITY, "Golden Apple", Material.GOLDEN_APPLE, 1, Material.GOLD_INGOT, 3));
        catalog.add(ShopItem.give("tnt", ShopCategory.UTILITY, "TNT", Material.TNT, 1, Material.GOLD_INGOT, 4));
        catalog.add(ShopItem.special("fireball", ShopCategory.UTILITY, "Fireball", Material.FIRE_CHARGE, 1, Material.IRON_INGOT, 40));
        catalog.add(ShopItem.give("ender_pearl", ShopCategory.UTILITY, "Ender Pearl", Material.ENDER_PEARL, 1, Material.EMERALD, 4));
        catalog.add(ShopItem.give("water_bucket", ShopCategory.UTILITY, "Water Bucket", Material.WATER_BUCKET, 1, Material.GOLD_INGOT, 3));
        catalog.add(ShopItem.give("golden_apple_food", ShopCategory.UTILITY, "Bridge Egg", Material.EGG, 1, Material.EMERALD, 1));

        // Default quick buy favorites
        quickBuyIds.addAll(List.of("wool", "stone_sword", "arrow", "golden_apple", "tnt", "fireball", "armor_iron", "pickaxe"));

        applyCustomProfile();
    }

    /**
     * Applies an optional config-driven shop profile: {@code shop.custom-items} adds
     * plain "give" entries (or overrides existing ids), and {@code shop.quick-buy}
     * replaces the default Quick Buy id list. Absent config leaves defaults intact.
     */
    private void applyCustomProfile() {
        var config = plugin.getConfig();
        for (Map<?, ?> entry : config.getMapList("shop.custom-items")) {
            String id = strOf(entry.get("id"));
            if (id == null) continue;
            ShopCategory category = parseCategory(strOf(entry.get("category")));
            Material icon = parseMaterial(strOf(entry.get("icon")));
            Material cost = parseMaterial(strOf(entry.get("cost-material")));
            if (category == null || icon == null || cost == null) {
                plugin.getLogger().warning("Skipping custom shop item '" + id + "' (bad category/icon/cost-material).");
                continue;
            }
            String name = entry.get("name") != null ? strOf(entry.get("name")) : id;
            int amount = entry.get("amount") instanceof Number n ? n.intValue() : 1;
            int costAmount = entry.get("cost-amount") instanceof Number n ? n.intValue() : 1;
            catalog.removeIf(i -> i.id.equals(id));
            catalog.add(ShopItem.give(id, category, name, icon, amount, cost, costAmount));
        }

        List<String> customQuickBuy = config.getStringList("shop.quick-buy");
        if (!customQuickBuy.isEmpty()) {
            quickBuyIds.clear();
            for (String id : customQuickBuy) {
                boolean known = catalog.stream().anyMatch(i -> i.id.equals(id));
                if (known) quickBuyIds.add(id);
                else plugin.getLogger().warning("Quick-buy id '" + id + "' has no matching shop item.");
            }
        }
    }

    private static String strOf(Object o) { return o == null ? null : String.valueOf(o); }

    private Material parseMaterial(String s) {
        return s == null ? null : Material.matchMaterial(s);
    }

    private ShopCategory parseCategory(String s) {
        if (s == null) return null;
        for (ShopCategory c : ShopCategory.values()) if (c.name().equalsIgnoreCase(s)) return c;
        return null;
    }

    // ---- GUI holders so the click listener can identify our inventories ----
    public static class ShopHolder implements InventoryHolder {
        public final ShopCategory category;
        public ShopHolder(ShopCategory category) { this.category = category; }
        @Override public Inventory getInventory() { return null; }
    }
    public static class UpgradeHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void openShop(Player player, ShopCategory category) {
        Inventory inv = Bukkit.createInventory(new ShopHolder(category), 54, SHOP_TITLE);

        // Category tabs (row 0)
        ShopCategory[] cats = ShopCategory.values();
        for (int i = 0; i < cats.length; i++) {
            ItemBuilder tab = new ItemBuilder(cats[i].icon())
                    .name(cats[i].display(), cats[i] == category ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
            if (cats[i] == category) tab.glow();
            inv.setItem(i, tab.build());
        }
        // separator row
        ItemStack pane = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ", NamedTextColor.GRAY).build();
        for (int i = 9; i < 18; i++) inv.setItem(i, pane);

        // Items
        List<ShopItem> items = category == ShopCategory.QUICK_BUY ? quickBuyItems() : itemsFor(category);
        int slot = 19;
        for (ShopItem item : items) {
            if (slot >= 53) break;
            inv.setItem(slot, renderShopItem(player, item));
            slot++;
            if ((slot % 9) == 8) slot += 2; // keep 1-slot borders
        }

        player.openInventory(inv);
    }

    public void openUpgrades(Player player) {
        Inventory inv = Bukkit.createInventory(new UpgradeHolder(), 27, UPGRADE_TITLE);
        BedwarsTeam team = plugin.getTeamManager().getTeam(player.getUniqueId());
        int sharpCap = plugin.getSetupConfig().getSharpnessCap();

        inv.setItem(10, upgradeIcon(Material.IRON_SWORD, "Sharpened Swords",
                team != null && team.getSharpnessLevel() >= 1, "8 Diamonds — passive Sharpness for all team melee"));
        inv.setItem(11, upgradeIcon(Material.IRON_CHESTPLATE, "Reinforced Armor " + (team != null ? "(" + team.getProtectionLevel() + "/4)" : ""),
                false, "Protection on all team armor (5/10/20/30 Diamonds)"));
        inv.setItem(12, upgradeIcon(Material.GOLDEN_PICKAXE, "Maniac Miner " + (team != null ? "(" + team.getHasteLevel() + "/2)" : ""),
                false, "Permanent Haste for the team (4/6 Diamonds)"));
        inv.setItem(13, upgradeIcon(Material.FURNACE, "Iron Forge " + (team != null ? "(" + team.getForgeLevel() + "/4)" : ""),
                false, "Faster base generators (4/8/12/16 Diamonds)"));
        inv.setItem(14, upgradeIcon(Material.BEACON, "Heal Pool",
                team != null && team.hasHealPool(), "1 Diamond — regen field near your bed"));
        inv.setItem(15, upgradeIcon(Material.TRIPWIRE_HOOK, "Buy Trap: Its a Trap!",
                team != null && team.getPurchasedTraps().contains("blindness"), "1 Diamond — blind + slow intruders"));
        inv.setItem(16, upgradeIcon(Material.FEATHER, "Buy Trap: Alarm",
                team != null && team.getPurchasedTraps().contains("alarm"), "1 Diamond — reveal invisible intruders"));

        player.openInventory(inv);
    }

    private ItemStack upgradeIcon(Material mat, String name, boolean owned, String desc) {
        ItemBuilder b = new ItemBuilder(mat).name(name, NamedTextColor.AQUA)
                .loreLine(desc, NamedTextColor.GRAY);
        if (owned) b.loreLine("PURCHASED", NamedTextColor.GREEN).glow();
        else b.loreLine("Click to purchase", NamedTextColor.YELLOW);
        return b.build();
    }

    private List<ShopItem> itemsFor(ShopCategory category) {
        List<ShopItem> out = new ArrayList<>();
        for (ShopItem i : catalog) if (i.category == category) out.add(i);
        return out;
    }

    private List<ShopItem> quickBuyItems() {
        List<ShopItem> out = new ArrayList<>();
        for (String id : quickBuyIds) {
            for (ShopItem i : catalog) if (i.id.equals(id)) { out.add(i); break; }
        }
        return out;
    }

    private ItemStack renderShopItem(Player player, ShopItem item) {
        NamedTextColor costColor = switch (item.costMaterial) {
            case IRON_INGOT -> NamedTextColor.WHITE;
            case GOLD_INGOT -> NamedTextColor.GOLD;
            case DIAMOND -> NamedTextColor.AQUA;
            case EMERALD -> NamedTextColor.GREEN;
            default -> NamedTextColor.GRAY;
        };
        boolean canAfford = countCurrency(player, item.costMaterial) >= item.costAmount;
        return new ItemBuilder(item.icon, item.iconAmount)
                .name(item.name, canAfford ? NamedTextColor.GREEN : NamedTextColor.RED)
                .loreLine("Cost: " + item.costAmount + " " + prettyMat(item.costMaterial), costColor)
                .loreLine(canAfford ? "Click to buy" : "Not enough resources", canAfford ? NamedTextColor.YELLOW : NamedTextColor.RED)
                .tag(Keys.SHOP_ID, item.id)
                .build();
    }

    /** Look up a catalog entry by the stable PDC id stored on a rendered shop icon. */
    public ShopItem findById(ItemStack clicked) {
        String id = Keys.read(clicked, Keys.SHOP_ID);
        if (id == null) return null;
        for (ShopItem i : catalog) if (i.id.equals(id)) return i;
        return null;
    }

    private String prettyMat(Material m) {
        return switch (m) {
            case IRON_INGOT -> "Iron";
            case GOLD_INGOT -> "Gold";
            case DIAMOND -> "Diamonds";
            case EMERALD -> "Emeralds";
            default -> m.name();
        };
    }

    public ShopItem findByIcon(ItemStack clicked, ShopCategory category) {
        if (clicked == null) return null;
        List<ShopItem> items = category == ShopCategory.QUICK_BUY ? quickBuyItems() : itemsFor(category);
        for (ShopItem i : items) {
            if (i.icon == clicked.getType()) return i;
        }
        // fall back to whole catalog match (quick buy uses give icon == item icon)
        for (ShopItem i : catalog) if (i.icon == clicked.getType() && i.category == category) return i;
        return null;
    }

    public ShopCategory categoryFromTab(Material icon) {
        for (ShopCategory c : ShopCategory.values()) if (c.icon() == icon) return c;
        return null;
    }

    // ---- Purchasing ----
    public void purchase(Player player, ShopItem item) {
        if (countCurrency(player, item.costMaterial) < item.costAmount) {
            fail(player, "You don't have enough " + prettyMat(item.costMaterial) + "!");
            return;
        }
        removeCurrency(player, item.costMaterial, item.costAmount);
        applyPurchase(player, item);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        player.sendActionBar(Component.text("Purchased " + item.name, NamedTextColor.GREEN));
    }

    private void applyPurchase(Player player, ShopItem item) {
        PlayerData data = plugin.getGameManager().getPlayerData(player.getUniqueId());
        switch (item.id) {
            case "wool" -> {
                BedwarsTeam team = plugin.getTeamManager().getTeam(player.getUniqueId());
                Material wool = team != null ? team.getColor().wool() : Material.WHITE_WOOL;
                give(player, new ItemStack(wool, item.iconAmount));
            }
            case "armor_chain" -> { if (data != null) { data.armorTier = Math.max(data.armorTier, 1); plugin.getGameManager().applyArmor(player); } }
            case "armor_iron" -> { if (data != null) { data.armorTier = Math.max(data.armorTier, 2); plugin.getGameManager().applyArmor(player); } }
            case "armor_diamond" -> { if (data != null) { data.armorTier = Math.max(data.armorTier, 3); plugin.getGameManager().applyArmor(player); } }
            case "pickaxe" -> { if (data != null) { data.pickaxeTier = Math.min(4, data.pickaxeTier + 1); plugin.getGameManager().applyTools(player); } }
            case "axe" -> { if (data != null) { data.axeTier = Math.min(4, data.axeTier + 1); plugin.getGameManager().applyTools(player); } }
            case "shears" -> { if (data != null) { data.shears = true; plugin.getGameManager().applyTools(player); } }
            case "bow_power" -> give(player, new ItemBuilder(Material.BOW).name("Bow", NamedTextColor.WHITE).enchant(org.bukkit.enchantments.Enchantment.POWER, 1).build());
            case "bow_punch" -> give(player, new ItemBuilder(Material.BOW).name("Bow", NamedTextColor.WHITE).enchant(org.bukkit.enchantments.Enchantment.POWER, 1).enchant(org.bukkit.enchantments.Enchantment.PUNCH, 1).build());
            case "golden_apple" -> give(player, new ItemStack(Material.GOLDEN_APPLE, 1));
            case "fireball" -> give(player, new ItemBuilder(Material.FIRE_CHARGE).name("Fireball", NamedTextColor.RED).build());
            case "pot_speed" -> givePotion(player, org.bukkit.potion.PotionEffectType.SPEED, 45 * 20, 1);
            case "pot_jump" -> givePotion(player, org.bukkit.potion.PotionEffectType.JUMP_BOOST, 45 * 20, 4);
            case "pot_invis" -> givePotion(player, org.bukkit.potion.PotionEffectType.INVISIBILITY, 30 * 20, 0);
            default -> {
                if (item.giveMaterial != null) {
                    ItemStack stack = new ItemStack(item.giveMaterial, item.giveAmount);
                    if (item.id.equals("stick_kb")) {
                        stack = new ItemBuilder(Material.STICK).name("Knockback Stick", NamedTextColor.GOLD)
                                .enchant(org.bukkit.enchantments.Enchantment.KNOCKBACK, 1).build();
                    }
                    give(player, stack);
                }
            }
        }
    }

    private void givePotion(Player player, org.bukkit.potion.PotionEffectType type, int duration, int amp) {
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(type, duration, amp, false, true));
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
    }

    private void give(Player player, ItemStack stack) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        for (ItemStack o : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), o);
    }

    private void fail(Player player, String msg) {
        player.sendActionBar(Component.text(msg, NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    // ---- Team upgrade purchasing (diamonds) ----
    public void handleUpgradeClick(Player player, Material icon) {
        BedwarsTeam team = plugin.getTeamManager().getTeam(player.getUniqueId());
        if (team == null) return;
        int sharpCap = plugin.getSetupConfig().getSharpnessCap();
        switch (icon) {
            case IRON_SWORD -> {
                if (team.getSharpnessLevel() >= 1) { fail(player, "Already purchased!"); return; }
                if (!tryPayDiamonds(player, 8)) return;
                team.setSharpnessLevel(Math.min(sharpCap, 1));
                plugin.getGameManager().applyTeamUpgrades(team);
                announceUpgrade(team, "Sharpened Swords");
            }
            case IRON_CHESTPLATE -> {
                int lvl = team.getProtectionLevel();
                if (lvl >= 4) { fail(player, "Max Protection!"); return; }
                int cost = new int[]{5, 10, 20, 30}[lvl];
                if (!tryPayDiamonds(player, cost)) return;
                team.setProtectionLevel(lvl + 1);
                plugin.getGameManager().applyTeamUpgrades(team);
                announceUpgrade(team, "Reinforced Armor " + (lvl + 1));
            }
            case GOLDEN_PICKAXE -> {
                int lvl = team.getHasteLevel();
                if (lvl >= 2) { fail(player, "Max Haste!"); return; }
                int cost = new int[]{4, 6}[lvl];
                if (!tryPayDiamonds(player, cost)) return;
                team.setHasteLevel(lvl + 1);
                plugin.getGameManager().applyTeamUpgrades(team);
                announceUpgrade(team, "Maniac Miner " + (lvl + 1));
            }
            case FURNACE -> {
                int lvl = team.getForgeLevel();
                if (lvl >= 4) { fail(player, "Max Forge!"); return; }
                int cost = new int[]{4, 8, 12, 16}[lvl];
                if (!tryPayDiamonds(player, cost)) return;
                team.setForgeLevel(lvl + 1);
                announceUpgrade(team, "Iron Forge " + (lvl + 1));
            }
            case BEACON -> {
                if (team.hasHealPool()) { fail(player, "Already purchased!"); return; }
                if (!tryPayDiamonds(player, 1)) return;
                team.setHealPool(true);
                announceUpgrade(team, "Heal Pool");
            }
            case TRIPWIRE_HOOK -> {
                if (team.getPurchasedTraps().contains("blindness")) { fail(player, "Already armed!"); return; }
                if (!tryPayDiamonds(player, 1)) return;
                team.getPurchasedTraps().add("blindness");
                announceUpgrade(team, "Trap: It's a Trap!");
            }
            case FEATHER -> {
                if (team.getPurchasedTraps().contains("alarm")) { fail(player, "Already armed!"); return; }
                if (!tryPayDiamonds(player, 1)) return;
                team.getPurchasedTraps().add("alarm");
                announceUpgrade(team, "Trap: Alarm");
            }
            default -> {}
        }
        openUpgrades(player);
    }

    private boolean tryPayDiamonds(Player player, int amount) {
        if (countCurrency(player, Material.DIAMOND) < amount) {
            fail(player, "You need " + amount + " Diamonds!");
            return false;
        }
        removeCurrency(player, Material.DIAMOND, amount);
        return true;
    }

    private void announceUpgrade(BedwarsTeam team, String name) {
        Component msg = Component.text("[Upgrade] ", NamedTextColor.AQUA)
                .append(Component.text(team.getColor().displayName() + " purchased " + name, team.getColor().textColor()));
        for (UUID uuid : team.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(msg);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        }
    }

    public int countCurrency(Player player, Material mat) {
        int total = 0;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == mat) total += is.getAmount();
        }
        return total;
    }

    private void removeCurrency(Player player, Material mat, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack is = contents[i];
            if (is == null || is.getType() != mat) continue;
            int take = Math.min(remaining, is.getAmount());
            is.setAmount(is.getAmount() - take);
            remaining -= take;
            if (is.getAmount() <= 0) player.getInventory().setItem(i, null);
        }
        player.updateInventory();
    }
}

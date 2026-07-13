package com.aspireserver.bedwars.listeners;

import com.aspireserver.bedwars.AspireBedwars;
import com.aspireserver.bedwars.config.NpcPoint;
import com.aspireserver.bedwars.shop.ShopCategory;
import com.aspireserver.bedwars.shop.ShopItem;
import com.aspireserver.bedwars.shop.ShopManager;
import com.aspireserver.bedwars.team.TeamColor;
import com.aspireserver.bedwars.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class InteractionListener implements Listener {

    public static final Component TEAM_SELECTOR_TITLE = Component.text("Select Team", NamedTextColor.DARK_GREEN);

    private final AspireBedwars plugin;

    public InteractionListener(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        NpcPoint.NpcType type = plugin.getNpcManager().typeOf(event.getRightClicked());
        if (type == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (plugin.getSpectatorManager().isSpectator(player.getUniqueId())) return;
        if (plugin.getTeamManager().getTeam(player.getUniqueId()) == null) return;
        if (type == NpcPoint.NpcType.SHOP) plugin.getShopManager().openShop(player, ShopCategory.QUICK_BUY);
        else plugin.getShopManager().openUpgrades(player);
    }

    @EventHandler
    public void onChestPunch(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        if (!plugin.getGameManager().isInBedwarsWorld(player)) return;
        if (!plugin.getGameManager().isRunning()) return;
        if (plugin.getSetupMode().isInSetup(player)) return;
        if (plugin.getSpectatorManager().isSpectator(player.getUniqueId())) return;
        if (!(event.getClickedBlock().getState() instanceof org.bukkit.block.Container container)) return;

        event.setCancelled(true);
        Inventory chest = container.getInventory();
        boolean movedAny = false;
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack it = storage[i];
            if (it == null || it.getType() == Material.AIR) continue;
            if (isKeptItem(it.getType())) continue;
            var overflow = chest.addItem(it.clone());
            if (overflow.isEmpty()) {
                player.getInventory().setItem(i, null);
                movedAny = true;
            } else {
                player.getInventory().setItem(i, overflow.get(0));
            }
        }
        if (movedAny) {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_CLOSE, 1f, 1.2f);
            player.sendActionBar(Component.text("Deposited items into the chest", NamedTextColor.GREEN));
        } else {
            player.sendActionBar(Component.text("Nothing to deposit", NamedTextColor.GRAY));
        }
    }

    /** Items never auto-deposited on a chest punch (kept on the player). */
    private boolean isKeptItem(Material m) {
        String n = m.name();
        return n.endsWith("_SWORD") || n.endsWith("_PICKAXE") || n.endsWith("_AXE")
                || m == Material.SHEARS || m == Material.COMPASS || m == Material.BOW
                || m == Material.ARROW || n.endsWith("_BED");
    }

    @EventHandler
    public void onLobbyInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (plugin.getSetupMode().isInSetup(player)) return;
        ItemStack hand = event.getItem();
        if (hand == null) return;
        if (!plugin.getGameManager().isInBedwarsWorld(player)) return;

        if (hand.getType() == Material.COMPASS && plugin.getGameManager().getLobbyPlayers().contains(player.getUniqueId())) {
            event.setCancelled(true);
            openTeamSelector(player);
        } else if (hand.getType() == Material.RED_BED && plugin.getGameManager().getLobbyPlayers().contains(player.getUniqueId())) {
            event.setCancelled(true);
            var w = plugin.getServer().getWorlds().get(0);
            player.teleport(w.getSpawnLocation());
        }
    }

    private void openTeamSelector(Player player) {
        Inventory inv = Bukkit.createInventory(new TeamSelectorHolder(), 27, TEAM_SELECTOR_TITLE);
        int slot = 10;
        for (TeamColor c : plugin.getSetupConfig().configuredTeams()) {
            int count = countPref(c);
            inv.setItem(slot++, new ItemBuilder(c.wool())
                    .name(c.displayName() + " Team", c.textColor())
                    .loreLine(count + " selected", NamedTextColor.GRAY)
                    .build());
            if (slot >= 17) break;
        }
        player.openInventory(inv);
    }

    private int countPref(TeamColor c) {
        int n = 0;
        for (TeamColor v : plugin.getTeamManager().getPreferences().values()) if (v == c) n++;
        return n;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ShopManager.ShopHolder || holder instanceof ShopManager.UpgradeHolder || holder instanceof TeamSelectorHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        if (holder instanceof ShopManager.ShopHolder sh) {
            // Tab row (slots 0-7) switches category
            if (event.getRawSlot() >= 0 && event.getRawSlot() < ShopCategory.values().length) {
                ShopCategory cat = plugin.getShopManager().categoryFromTab(clicked.getType());
                if (cat != null) { plugin.getShopManager().openShop(player, cat); return; }
            }
            ShopItem item = plugin.getShopManager().findById(clicked);
            if (item == null) item = plugin.getShopManager().findByIcon(clicked, sh.category);
            if (item != null) plugin.getShopManager().purchase(player, item);
        } else if (holder instanceof ShopManager.UpgradeHolder) {
            plugin.getShopManager().handleUpgradeClick(player, clicked.getType());
        } else if (holder instanceof TeamSelectorHolder) {
            TeamColor color = colorFromWool(clicked.getType());
            if (color != null) {
                plugin.getTeamManager().setPreference(player.getUniqueId(), color);
                player.sendActionBar(Component.text("Selected " + color.displayName() + " team", color.textColor()));
                player.closeInventory();
            }
        }
    }

    private TeamColor colorFromWool(Material mat) {
        for (TeamColor c : TeamColor.values()) if (c.wool() == mat) return c;
        return null;
    }

    public static class TeamSelectorHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}

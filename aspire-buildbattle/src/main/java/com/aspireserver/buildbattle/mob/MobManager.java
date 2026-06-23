package com.aspireserver.buildbattle.mob;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.game.GameSession;
import com.aspireserver.buildbattle.game.GameState;
import com.aspireserver.buildbattle.plot.PlotRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SpawnEggMeta;

import java.util.*;

public class MobManager implements Listener {

    private final AspireBuildBattle plugin;
    private final Map<UUID, Entity> editingMob;

    public static final String MOB_GUI_TITLE = "Mob Options";
    public static final String COLOR_GUI_TITLE = "Change Color";
    public static final String AGE_GUI_TITLE = "Change Age";

    public MobManager(AspireBuildBattle plugin) {
        this.plugin = plugin;
        this.editingMob = new HashMap<>();
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (entity instanceof Player) return;

        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session == null) return;
        if (session.getState() != GameState.BUILDING) return;

        PlotRegion plot = session.getPlayerPlot(player.getUniqueId());
        if (plot == null) return;

        if (!plot.contains(entity.getLocation())) return;

        if (!(entity instanceof Mob)) return;

        event.setCancelled(true);
        editingMob.put(player.getUniqueId(), entity);
        openMobGui(player, entity);
    }

    private void openMobGui(Player player, Entity entity) {
        Inventory gui = Bukkit.createInventory(null, 9,
            Component.text(MOB_GUI_TITLE, NamedTextColor.DARK_PURPLE));

        // Slot 2: Delete
        ItemStack deleteItem = new ItemStack(Material.RED_CONCRETE);
        ItemMeta deleteMeta = deleteItem.getItemMeta();
        deleteMeta.displayName(Component.text("Delete Mob", NamedTextColor.RED, TextDecoration.BOLD));
        deleteMeta.lore(List.of(Component.text("Remove this mob from your plot", NamedTextColor.GRAY)));
        deleteItem.setItemMeta(deleteMeta);
        gui.setItem(2, deleteItem);

        // Slot 4: Change Age (only for Ageable)
        if (entity instanceof Ageable) {
            ItemStack ageItem = new ItemStack(Material.CLOCK);
            ItemMeta ageMeta = ageItem.getItemMeta();
            ageMeta.displayName(Component.text("Change Age", NamedTextColor.YELLOW, TextDecoration.BOLD));
            ageMeta.lore(List.of(
                Component.text("Toggle baby/adult", NamedTextColor.GRAY),
                Component.text("Current: " + (((Ageable) entity).isAdult() ? "Adult" : "Baby"), NamedTextColor.AQUA)
            ));
            ageItem.setItemMeta(ageMeta);
            gui.setItem(4, ageItem);
        }

        // Slot 6: Change Color (only for colorable mobs)
        if (isColorable(entity)) {
            ItemStack colorItem = new ItemStack(Material.LIME_DYE);
            ItemMeta colorMeta = colorItem.getItemMeta();
            colorMeta.displayName(Component.text("Change Color", NamedTextColor.GREEN, TextDecoration.BOLD));
            colorMeta.lore(List.of(Component.text("Select a new color", NamedTextColor.GRAY)));
            colorItem.setItemMeta(colorMeta);
            gui.setItem(6, colorItem);
        }

        player.openInventory(gui);
    }

    private boolean isColorable(Entity entity) {
        return entity instanceof Sheep || entity instanceof Wolf
            || entity instanceof Cat || entity instanceof Parrot
            || entity instanceof Axolotl || entity instanceof Frog;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(event.getView().title());

        if (title.equals(MOB_GUI_TITLE)) {
            handleMobGuiClick(event, player);
        } else if (title.equals(COLOR_GUI_TITLE)) {
            handleColorGuiClick(event, player);
        } else if (title.equals(AGE_GUI_TITLE)) {
            handleAgeGuiClick(event, player);
        }
    }

    private void handleMobGuiClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        Entity mob = editingMob.get(player.getUniqueId());
        if (mob == null || mob.isDead()) {
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();
        switch (slot) {
            case 2 -> {
                mob.remove();
                editingMob.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Mob removed!", NamedTextColor.GREEN));
            }
            case 4 -> {
                if (mob instanceof Ageable ageable) {
                    openAgeGui(player, ageable);
                }
            }
            case 6 -> {
                if (isColorable(mob)) {
                    openColorGui(player);
                }
            }
        }
    }

    private void openAgeGui(Player player, Ageable entity) {
        Inventory gui = Bukkit.createInventory(null, 9,
            Component.text(AGE_GUI_TITLE, NamedTextColor.YELLOW));

        ItemStack babyItem = new ItemStack(Material.EGG);
        ItemMeta babyMeta = babyItem.getItemMeta();
        babyMeta.displayName(Component.text("Baby", NamedTextColor.AQUA, TextDecoration.BOLD));
        babyItem.setItemMeta(babyMeta);
        gui.setItem(3, babyItem);

        ItemStack adultItem = new ItemStack(Material.WHEAT);
        ItemMeta adultMeta = adultItem.getItemMeta();
        adultMeta.displayName(Component.text("Adult", NamedTextColor.GREEN, TextDecoration.BOLD));
        adultItem.setItemMeta(adultMeta);
        gui.setItem(5, adultItem);

        player.openInventory(gui);
    }

    private void handleAgeGuiClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        Entity mob = editingMob.get(player.getUniqueId());
        if (mob == null || mob.isDead() || !(mob instanceof Ageable ageable)) {
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();
        if (slot == 3) {
            ageable.setBaby();
            player.sendMessage(Component.text("Set to baby!", NamedTextColor.GREEN));
            player.closeInventory();
        } else if (slot == 5) {
            ageable.setAdult();
            player.sendMessage(Component.text("Set to adult!", NamedTextColor.GREEN));
            player.closeInventory();
        }
    }

    private void openColorGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 18,
            Component.text(COLOR_GUI_TITLE, NamedTextColor.GREEN));

        DyeColor[] colors = DyeColor.values();
        for (int i = 0; i < colors.length && i < 18; i++) {
            Material wool = getWoolForColor(colors[i]);
            ItemStack item = new ItemStack(wool);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(formatColorName(colors[i]), NamedTextColor.WHITE));
            item.setItemMeta(meta);
            gui.setItem(i, item);
        }

        player.openInventory(gui);
    }

    private void handleColorGuiClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        Entity mob = editingMob.get(player.getUniqueId());
        if (mob == null || mob.isDead()) {
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();
        DyeColor[] colors = DyeColor.values();
        if (slot < 0 || slot >= colors.length) return;

        DyeColor color = colors[slot];

        if (mob instanceof Sheep sheep) {
            sheep.setColor(color);
        } else if (mob instanceof Wolf wolf) {
            wolf.setCollarColor(color);
        } else if (mob instanceof Cat cat) {
            cat.setCollarColor(color);
        }

        player.sendMessage(Component.text("Color set to " + formatColorName(color) + "!", NamedTextColor.GREEN));
        player.closeInventory();
    }

    private Material getWoolForColor(DyeColor color) {
        return switch (color) {
            case WHITE -> Material.WHITE_WOOL;
            case ORANGE -> Material.ORANGE_WOOL;
            case MAGENTA -> Material.MAGENTA_WOOL;
            case LIGHT_BLUE -> Material.LIGHT_BLUE_WOOL;
            case YELLOW -> Material.YELLOW_WOOL;
            case LIME -> Material.LIME_WOOL;
            case PINK -> Material.PINK_WOOL;
            case GRAY -> Material.GRAY_WOOL;
            case LIGHT_GRAY -> Material.LIGHT_GRAY_WOOL;
            case CYAN -> Material.CYAN_WOOL;
            case PURPLE -> Material.PURPLE_WOOL;
            case BLUE -> Material.BLUE_WOOL;
            case BROWN -> Material.BROWN_WOOL;
            case GREEN -> Material.GREEN_WOOL;
            case RED -> Material.RED_WOOL;
            case BLACK -> Material.BLACK_WOOL;
        };
    }

    private String formatColorName(DyeColor color) {
        String name = color.name().replace('_', ' ');
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}

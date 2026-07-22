package com.aspireserver.buildbattle.pro;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.game.GameMode;
import com.aspireserver.buildbattle.game.GameSession;
import com.aspireserver.buildbattle.game.GameState;
import com.aspireserver.buildbattle.plot.PlotRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ProToolManager implements Listener {

    private final AspireBuildBattle plugin;
    private final Map<UUID, ProToolMode> playerModes;
    private final Map<UUID, Material> selectedMaterial;
    private final Map<UUID, org.bukkit.Location> pos1Map;
    private final Map<UUID, org.bukkit.Location> pos2Map;

    public static final String PRO_TOOLS_TITLE = "Pro Build Tools";
    public static final String MATERIAL_SELECT_TITLE = "Select Material";

    public enum ProToolMode {
        FILL, REPLACE, WALLS, HOLLOW, LINE, LAYER
    }

    public ProToolManager(AspireBuildBattle plugin) {
        this.plugin = plugin;
        this.playerModes = new HashMap<>();
        this.selectedMaterial = new HashMap<>();
        this.pos1Map = new HashMap<>();
        this.pos2Map = new HashMap<>();
    }

    public static ItemStack createProToolItem() {
        ItemStack item = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Magic Axe", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        meta.lore(List.of(
            Component.text("Pro Build Tool", NamedTextColor.GRAY),
            Component.text("Left-click: Set pos 1", NamedTextColor.AQUA),
            Component.text("Right-click: Set pos 2", NamedTextColor.AQUA),
            Component.text("Shift+Right: Open tool menu", NamedTextColor.YELLOW),
            Component.text("", NamedTextColor.GRAY),
            Component.text("Commands: //set, //replace,", NamedTextColor.GRAY),
            Component.text("//walls, //hollow, //line,", NamedTextColor.GRAY),
            Component.text("//layer, //clear, //undo", NamedTextColor.GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }

    // Command-based pro tools (//set, //replace, etc.)
    @EventHandler(priority = EventPriority.LOW)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String msg = event.getMessage().toLowerCase();

        if (!msg.startsWith("//")) return;

        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session == null || session.getState() != GameState.BUILDING) return;
        if (!session.getGameMode().isWorldEditEnabled()) return;

        event.setCancelled(true);
        String[] parts = event.getMessage().substring(2).split(" ");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "set" -> {
                if (parts.length < 2) {
                    player.sendMessage(Component.text("Usage: //set <block>", NamedTextColor.RED));
                    player.sendMessage(Component.text("Hold a block to use it, or type the name.", NamedTextColor.GRAY));
                    return;
                }
                Material mat = parseMaterial(parts[1], player);
                if (mat == null) return;
                executeTool(player, ProToolMode.FILL, mat);
            }
            case "replace" -> {
                if (parts.length < 3) {
                    player.sendMessage(Component.text("Usage: //replace <from> <to>", NamedTextColor.RED));
                    return;
                }
                Material from = parseMaterial(parts[1], player);
                Material to = parseMaterial(parts[2], player);
                if (from == null || to == null) return;
                selectedMaterial.put(player.getUniqueId(), from);
                executeTool(player, ProToolMode.REPLACE, to);
            }
            case "walls" -> {
                if (parts.length < 2) {
                    player.sendMessage(Component.text("Usage: //walls <block>", NamedTextColor.RED));
                    return;
                }
                Material mat = parseMaterial(parts[1], player);
                if (mat == null) return;
                executeTool(player, ProToolMode.WALLS, mat);
            }
            case "hollow" -> {
                if (parts.length < 2) {
                    player.sendMessage(Component.text("Usage: //hollow <block>", NamedTextColor.RED));
                    return;
                }
                Material mat = parseMaterial(parts[1], player);
                if (mat == null) return;
                executeTool(player, ProToolMode.HOLLOW, mat);
            }
            case "line" -> {
                if (parts.length < 2) {
                    player.sendMessage(Component.text("Usage: //line <block>", NamedTextColor.RED));
                    return;
                }
                Material mat = parseMaterial(parts[1], player);
                if (mat == null) return;
                executeTool(player, ProToolMode.LINE, mat);
            }
            case "layer" -> {
                if (parts.length < 2) {
                    player.sendMessage(Component.text("Usage: //layer <block>", NamedTextColor.RED));
                    return;
                }
                Material mat = parseMaterial(parts[1], player);
                if (mat == null) return;
                executeTool(player, ProToolMode.LAYER, mat);
            }
            case "clear" -> executeTool(player, ProToolMode.FILL, Material.AIR);
            case "undo" -> player.sendMessage(Component.text("Undo is not available yet.", NamedTextColor.YELLOW));
            case "pos1" -> {
                Block target = player.getTargetBlockExact(5);
                if (target == null) {
                    player.sendMessage(Component.text("Look at a block!", NamedTextColor.RED));
                    return;
                }
                PlotRegion plot = session.getPlayerPlot(player.getUniqueId());
                if (plot == null || !plot.contains(target.getLocation())) {
                    player.sendMessage(Component.text("Position must be inside your plot!", NamedTextColor.RED));
                    return;
                }
                pos1Map.put(player.getUniqueId(), target.getLocation());
                player.sendMessage(Component.text("Pos 1 set: (" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")", NamedTextColor.AQUA));
            }
            case "pos2" -> {
                Block target = player.getTargetBlockExact(5);
                if (target == null) {
                    player.sendMessage(Component.text("Look at a block!", NamedTextColor.RED));
                    return;
                }
                PlotRegion plot = session.getPlayerPlot(player.getUniqueId());
                if (plot == null || !plot.contains(target.getLocation())) {
                    player.sendMessage(Component.text("Position must be inside your plot!", NamedTextColor.RED));
                    return;
                }
                pos2Map.put(player.getUniqueId(), target.getLocation());
                player.sendMessage(Component.text("Pos 2 set: (" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")", NamedTextColor.AQUA));
            }
            default -> player.sendMessage(Component.text("Unknown command. Use: //set, //replace, //walls, //hollow, //line, //layer, //clear", NamedTextColor.RED));
        }
    }

    private Material parseMaterial(String input, Player player) {
        // Allow "hand" to use held block
        if (input.equalsIgnoreCase("hand")) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isBlock() && held.getType() != Material.AIR) {
                return held.getType();
            }
            player.sendMessage(Component.text("Hold a block in your hand!", NamedTextColor.RED));
            return null;
        }

        Material mat = Material.matchMaterial(input);
        if (mat == null || !mat.isBlock()) {
            player.sendMessage(Component.text("Unknown block: " + input, NamedTextColor.RED));
            return null;
        }
        return mat;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.GOLDEN_AXE) return;
        if (!item.hasItemMeta()) return;

        String name = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        if (!"Magic Axe".equals(name)) return;

        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session == null || session.getState() != GameState.BUILDING) return;
        if (!session.getGameMode().isWorldEditEnabled()) {
            player.sendMessage(Component.text("Pro tools are only available in Pro modes!", NamedTextColor.RED));
            return;
        }

        event.setCancelled(true);

        if (player.isSneaking() && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            openProToolsGui(player);
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) return;

        PlotRegion plot = session.getPlayerPlot(player.getUniqueId());
        if (plot == null) return;

        org.bukkit.Location loc = block.getLocation();
        if (!plot.contains(loc)) {
            player.sendMessage(Component.text("Position must be inside your plot!", NamedTextColor.RED));
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1Map.put(player.getUniqueId(), loc);
            player.sendMessage(Component.text("Pos 1 set: (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")", NamedTextColor.AQUA));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            pos2Map.put(player.getUniqueId(), loc);
            player.sendMessage(Component.text("Pos 2 set: (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")", NamedTextColor.AQUA));
        }
    }

    private void openProToolsGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27,
            Component.text(PRO_TOOLS_TITLE, NamedTextColor.LIGHT_PURPLE));

        gui.setItem(10, createToolItem(Material.BRICKS, "Fill", "Fill region (//set <block>)"));
        gui.setItem(11, createToolItem(Material.CRAFTING_TABLE, "Replace", "Replace blocks (//replace <from> <to>)"));
        gui.setItem(12, createToolItem(Material.COBBLESTONE_WALL, "Walls", "Create walls (//walls <block>)"));
        gui.setItem(13, createToolItem(Material.GLASS, "Hollow", "Hollow selection (//hollow <block>)"));
        gui.setItem(14, createToolItem(Material.STICK, "Line", "Draw line (//line <block>)"));
        gui.setItem(15, createToolItem(Material.SMOOTH_STONE_SLAB, "Layer", "Fill Y-layer (//layer <block>)"));
        gui.setItem(16, createToolItem(Material.TNT, "Clear", "Clear all (//clear)"));

        player.openInventory(gui);
    }

    private ItemStack createToolItem(Material material, String name, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GREEN, TextDecoration.BOLD));
        meta.lore(List.of(Component.text(description, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        if (title.equals(PRO_TOOLS_TITLE)) {
            event.setCancelled(true);
            handleProToolClick(event, player);
        } else if (title.equals(MATERIAL_SELECT_TITLE)) {
            handleMaterialSelect(event, player);
        }
    }

    private void handleProToolClick(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String toolName = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());

        UUID uuid = player.getUniqueId();

        switch (toolName) {
            case "Fill" -> {
                playerModes.put(uuid, ProToolMode.FILL);
                player.closeInventory();
                openMaterialSelectGui(player);
            }
            case "Replace" -> {
                playerModes.put(uuid, ProToolMode.REPLACE);
                player.closeInventory();
                openMaterialSelectGui(player);
            }
            case "Walls" -> {
                playerModes.put(uuid, ProToolMode.WALLS);
                player.closeInventory();
                openMaterialSelectGui(player);
            }
            case "Hollow" -> {
                playerModes.put(uuid, ProToolMode.HOLLOW);
                player.closeInventory();
                openMaterialSelectGui(player);
            }
            case "Line" -> {
                playerModes.put(uuid, ProToolMode.LINE);
                player.closeInventory();
                openMaterialSelectGui(player);
            }
            case "Layer" -> {
                playerModes.put(uuid, ProToolMode.LAYER);
                player.closeInventory();
                openMaterialSelectGui(player);
            }
            case "Clear" -> {
                player.closeInventory();
                executeTool(player, ProToolMode.FILL, Material.AIR);
            }
        }
    }

    private void openMaterialSelectGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54,
            Component.text(MATERIAL_SELECT_TITLE, NamedTextColor.GREEN));

        // Fill with common blocks but leave last row empty for drag-in
        Material[] blocks = {
            Material.STONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE,
            Material.COBBLESTONE, Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
            Material.DARK_OAK_PLANKS, Material.BRICKS, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
            Material.SANDSTONE, Material.RED_SANDSTONE, Material.QUARTZ_BLOCK, Material.SMOOTH_QUARTZ,
            Material.PRISMARINE, Material.DARK_PRISMARINE, Material.PURPUR_BLOCK, Material.END_STONE_BRICKS,
            Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES, Material.POLISHED_DEEPSLATE, Material.TUFF,
            Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.GRAY_CONCRETE, Material.BLACK_CONCRETE,
            Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
            Material.GREEN_CONCRETE, Material.CYAN_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.BLUE_CONCRETE,
            Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE, Material.PINK_CONCRETE, Material.BROWN_CONCRETE,
            Material.WHITE_WOOL, Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
            Material.GLASS, Material.WHITE_STAINED_GLASS
        };

        for (int i = 0; i < blocks.length && i < 45; i++) {
            gui.setItem(i, new ItemStack(blocks[i]));
        }

        // Info item in slot 49
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(Component.text("Or click a block from your inventory!", NamedTextColor.YELLOW));
        meta.lore(List.of(Component.text("Click any block in your inventory below", NamedTextColor.GRAY)));
        info.setItemMeta(meta);
        gui.setItem(49, info);

        player.openInventory(gui);
    }

    private void handleMaterialSelect(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();

        int rawSlot = event.getRawSlot();

        // Clicking in bottom inventory (player's inventory) — use that block
        if (rawSlot >= 54) {
            if (clicked != null && clicked.getType().isBlock() && clicked.getType() != Material.AIR) {
                applySelectedMaterial(player, clicked.getType());
            }
            return;
        }

        // Clicking in top GUI
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.PAPER) return; // info item

        if (!clicked.getType().isBlock()) {
            player.sendMessage(Component.text("Select a block!", NamedTextColor.RED));
            return;
        }

        applySelectedMaterial(player, clicked.getType());
    }

    private void applySelectedMaterial(Player player, Material mat) {
        UUID uuid = player.getUniqueId();
        ProToolMode mode = playerModes.get(uuid);
        player.closeInventory();

        if (mode == null) {
            player.sendMessage(Component.text("No tool selected!", NamedTextColor.RED));
            return;
        }

        executeTool(player, mode, mat);
    }

    private void executeTool(Player player, ProToolMode mode, Material material) {
        UUID uuid = player.getUniqueId();
        org.bukkit.Location p1 = pos1Map.get(uuid);
        org.bukkit.Location p2 = pos2Map.get(uuid);

        if (p1 == null || p2 == null) {
            player.sendMessage(Component.text("Set both positions first! (Left/Right click with Magic Axe or //pos1 //pos2)", NamedTextColor.RED));
            return;
        }

        GameSession session = plugin.getArenaManager().getPlayerSession(uuid);
        if (session == null || session.getState() != GameState.BUILDING) return;

        PlotRegion plot = session.getPlayerPlot(uuid);
        if (plot == null) return;

        int minX = Math.max(plot.getMinX() + 1, Math.min(p1.getBlockX(), p2.getBlockX()));
        int minY = Math.max(plot.getMinY() + 1, Math.min(p1.getBlockY(), p2.getBlockY()));
        int minZ = Math.max(plot.getMinZ() + 1, Math.min(p1.getBlockZ(), p2.getBlockZ()));
        int maxX = Math.min(plot.getMaxX() - 1, Math.max(p1.getBlockX(), p2.getBlockX()));
        int maxY = Math.min(plot.getMaxY(), Math.max(p1.getBlockY(), p2.getBlockY()));
        int maxZ = Math.min(plot.getMaxZ() - 1, Math.max(p1.getBlockZ(), p2.getBlockZ()));

        World world = Bukkit.getWorld(plot.getWorldName());
        if (world == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            int count = 0;
            switch (mode) {
                case FILL -> {
                    for (int x = minX; x <= maxX; x++)
                        for (int y = minY; y <= maxY; y++)
                            for (int z = minZ; z <= maxZ; z++) {
                                world.getBlockAt(x, y, z).setType(material, false);
                                count++;
                            }
                }
                case REPLACE -> {
                    Material replaceFrom = selectedMaterial.getOrDefault(uuid, Material.STONE);
                    for (int x = minX; x <= maxX; x++)
                        for (int y = minY; y <= maxY; y++)
                            for (int z = minZ; z <= maxZ; z++) {
                                Block b = world.getBlockAt(x, y, z);
                                if (b.getType() == replaceFrom) {
                                    b.setType(material, false);
                                    count++;
                                }
                            }
                }
                case WALLS -> {
                    for (int x = minX; x <= maxX; x++)
                        for (int y = minY; y <= maxY; y++)
                            for (int z = minZ; z <= maxZ; z++) {
                                if (x == minX || x == maxX || z == minZ || z == maxZ) {
                                    world.getBlockAt(x, y, z).setType(material, false);
                                    count++;
                                }
                            }
                }
                case HOLLOW -> {
                    for (int x = minX; x <= maxX; x++)
                        for (int y = minY; y <= maxY; y++)
                            for (int z = minZ; z <= maxZ; z++) {
                                if (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ) {
                                    world.getBlockAt(x, y, z).setType(material, false);
                                } else {
                                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                                }
                                count++;
                            }
                }
                case LINE -> {
                    int dx = maxX - minX;
                    int dy = maxY - minY;
                    int dz = maxZ - minZ;
                    int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
                    if (steps == 0) steps = 1;
                    for (int i = 0; i <= steps; i++) {
                        int x = minX + (int) Math.round((double) dx * i / steps);
                        int y = minY + (int) Math.round((double) dy * i / steps);
                        int z = minZ + (int) Math.round((double) dz * i / steps);
                        world.getBlockAt(x, y, z).setType(material, false);
                        count++;
                    }
                }
                case LAYER -> {
                    int y = p1.getBlockY();
                    y = Math.max(plot.getMinY() + 1, Math.min(plot.getMaxY(), y));
                    for (int x = minX; x <= maxX; x++)
                        for (int z = minZ; z <= maxZ; z++) {
                            world.getBlockAt(x, y, z).setType(material, false);
                            count++;
                        }
                }
            }
            player.sendMessage(Component.text(mode.name() + " complete! (" + count + " blocks)", NamedTextColor.GREEN));
        });

        playerModes.remove(uuid);
    }

    public void giveProTools(Player player) {
        player.getInventory().setItem(7, createProToolItem());
    }

    public void cleanup(UUID player) {
        playerModes.remove(player);
        selectedMaterial.remove(player);
        pos1Map.remove(player);
        pos2Map.remove(player);
    }
}

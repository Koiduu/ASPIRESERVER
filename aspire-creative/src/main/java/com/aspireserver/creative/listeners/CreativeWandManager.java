package com.aspireserver.creative.listeners;

import com.aspireserver.creative.AspireCreative;
import com.aspireserver.creative.plot.CreativePlot;
import com.aspireserver.creative.plot.PlotManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreativeWandManager implements Listener {

    private final AspireCreative plugin;
    private final PlotManager plotManager;
    private final Map<UUID, Location> pos1Map = new HashMap<>();
    private final Map<UUID, Location> pos2Map = new HashMap<>();
    private final Map<UUID, ToolMode> playerModes = new HashMap<>();
    private final Map<UUID, Material> replaceMaterial = new HashMap<>();

    private static final String WAND_NAME = "Magic Axe";
    private static final String TOOLS_TITLE = "Creative Build Tools";
    private static final String MATERIAL_TITLE = "Select Material";

    public enum ToolMode {
        FILL, REPLACE, WALLS, HOLLOW, LINE, LAYER
    }

    public CreativeWandManager(AspireCreative plugin, PlotManager plotManager) {
        this.plugin = plugin;
        this.plotManager = plotManager;
    }

    public static ItemStack createWandItem() {
        ItemStack item = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(WAND_NAME, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        meta.lore(List.of(
            Component.text("Creative Build Tool", NamedTextColor.GRAY),
            Component.text("Left-click: Set pos 1", NamedTextColor.AQUA),
            Component.text("Right-click: Set pos 2", NamedTextColor.AQUA),
            Component.text("Shift+Right: Open tool menu", NamedTextColor.YELLOW),
            Component.text("", NamedTextColor.GRAY),
            Component.text("Commands: //set, //replace,", NamedTextColor.GRAY),
            Component.text("//walls, //hollow, //line,", NamedTextColor.GRAY),
            Component.text("//layer, //clear", NamedTextColor.GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private boolean isCreativeWorld(Player player) {
        String worldName = player.getWorld().getName();
        return worldName.startsWith("creative_");
    }

    private CreativePlot getAccessiblePlot(Player player) {
        CreativePlot plot = plotManager.getPlotAt(player.getLocation());
        if (plot == null || !plot.canAccess(player.getUniqueId())) return null;
        return plot;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String msg = event.getMessage().toLowerCase();

        if (!msg.startsWith("//")) return;
        if (!isCreativeWorld(player)) return;

        String[] parts = event.getMessage().substring(2).split(" ");
        String cmd = parts[0].toLowerCase();

        if (cmd.equals("wand")) {
            event.setCancelled(true);
            player.getInventory().addItem(createWandItem());
            player.sendMessage(Component.text("Wand given! Use it to select positions.", NamedTextColor.GREEN));
            return;
        }

        CreativePlot plot = getAccessiblePlot(player);
        if (plot == null) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You must be on your own plot!", NamedTextColor.RED));
            return;
        }

        event.setCancelled(true);

        switch (cmd) {
            case "set" -> {
                if (parts.length < 2) {
                    player.sendMessage(Component.text("Usage: //set <block> (or 'hand')", NamedTextColor.RED));
                    return;
                }
                Material mat = parseMaterial(parts[1], player);
                if (mat == null) return;
                executeTool(player, plot, ToolMode.FILL, mat);
            }
            case "replace" -> {
                if (parts.length < 3) {
                    player.sendMessage(Component.text("Usage: //replace <from> <to>", NamedTextColor.RED));
                    return;
                }
                Material from = parseMaterial(parts[1], player);
                Material to = parseMaterial(parts[2], player);
                if (from == null || to == null) return;
                replaceMaterial.put(player.getUniqueId(), from);
                executeTool(player, plot, ToolMode.REPLACE, to);
            }
            case "walls" -> {
                if (parts.length < 2) {
                    player.sendMessage(Component.text("Usage: //walls <block>", NamedTextColor.RED));
                    return;
                }
                Material mat = parseMaterial(parts[1], player);
                if (mat == null) return;
                executeTool(player, plot, ToolMode.WALLS, mat);
            }
            case "hollow" -> {
                if (parts.length < 2) {
                    player.sendMessage(Component.text("Usage: //hollow <block>", NamedTextColor.RED));
                    return;
                }
                Material mat = parseMaterial(parts[1], player);
                if (mat == null) return;
                executeTool(player, plot, ToolMode.HOLLOW, mat);
            }
            case "line" -> {
                if (parts.length < 2) {
                    player.sendMessage(Component.text("Usage: //line <block>", NamedTextColor.RED));
                    return;
                }
                Material mat = parseMaterial(parts[1], player);
                if (mat == null) return;
                executeTool(player, plot, ToolMode.LINE, mat);
            }
            case "layer" -> {
                if (parts.length < 2) {
                    player.sendMessage(Component.text("Usage: //layer <block>", NamedTextColor.RED));
                    return;
                }
                Material mat = parseMaterial(parts[1], player);
                if (mat == null) return;
                executeTool(player, plot, ToolMode.LAYER, mat);
            }
            case "clear" -> executeTool(player, plot, ToolMode.FILL, Material.AIR);
            case "pos1" -> {
                Block target = player.getTargetBlockExact(5);
                if (target == null) {
                    player.sendMessage(Component.text("Look at a block!", NamedTextColor.RED));
                    return;
                }
                if (!plot.contains(target.getLocation())) {
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
                if (!plot.contains(target.getLocation())) {
                    player.sendMessage(Component.text("Position must be inside your plot!", NamedTextColor.RED));
                    return;
                }
                pos2Map.put(player.getUniqueId(), target.getLocation());
                player.sendMessage(Component.text("Pos 2 set: (" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")", NamedTextColor.AQUA));
            }
            default -> player.sendMessage(Component.text("Unknown command. Use: //wand, //set, //replace, //walls, //hollow, //line, //layer, //clear", NamedTextColor.RED));
        }
    }

    private Material parseMaterial(String input, Player player) {
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
        if (!item.hasItemMeta() || item.getItemMeta().displayName() == null) return;

        String name = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        if (!WAND_NAME.equals(name)) return;
        if (!isCreativeWorld(player)) return;

        CreativePlot plot = getAccessiblePlot(player);
        if (plot == null) {
            player.sendMessage(Component.text("You must be on your own plot to use this!", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        if (player.isSneaking() && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            openToolsGui(player);
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (!plot.contains(block.getLocation())) {
            player.sendMessage(Component.text("Position must be inside your plot!", NamedTextColor.RED));
            return;
        }

        Location loc = block.getLocation();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1Map.put(player.getUniqueId(), loc);
            player.sendMessage(Component.text("Pos 1 set: (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")", NamedTextColor.AQUA));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            pos2Map.put(player.getUniqueId(), loc);
            player.sendMessage(Component.text("Pos 2 set: (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")", NamedTextColor.AQUA));
        }
    }

    private void openToolsGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27,
                Component.text(TOOLS_TITLE, NamedTextColor.LIGHT_PURPLE));

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

        if (title.equals(TOOLS_TITLE)) {
            event.setCancelled(true);
            handleToolClick(event, player);
        } else if (title.equals(MATERIAL_TITLE)) {
            handleMaterialSelect(event, player);
        }
    }

    private void handleToolClick(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getItemMeta() == null || clicked.getItemMeta().displayName() == null) return;

        String toolName = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());
        UUID uuid = player.getUniqueId();

        switch (toolName) {
            case "Fill" -> { playerModes.put(uuid, ToolMode.FILL); player.closeInventory(); openMaterialGui(player); }
            case "Replace" -> { playerModes.put(uuid, ToolMode.REPLACE); player.closeInventory(); openMaterialGui(player); }
            case "Walls" -> { playerModes.put(uuid, ToolMode.WALLS); player.closeInventory(); openMaterialGui(player); }
            case "Hollow" -> { playerModes.put(uuid, ToolMode.HOLLOW); player.closeInventory(); openMaterialGui(player); }
            case "Line" -> { playerModes.put(uuid, ToolMode.LINE); player.closeInventory(); openMaterialGui(player); }
            case "Layer" -> { playerModes.put(uuid, ToolMode.LAYER); player.closeInventory(); openMaterialGui(player); }
            case "Clear" -> {
                player.closeInventory();
                CreativePlot plot = getAccessiblePlot(player);
                if (plot != null) executeTool(player, plot, ToolMode.FILL, Material.AIR);
            }
        }
    }

    private void openMaterialGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54,
                Component.text(MATERIAL_TITLE, NamedTextColor.GREEN));

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

        if (rawSlot >= 54) {
            if (clicked != null && clicked.getType().isBlock() && clicked.getType() != Material.AIR) {
                applyMaterial(player, clicked.getType());
            }
            return;
        }

        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.PAPER) return;
        if (!clicked.getType().isBlock()) {
            player.sendMessage(Component.text("Select a block!", NamedTextColor.RED));
            return;
        }
        applyMaterial(player, clicked.getType());
    }

    private void applyMaterial(Player player, Material mat) {
        UUID uuid = player.getUniqueId();
        ToolMode mode = playerModes.get(uuid);
        player.closeInventory();

        if (mode == null) {
            player.sendMessage(Component.text("No tool selected!", NamedTextColor.RED));
            return;
        }

        CreativePlot plot = getAccessiblePlot(player);
        if (plot == null) {
            player.sendMessage(Component.text("You must be on your own plot!", NamedTextColor.RED));
            return;
        }

        executeTool(player, plot, mode, mat);
    }

    private void executeTool(Player player, CreativePlot plot, ToolMode mode, Material material) {
        UUID uuid = player.getUniqueId();
        Location p1 = pos1Map.get(uuid);
        Location p2 = pos2Map.get(uuid);

        if (p1 == null || p2 == null) {
            player.sendMessage(Component.text("Set both positions first! (Left/Right click with Magic Axe or //pos1 //pos2)", NamedTextColor.RED));
            return;
        }

        int minX = Math.max(plot.getMinX(), Math.min(p1.getBlockX(), p2.getBlockX()));
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.max(plot.getMinZ(), Math.min(p1.getBlockZ(), p2.getBlockZ()));
        int maxX = Math.min(plot.getMaxX(), Math.max(p1.getBlockX(), p2.getBlockX()));
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int maxZ = Math.min(plot.getMaxZ(), Math.max(p1.getBlockZ(), p2.getBlockZ()));

        World world = p1.getWorld();
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
                    Material from = replaceMaterial.getOrDefault(uuid, Material.STONE);
                    for (int x = minX; x <= maxX; x++)
                        for (int y = minY; y <= maxY; y++)
                            for (int z = minZ; z <= maxZ; z++) {
                                Block b = world.getBlockAt(x, y, z);
                                if (b.getType() == from) {
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
}

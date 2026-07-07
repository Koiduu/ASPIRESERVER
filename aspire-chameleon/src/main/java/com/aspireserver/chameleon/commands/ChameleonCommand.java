package com.aspireserver.chameleon.commands;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.config.ConfigManager;
import com.aspireserver.chameleon.config.ConfigManager.MapData;
import com.aspireserver.chameleon.config.ConfigManager.SkinEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ChameleonCommand implements CommandExecutor, TabCompleter {

    private final MecchaChameleon plugin;

    public ChameleonCommand(MecchaChameleon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setlobby" -> {
                if (!player.hasPermission("chameleon.admin")) return noPermission(player);
                plugin.getConfigManager().setLobbySpawn(player.getLocation());
                player.sendMessage(Component.text("[Chameleon] Lobby spawn set!", NamedTextColor.GREEN));
            }
            case "sethunterroom" -> {
                if (!player.hasPermission("chameleon.admin")) return noPermission(player);
                plugin.getConfigManager().setHunterRoom(player.getLocation());
                player.sendMessage(Component.text("[Chameleon] Hunter room set!", NamedTextColor.GREEN));
            }
            case "map" -> handleMapCommand(player, args);
            case "forcestart" -> {
                if (!player.hasPermission("chameleon.admin")) return noPermission(player);
                if (plugin.getGameManager().isGameActive()) {
                    player.sendMessage(Component.text("A game is already active!", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /chameleon forcestart <map>", NamedTextColor.RED));
                    return true;
                }
                String mapId = args[1].toLowerCase();
                ConfigManager cfg = plugin.getConfigManager();
                String chameleonWorld = cfg.getWorldName();
                List<Player> participants = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().getName().equalsIgnoreCase(chameleonWorld)) {
                        participants.add(p);
                    }
                }
                if (plugin.getGameManager().startGame(mapId,
                        cfg.getDefaultRoundDuration(), cfg.getDefaultHidingDuration(),
                        cfg.getDefaultSeekerCount(), participants)) {
                    player.sendMessage(Component.text("[Chameleon] Game force-started!", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Failed to start. Does the map exist?", NamedTextColor.RED));
                }
            }
            case "stop" -> {
                if (!player.hasPermission("chameleon.admin")) return noPermission(player);
                if (!plugin.getGameManager().isGameActive()) {
                    player.sendMessage(Component.text("No game active.", NamedTextColor.RED));
                    return true;
                }
                plugin.getGameManager().forceStop();
                player.sendMessage(Component.text("[Chameleon] Game stopped.", NamedTextColor.GREEN));
            }
            case "removespot" -> {
                if (!player.hasPermission("chameleon.admin")) return noPermission(player);
                ArmorStand closest = null;
                double closestDist = Double.MAX_VALUE;
                for (Entity entity : player.getNearbyEntities(20, 20, 20)) {
                    if (!(entity instanceof ArmorStand as)) continue;
                    double dist = entity.getLocation().distanceSquared(player.getLocation());
                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = as;
                    }
                }
                if (closest != null) {
                    closest.remove();
                    player.sendMessage(Component.text("[Chameleon] Removed closest armor stand (" + String.format("%.1f", Math.sqrt(closestDist)) + " blocks away)", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("No armor stands found within 20 blocks.", NamedTextColor.RED));
                }
            }
            case "skins" -> {
                if (!player.hasPermission("chameleon.admin")) return noPermission(player);
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /chameleon skins <map>", NamedTextColor.RED));
                    return true;
                }
                openSkinsGui(player, args[1].toLowerCase());
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleMapCommand(Player player, String[] args) {
        if (!player.hasPermission("chameleon.admin")) { noPermission(player); return; }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /chameleon map <create|addhiderspawn|setseekerspawn|addskin|removeskin> ...", NamedTextColor.RED));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /chameleon map create <name>", NamedTextColor.RED));
                    return;
                }
                String name = args[2].toLowerCase();
                plugin.getConfigManager().createMap(name);
                player.sendMessage(Component.text("[Chameleon] Map '" + name + "' created!", NamedTextColor.GREEN));
            }
            case "addhiderspawn" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /chameleon map addhiderspawn <map>", NamedTextColor.RED));
                    return;
                }
                String mapId = args[2].toLowerCase();
                if (plugin.getConfigManager().getMap(mapId) == null) {
                    player.sendMessage(Component.text("Map not found. Create it first.", NamedTextColor.RED));
                    return;
                }
                plugin.getConfigManager().addHiderSpawn(mapId, player.getLocation());
                MapData data = plugin.getConfigManager().getMap(mapId);
                player.sendMessage(Component.text("[Chameleon] Hider spawn added! Total: " + data.hiderSpawns.size(), NamedTextColor.GREEN));
            }
            case "setseekerspawn" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /chameleon map setseekerspawn <map>", NamedTextColor.RED));
                    return;
                }
                String mapId = args[2].toLowerCase();
                if (plugin.getConfigManager().getMap(mapId) == null) {
                    player.sendMessage(Component.text("Map not found.", NamedTextColor.RED));
                    return;
                }
                plugin.getConfigManager().setSeekerSpawn(mapId, player.getLocation());
                player.sendMessage(Component.text("[Chameleon] Seeker spawn set!", NamedTextColor.GREEN));
            }
            case "addskin" -> {
                if (args.length < 5) {
                    player.sendMessage(Component.text("Usage: /chameleon map addskin <map> <skinName> <uuid>", NamedTextColor.RED));
                    return;
                }
                String mapId = args[2].toLowerCase();
                String skinName = args[3].replace("_", " ");
                String uuid = args[4].replace("-", "");
                plugin.getConfigManager().addSkin(mapId, skinName, uuid);
                plugin.getSkinCache().fetchSkinByUuid(uuid, skinName);
                player.sendMessage(Component.text("[Chameleon] Skin '" + skinName + "' added to " + mapId, NamedTextColor.GREEN));
            }
            case "removeskin" -> {
                if (args.length < 4) {
                    player.sendMessage(Component.text("Usage: /chameleon map removeskin <map> <skinName>", NamedTextColor.RED));
                    return;
                }
                String mapId = args[2].toLowerCase();
                String skinName = args[3].replace("_", " ");
                if (plugin.getConfigManager().removeSkin(mapId, skinName)) {
                    player.sendMessage(Component.text("[Chameleon] Removed '" + skinName + "'", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Skin not found.", NamedTextColor.RED));
                }
            }
            default -> player.sendMessage(Component.text("Unknown map subcommand.", NamedTextColor.RED));
        }
    }

    private void openSkinsGui(Player player, String mapId) {
        MapData data = plugin.getConfigManager().getMap(mapId);
        if (data == null) {
            player.sendMessage(Component.text("Map not found.", NamedTextColor.RED));
            return;
        }
        List<SkinEntry> skins = data.skins;
        int size = Math.max(9, ((skins.size() + 2 + 8) / 9) * 9);
        size = Math.min(54, size);

        Inventory gui = Bukkit.createInventory(null, size,
                Component.text("Skins: " + mapId, NamedTextColor.DARK_GREEN));

        for (int i = 0; i < skins.size() && i < size - 1; i++) {
            SkinEntry entry = skins.get(i);
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(entry.name(), NamedTextColor.GREEN));
            meta.lore(List.of(
                    Component.text("UUID: " + entry.uuid(), NamedTextColor.DARK_GRAY),
                    Component.text(""),
                    Component.text("Click to REMOVE", NamedTextColor.RED)
            ));
            item.setItemMeta(meta);
            gui.setItem(i, item);
        }

        ItemStack addBtn = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta addMeta = addBtn.getItemMeta();
        addMeta.displayName(Component.text("+ Add Skin", NamedTextColor.GREEN));
        addMeta.lore(List.of(Component.text("/chameleon map addskin " + mapId + " <name> <uuid>", NamedTextColor.GRAY)));
        addBtn.setItemMeta(addMeta);
        gui.setItem(size - 1, addBtn);

        player.openInventory(gui);
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== MecchaChameleon ===", NamedTextColor.GREEN));
        player.sendMessage(Component.text("/chameleon setlobby", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/chameleon sethunterroom", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/chameleon map create <name>", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/chameleon map addhiderspawn <map>", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/chameleon map setseekerspawn <map>", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/chameleon map addskin <map> <name> <uuid>", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/chameleon map removeskin <map> <name>", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/chameleon skins <map> — GUI to manage skins", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/chameleon forcestart <map>", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/chameleon stop", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/chameleon removespot — remove closest armor stand", NamedTextColor.AQUA));
    }

    private boolean noPermission(Player player) {
        player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("setlobby", "sethunterroom", "map", "forcestart", "stop", "skins", "removespot"));
        } else if (args.length == 2) {
            if ("map".equalsIgnoreCase(args[0])) {
                completions.addAll(List.of("create", "addhiderspawn", "setseekerspawn", "addskin", "removeskin"));
            } else if ("forcestart".equalsIgnoreCase(args[0]) || "skins".equalsIgnoreCase(args[0])) {
                completions.addAll(plugin.getConfigManager().getMapIds());
            }
        } else if (args.length == 3 && "map".equalsIgnoreCase(args[0])) {
            String sub = args[1].toLowerCase();
            if ("addhiderspawn".equals(sub) || "setseekerspawn".equals(sub) || "addskin".equals(sub) || "removeskin".equals(sub)) {
                completions.addAll(plugin.getConfigManager().getMapIds());
            }
        } else if (args.length == 4 && "map".equalsIgnoreCase(args[0]) && "removeskin".equalsIgnoreCase(args[1])) {
            MapData data = plugin.getConfigManager().getMap(args[2].toLowerCase());
            if (data != null) {
                for (SkinEntry s : data.skins) {
                    completions.add(s.name().replace(" ", "_"));
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(prefix));
        return completions;
    }
}

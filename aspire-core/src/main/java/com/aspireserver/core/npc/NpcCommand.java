package com.aspireserver.core.npc;

import com.aspireserver.core.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class NpcCommand implements CommandExecutor, TabCompleter {

    private final NpcManager npcManager;

    public NpcCommand(NpcManager npcManager) {
        this.npcManager = npcManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("aspire.admin.npc")) {
            MessageUtil.sendError(player, "No permission!");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> {
                if (args.length < 3) {
                    MessageUtil.sendError(player, "Usage: /npc create <id> <displayName>");
                    return true;
                }
                String id = args[1];
                String displayName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                handleCreate(player, id, displayName);
            }
            case "remove", "delete" -> {
                if (args.length < 2) {
                    MessageUtil.sendError(player, "Usage: /npc remove <id>");
                    return true;
                }
                handleRemove(player, args[1]);
            }
            case "edit" -> {
                if (args.length < 2) {
                    MessageUtil.sendError(player, "Usage: /npc edit <id>");
                    return true;
                }
                handleEdit(player, args[1]);
            }
            case "list" -> handleList(player);
            case "movehere" -> {
                if (args.length < 2) {
                    MessageUtil.sendError(player, "Usage: /npc movehere <id>");
                    return true;
                }
                handleMoveHere(player, args[1]);
            }
            case "setcmd" -> {
                if (args.length < 3) {
                    MessageUtil.sendError(player, "Usage: /npc setcmd <id> <command without />)");
                    return true;
                }
                handleSetCmd(player, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
            }
            case "setskin" -> {
                if (args.length < 3) {
                    MessageUtil.sendError(player, "Usage: /npc setskin <id> <playerName>");
                    return true;
                }
                handleSetSkin(player, args[1], args[2]);
            }
            default -> sendUsage(player);
        }
        return true;
    }

    private void handleCreate(Player player, String id, String displayName) {
        if (npcManager.getNpc(id) != null) {
            MessageUtil.sendError(player, "An NPC with that ID already exists!");
            return;
        }
        npcManager.createNpc(id, displayName, NpcAction.WARP_LOBBY, player.getLocation());
        MessageUtil.sendSuccess(player, "NPC '" + id + "' created! Use /npc edit " + id + " to set action.");
    }

    private void handleRemove(Player player, String id) {
        if (npcManager.removeNpc(id)) {
            MessageUtil.sendSuccess(player, "NPC '" + id + "' removed.");
        } else {
            MessageUtil.sendError(player, "NPC not found!");
        }
    }

    private void handleEdit(Player player, String id) {
        NpcData npc = npcManager.getNpc(id);
        if (npc == null) {
            MessageUtil.sendError(player, "NPC not found!");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27,
            Component.text("Edit NPC: " + id, NamedTextColor.GOLD));

        int slot = 0;
        for (NpcAction action : NpcAction.values()) {
            Material mat = switch (action) {
                case BUILD_BATTLE_SOLO -> Material.WOODEN_SWORD;
                case BUILD_BATTLE_TEAMS -> Material.IRON_SWORD;
                case BUILD_BATTLE_PRO_SOLO -> Material.DIAMOND_SWORD;
                case BUILD_BATTLE_PRO_TEAMS -> Material.NETHERITE_SWORD;
                case WARP_SMP -> Material.GRASS_BLOCK;
                case WARP_CREATIVE -> Material.CRAFTING_TABLE;
                case WARP_CHAMELEON -> Material.LIME_DYE;
                case WARP_LOBBY -> Material.COMPASS;
                case CUSTOM_COMMAND -> Material.COMMAND_BLOCK;
            };

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            boolean isSelected = npc.getAction() == action;
            NamedTextColor color = isSelected ? NamedTextColor.GREEN : NamedTextColor.WHITE;
            meta.displayName(Component.text(action.getDisplayName(), color));
            if (isSelected) {
                meta.lore(List.of(Component.text("SELECTED", NamedTextColor.GREEN)));
            } else {
                meta.lore(List.of(Component.text("Click to set", NamedTextColor.GRAY)));
            }
            item.setItemMeta(meta);
            gui.setItem(slot, item);
            slot++;
        }

        player.openInventory(gui);
    }

    private void handleList(Player player) {
        var allNpcs = npcManager.getAllNpcs();
        if (allNpcs.isEmpty()) {
            MessageUtil.send(player, "No NPCs created yet.");
            return;
        }
        MessageUtil.sendInfo(player, "--- NPCs (" + allNpcs.size() + ") ---");
        for (NpcData npc : allNpcs) {
            MessageUtil.send(player, "  " + npc.getId() + " - " + npc.getDisplayName()
                + " [" + npc.getAction().getDisplayName() + "]");
        }
    }

    private void handleMoveHere(Player player, String id) {
        NpcData npc = npcManager.getNpc(id);
        if (npc == null) {
            MessageUtil.sendError(player, "NPC not found!");
            return;
        }
        npc.setLocation(player.getLocation());
        npcManager.spawnNpc(npc);
        npcManager.saveNpcs();
        MessageUtil.sendSuccess(player, "NPC moved to your location.");
    }

    private void handleSetCmd(Player player, String id, String cmd) {
        NpcData npc = npcManager.getNpc(id);
        if (npc == null) {
            MessageUtil.sendError(player, "NPC not found!");
            return;
        }
        npc.setAction(NpcAction.CUSTOM_COMMAND);
        npc.setCustomCommand(cmd);
        npcManager.saveNpcs();
        MessageUtil.sendSuccess(player, "NPC '" + id + "' will now run: /" + cmd);
    }

    private void handleSetSkin(Player player, String id, String skinName) {
        NpcData npc = npcManager.getNpc(id);
        if (npc == null) {
            MessageUtil.sendError(player, "NPC not found!");
            return;
        }
        npc.setSkinName(skinName);
        npcManager.saveNpcs();
        npcManager.spawnNpc(npc);
        MessageUtil.sendSuccess(player, "NPC '" + id + "' skin set to: " + skinName + ". Player head will display above the NPC.");
    }

    private void sendUsage(Player player) {
        MessageUtil.sendInfo(player, "--- NPC Commands ---");
        MessageUtil.send(player, "/npc create <id> <name> - Create NPC at your location");
        MessageUtil.send(player, "/npc edit <id> - Open action selector GUI");
        MessageUtil.send(player, "/npc remove <id> - Remove NPC");
        MessageUtil.send(player, "/npc movehere <id> - Move NPC to your position");
        MessageUtil.send(player, "/npc setcmd <id> <command> - Set custom command (no /)");
        MessageUtil.send(player, "/npc setskin <id> <playerName> - Set NPC skin to a player");
        MessageUtil.send(player, "/npc list - List all NPCs");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return List.of("create", "edit", "remove", "movehere", "setcmd", "setskin", "list").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("remove")
            || args[0].equalsIgnoreCase("movehere") || args[0].equalsIgnoreCase("setcmd")
            || args[0].equalsIgnoreCase("setskin"))) {
            return npcManager.getAllNpcs().stream()
                .map(NpcData::getId)
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setskin")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).toList();
        }
        return List.of();
    }
}

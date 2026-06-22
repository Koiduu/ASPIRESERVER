package com.aspireserver.smp.commands;

import com.aspireserver.smp.claim.Claim;
import com.aspireserver.smp.claim.ClaimManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClaimCommand implements CommandExecutor, TabCompleter {

    private final ClaimManager claimManager;
    private final Map<UUID, int[]> pos1Map;
    private final Map<UUID, int[]> pos2Map;

    public ClaimCommand(ClaimManager claimManager) {
        this.claimManager = claimManager;
        this.pos1Map = new HashMap<>();
        this.pos2Map = new HashMap<>();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (label.equalsIgnoreCase("unclaim")) {
            return handleUnclaim(player);
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "pos1" -> {
                int x = player.getLocation().getBlockX();
                int z = player.getLocation().getBlockZ();
                pos1Map.put(player.getUniqueId(), new int[]{x, z});
                player.sendMessage(Component.text("Position 1 set: " + x + ", " + z, NamedTextColor.GREEN));
            }
            case "pos2" -> {
                int x = player.getLocation().getBlockX();
                int z = player.getLocation().getBlockZ();
                pos2Map.put(player.getUniqueId(), new int[]{x, z});
                player.sendMessage(Component.text("Position 2 set: " + x + ", " + z, NamedTextColor.GREEN));
            }
            case "confirm" -> handleConfirm(player);
            case "info" -> handleInfo(player);
            case "list" -> handleList(player);
            case "here" -> {
                int radius = 5;
                if (args.length >= 2) {
                    try {
                        radius = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {}
                }
                handleClaimHere(player, radius);
            }
            default -> sendUsage(player);
        }
        return true;
    }

    private void handleClaimHere(Player player, int radius) {
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        int minX = x - radius;
        int minZ = z - radius;
        int maxX = x + radius;
        int maxZ = z + radius;

        String world = player.getWorld().getName();
        boolean success = claimManager.createClaim(player.getUniqueId(), world, minX, minZ, maxX, maxZ);
        if (success) {
            int area = (2 * radius + 1) * (2 * radius + 1);
            player.sendMessage(Component.text("Claimed " + area + " blocks around you!", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Used: " + claimManager.getUsedBlocks(player.getUniqueId())
                + "/" + claimManager.getClaimLimit(player.getUniqueId()), NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("Cannot claim here! Area overlaps or exceeds your limit.", NamedTextColor.RED));
            player.sendMessage(Component.text("Used: " + claimManager.getUsedBlocks(player.getUniqueId())
                + "/" + claimManager.getClaimLimit(player.getUniqueId()), NamedTextColor.GRAY));
        }
    }

    private void handleConfirm(Player player) {
        int[] p1 = pos1Map.get(player.getUniqueId());
        int[] p2 = pos2Map.get(player.getUniqueId());

        if (p1 == null || p2 == null) {
            player.sendMessage(Component.text("Set both positions first! (/claim pos1, /claim pos2)", NamedTextColor.RED));
            return;
        }

        String world = player.getWorld().getName();
        boolean success = claimManager.createClaim(player.getUniqueId(), world, p1[0], p1[1], p2[0], p2[1]);

        if (success) {
            player.sendMessage(Component.text("Land claimed successfully!", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Used: " + claimManager.getUsedBlocks(player.getUniqueId())
                + "/" + claimManager.getClaimLimit(player.getUniqueId()), NamedTextColor.GRAY));
            pos1Map.remove(player.getUniqueId());
            pos2Map.remove(player.getUniqueId());
        } else {
            player.sendMessage(Component.text("Cannot claim! Area overlaps or exceeds your limit.", NamedTextColor.RED));
            player.sendMessage(Component.text("Used: " + claimManager.getUsedBlocks(player.getUniqueId())
                + "/" + claimManager.getClaimLimit(player.getUniqueId()), NamedTextColor.GRAY));
        }
    }

    private boolean handleUnclaim(Player player) {
        boolean removed = claimManager.removeClaim(player.getUniqueId(), player.getLocation());
        if (removed) {
            player.sendMessage(Component.text("Claim removed!", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("No claim found here that you own.", NamedTextColor.RED));
        }
        return true;
    }

    private void handleInfo(Player player) {
        Claim claim = claimManager.getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage(Component.text("This area is unclaimed.", NamedTextColor.GRAY));
            return;
        }
        String ownerName = org.bukkit.Bukkit.getOfflinePlayer(claim.getOwner()).getName();
        player.sendMessage(Component.text("--- Claim Info ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Owner: " + (ownerName != null ? ownerName : claim.getOwner()), NamedTextColor.GRAY));
        player.sendMessage(Component.text("Area: " + claim.getArea() + " blocks", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Trusted: " + claim.getTrustedPlayers().size() + " player(s)", NamedTextColor.GRAY));
    }

    private void handleList(Player player) {
        var claims = claimManager.getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) {
            player.sendMessage(Component.text("You have no claims.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("--- Your Claims (" + claims.size() + ") ---", NamedTextColor.GOLD));
        for (int i = 0; i < claims.size(); i++) {
            Claim c = claims.get(i);
            player.sendMessage(Component.text((i + 1) + ". " + c.getWorldName()
                + " (" + c.getMinX() + "," + c.getMinZ() + " -> " + c.getMaxX() + "," + c.getMaxZ() + ") "
                + c.getArea() + " blocks", NamedTextColor.GRAY));
        }
        player.sendMessage(Component.text("Total: " + claimManager.getUsedBlocks(player.getUniqueId())
            + "/" + claimManager.getClaimLimit(player.getUniqueId()), NamedTextColor.AQUA));
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("--- Claim Commands ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/claim here [radius] - Claim area around you", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/claim pos1 - Set first corner", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/claim pos2 - Set second corner", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/claim confirm - Confirm claim", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/claim info - Info about claim at location", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/claim list - List your claims", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/unclaim - Remove claim at location", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return List.of("pos1", "pos2", "confirm", "here", "info", "list").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}

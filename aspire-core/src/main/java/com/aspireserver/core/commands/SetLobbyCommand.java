package com.aspireserver.core.commands;

import com.aspireserver.core.AspireCore;
import com.aspireserver.core.utils.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SetLobbyCommand implements CommandExecutor {

    private final AspireCore plugin;

    public SetLobbyCommand(AspireCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("aspire.admin.setlobby")) {
            MessageUtil.sendError(player, "No permission!");
            return true;
        }

        Location loc = player.getLocation();
        plugin.getConfig().set("lobby.world", loc.getWorld().getName());
        plugin.getConfig().set("lobby.x", loc.getX());
        plugin.getConfig().set("lobby.y", loc.getY());
        plugin.getConfig().set("lobby.z", loc.getZ());
        plugin.getConfig().set("lobby.yaw", loc.getYaw());
        plugin.getConfig().set("lobby.pitch", loc.getPitch());
        plugin.saveConfig();

        MessageUtil.sendSuccess(player, "Lobby spawn set to your current location in world '" + loc.getWorld().getName() + "'!");
        return true;
    }
}

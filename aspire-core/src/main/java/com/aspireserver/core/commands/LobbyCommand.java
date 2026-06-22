package com.aspireserver.core.commands;

import com.aspireserver.core.AspireCore;
import com.aspireserver.core.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LobbyCommand implements CommandExecutor {

    private final AspireCore plugin;

    public LobbyCommand(AspireCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        String worldName = plugin.getConfig().getString("lobby.world", "world");
        double x = plugin.getConfig().getDouble("lobby.x", 0);
        double y = plugin.getConfig().getDouble("lobby.y", 100);
        double z = plugin.getConfig().getDouble("lobby.z", 0);
        float yaw = (float) plugin.getConfig().getDouble("lobby.yaw", 0);
        float pitch = (float) plugin.getConfig().getDouble("lobby.pitch", 0);

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            MessageUtil.sendError(player, "Lobby world not found!");
            return true;
        }

        Location loc = new Location(world, x, y, z, yaw, pitch);
        player.teleport(loc);
        MessageUtil.sendSuccess(player, "Teleported to lobby!");
        return true;
    }
}

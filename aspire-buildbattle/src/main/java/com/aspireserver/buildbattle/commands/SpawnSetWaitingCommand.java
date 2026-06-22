package com.aspireserver.buildbattle.commands;

import com.aspireserver.buildbattle.AspireBuildBattle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SpawnSetWaitingCommand implements CommandExecutor {

    private final AspireBuildBattle plugin;

    public SpawnSetWaitingCommand(AspireBuildBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("aspire.buildbattle.admin")) {
            player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        Location loc = player.getLocation();
        plugin.getConfig().set("waiting-lobby.world", loc.getWorld().getName());
        plugin.getConfig().set("waiting-lobby.x", loc.getX());
        plugin.getConfig().set("waiting-lobby.y", loc.getY());
        plugin.getConfig().set("waiting-lobby.z", loc.getZ());
        plugin.getConfig().set("waiting-lobby.yaw", loc.getYaw());
        plugin.getConfig().set("waiting-lobby.pitch", loc.getPitch());
        plugin.saveConfig();

        plugin.getArenaManager().loadWaitingLobby();

        player.sendMessage(Component.text("Waiting lobby spawn set to your location!", NamedTextColor.GREEN));
        return true;
    }
}

package com.aspireserver.core.admin;

import com.aspireserver.core.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class KickCommand implements CommandExecutor {

    private final AdminManager adminManager;

    public KickCommand(AdminManager adminManager) {
        this.adminManager = adminManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("aspire.admin.kick")) {
            if (sender instanceof Player p) MessageUtil.sendError(p, "No permission!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /kick <player> [reason]", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
            return true;
        }

        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Kicked by staff";
        target.kick(Component.text(reason, NamedTextColor.RED));
        Bukkit.broadcast(Component.text(target.getName() + " was kicked: " + reason, NamedTextColor.YELLOW));
        return true;
    }
}

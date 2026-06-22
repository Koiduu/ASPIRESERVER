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

public class TempBanCommand implements CommandExecutor {

    private final AdminManager adminManager;

    public TempBanCommand(AdminManager adminManager) {
        this.adminManager = adminManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("aspire.admin.tempban")) {
            if (sender instanceof Player p) MessageUtil.sendError(p, "No permission!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /tempban <player> <duration> <reason>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Duration format: 1h, 1d, 7d, 30d", NamedTextColor.GRAY));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
            return true;
        }

        long duration = parseDuration(args[1]);
        if (duration <= 0) {
            sender.sendMessage(Component.text("Invalid duration! Use format: 1h, 1d, 7d", NamedTextColor.RED));
            return true;
        }

        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        adminManager.tempBan(target.getUniqueId(), reason, duration);
        target.kick(Component.text("You have been temporarily banned: " + reason + "\nDuration: " + args[1], NamedTextColor.RED));
        Bukkit.broadcast(Component.text(target.getName() + " has been temp-banned for " + args[1] + ": " + reason, NamedTextColor.RED));
        return true;
    }

    private long parseDuration(String input) {
        try {
            char unit = input.charAt(input.length() - 1);
            long value = Long.parseLong(input.substring(0, input.length() - 1));
            return switch (unit) {
                case 's' -> value * 1000;
                case 'm' -> value * 60 * 1000;
                case 'h' -> value * 60 * 60 * 1000;
                case 'd' -> value * 24 * 60 * 60 * 1000;
                case 'w' -> value * 7 * 24 * 60 * 60 * 1000;
                default -> -1;
            };
        } catch (Exception e) {
            return -1;
        }
    }
}

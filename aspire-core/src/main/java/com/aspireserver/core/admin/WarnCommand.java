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

public class WarnCommand implements CommandExecutor {

    private final AdminManager adminManager;

    public WarnCommand(AdminManager adminManager) {
        this.adminManager = adminManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("aspire.admin.warn")) {
            if (sender instanceof Player p) MessageUtil.sendError(p, "No permission!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /warn <player> <reason>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
            return true;
        }

        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        adminManager.warn(target.getUniqueId(), reason);

        int warnCount = adminManager.getWarnings(target.getUniqueId()).size();
        MessageUtil.sendError(target, "You have been warned: " + reason + " (Warning #" + warnCount + ")");
        if (sender instanceof Player p) {
            MessageUtil.sendSuccess(p, "Warned " + target.getName() + " (" + warnCount + " total warnings)");
        }

        if (warnCount >= 3) {
            target.kick(Component.text("Auto-kicked: Reached 3 warnings.", NamedTextColor.RED));
        }
        return true;
    }
}

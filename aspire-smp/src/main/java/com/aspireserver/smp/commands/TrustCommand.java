package com.aspireserver.smp.commands;

import com.aspireserver.smp.claim.ClaimManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TrustCommand implements CommandExecutor {

    private final ClaimManager claimManager;

    public TrustCommand(ClaimManager claimManager) {
        this.claimManager = claimManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /trust <player>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You cannot trust yourself!", NamedTextColor.RED));
            return true;
        }

        claimManager.addTrusted(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(Component.text("Trusted " + target.getName() + " on all your claims!", NamedTextColor.GREEN));
        target.sendMessage(Component.text(player.getName() + " trusted you on their land!", NamedTextColor.GREEN));
        return true;
    }
}

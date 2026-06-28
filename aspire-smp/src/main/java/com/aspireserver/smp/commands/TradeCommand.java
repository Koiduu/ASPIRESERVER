package com.aspireserver.smp.commands;

import com.aspireserver.smp.trade.TradeManager;
import com.aspireserver.smp.trade.TradeSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TradeCommand implements CommandExecutor, TabCompleter {

    private final TradeManager tradeManager;

    public TradeCommand(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /trade <player> | /trade accept | /trade deny", NamedTextColor.YELLOW));
            return true;
        }

        if (args[0].equalsIgnoreCase("accept")) {
            return handleAccept(player);
        }

        if (args[0].equalsIgnoreCase("deny") || args[0].equalsIgnoreCase("cancel")) {
            return handleDeny(player);
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(Component.text("You can't trade with yourself!", NamedTextColor.RED));
            return true;
        }

        if (tradeManager.isInTrade(player.getUniqueId())) {
            player.sendMessage(Component.text("You're already in a trade!", NamedTextColor.RED));
            return true;
        }

        if (tradeManager.isInTrade(target.getUniqueId())) {
            player.sendMessage(Component.text(target.getName() + " is already in a trade!", NamedTextColor.RED));
            return true;
        }

        // Check if target already sent us a request — auto-accept
        if (tradeManager.hasPendingRequest(target.getUniqueId(), player.getUniqueId())) {
            tradeManager.removeRequest(target.getUniqueId());
            startTrade(player, target);
            return true;
        }

        tradeManager.sendRequest(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(Component.text("Trade request sent to " + target.getName() + "!", NamedTextColor.GREEN));

        target.sendMessage(Component.text(player.getName() + " wants to trade! ", NamedTextColor.GOLD)
            .append(Component.text("[Accept]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/trade accept")))
            .append(Component.text(" "))
            .append(Component.text("[Deny]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/trade deny"))));
        return true;
    }

    private boolean handleAccept(Player player) {
        // Find who sent us a request
        for (var entry : List.copyOf(List.of(Bukkit.getOnlinePlayers().toArray(new Player[0])))) {
            if (tradeManager.hasPendingRequest(entry.getUniqueId(), player.getUniqueId())) {
                tradeManager.removeRequest(entry.getUniqueId());
                startTrade(player, entry);
                return true;
            }
        }
        player.sendMessage(Component.text("No pending trade requests!", NamedTextColor.RED));
        return true;
    }

    private boolean handleDeny(Player player) {
        // Cancel our outgoing request
        if (tradeManager.getPendingTarget(player.getUniqueId()) != null) {
            UUID targetId = tradeManager.getPendingTarget(player.getUniqueId());
            tradeManager.removeRequest(player.getUniqueId());
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                target.sendMessage(Component.text(player.getName() + " cancelled the trade request.", NamedTextColor.RED));
            }
            player.sendMessage(Component.text("Trade request cancelled.", NamedTextColor.YELLOW));
            return true;
        }
        // Deny incoming requests
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (tradeManager.hasPendingRequest(online.getUniqueId(), player.getUniqueId())) {
                tradeManager.removeRequest(online.getUniqueId());
                online.sendMessage(Component.text(player.getName() + " denied your trade request.", NamedTextColor.RED));
                player.sendMessage(Component.text("Trade request denied.", NamedTextColor.YELLOW));
                return true;
            }
        }
        player.sendMessage(Component.text("No pending trade requests!", NamedTextColor.RED));
        return true;
    }

    private void startTrade(Player player1, Player player2) {
        TradeSession session = tradeManager.createSession(player1.getUniqueId(), player2.getUniqueId());
        player1.sendMessage(Component.text("Trade started with " + player2.getName() + "!", NamedTextColor.GREEN));
        player2.sendMessage(Component.text("Trade started with " + player1.getName() + "!", NamedTextColor.GREEN));
        TradeGui.openTradeGui(player1, session);
        TradeGui.openTradeGui(player2, session);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("accept");
            completions.add("deny");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player && !p.equals(sender)) {
                    completions.add(p.getName());
                }
            }
            String prefix = args[0].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(prefix));
        }
        return completions;
    }

}

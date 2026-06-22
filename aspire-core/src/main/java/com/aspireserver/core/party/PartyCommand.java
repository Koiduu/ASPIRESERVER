package com.aspireserver.core.party;

import com.aspireserver.core.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PartyCommand implements CommandExecutor, TabCompleter {

    private final PartyManager partyManager;

    public PartyCommand(PartyManager partyManager) {
        this.partyManager = partyManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (label.equalsIgnoreCase("pc")) {
            handleChat(player, args);
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "invite" -> {
                if (args.length < 2) {
                    MessageUtil.sendError(player, "Usage: /party invite <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    MessageUtil.sendError(player, "Player not found!");
                    return true;
                }
                partyManager.invite(player, target);
            }
            case "accept", "join" -> partyManager.acceptInvite(player);
            case "leave" -> partyManager.leave(player);
            case "disband" -> partyManager.disband(player);
            case "list" -> partyManager.listMembers(player);
            case "private" -> partyManager.togglePrivate(player);
            case "kickoffline" -> partyManager.kickOffline(player);
            case "chat" -> handleChatToggle(player);
            case "settings" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("allinvite")) {
                    partyManager.toggleAllInvite(player);
                } else {
                    MessageUtil.sendError(player, "Usage: /party settings allinvite");
                }
            }
            default -> {
                Player target = Bukkit.getPlayer(args[0]);
                if (target != null) {
                    partyManager.invite(player, target);
                } else {
                    MessageUtil.sendError(player, "Player not found or unknown subcommand.");
                }
            }
        }
        return true;
    }

    private void handleChat(Player player, String[] args) {
        Party party = partyManager.getParty(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendError(player, "You are not in a party!");
            return;
        }
        if (args.length == 0) {
            MessageUtil.sendError(player, "Usage: /pc <message>");
            return;
        }
        String message = String.join(" ", args);
        partyManager.broadcastToParty(party, "[Party] " + player.getName() + ": " + message);
    }

    private void handleChatToggle(Player player) {
        Party party = partyManager.getParty(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendError(player, "You are not in a party!");
            return;
        }
        MessageUtil.sendInfo(player, "Party chat toggled. Use /pc <message> or /party chat again to toggle.");
    }

    private void sendUsage(Player player) {
        MessageUtil.sendInfo(player, "--- Party Commands ---");
        MessageUtil.send(player, "/party <player> - Invite a player");
        MessageUtil.send(player, "/party accept - Accept an invite");
        MessageUtil.send(player, "/party leave - Leave the party");
        MessageUtil.send(player, "/party list - View members");
        MessageUtil.send(player, "/party private - Toggle private games");
        MessageUtil.send(player, "/party kickoffline - Kick offline members");
        MessageUtil.send(player, "/party chat - Toggle party chat");
        MessageUtil.send(player, "/pc <msg> - Send party message");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList(
                "invite", "accept", "leave", "disband", "list", "private", "kickoffline", "chat", "settings"
            ));
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
            return completions.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("settings")) {
            return List.of("allinvite");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}

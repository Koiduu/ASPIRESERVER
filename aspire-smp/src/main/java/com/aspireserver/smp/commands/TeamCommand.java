package com.aspireserver.smp.commands;

import com.aspireserver.smp.team.Team;
import com.aspireserver.smp.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

public class TeamCommand implements CommandExecutor, TabCompleter {

    private final TeamManager teamManager;

    public TeamCommand(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /team create <name>", NamedTextColor.RED));
                    return true;
                }
                String name = args[1];
                if (name.length() > 16) {
                    player.sendMessage(Component.text("Team name max 16 characters!", NamedTextColor.RED));
                    return true;
                }
                if (teamManager.createTeam(name, player.getUniqueId())) {
                    player.sendMessage(Component.text("Team '" + name + "' created!", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Could not create team. Name taken or you're already in a team.", NamedTextColor.RED));
                }
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /team invite <player>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
                    return true;
                }
                if (teamManager.invitePlayer(player.getUniqueId(), target.getUniqueId())) {
                    String teamName = teamManager.getPlayerTeamName(player.getUniqueId());
                    player.sendMessage(Component.text("Invited " + target.getName() + " to your team!", NamedTextColor.GREEN));
                    target.sendMessage(Component.text(player.getName() + " invited you to team '" + teamName + "'!", NamedTextColor.GOLD)
                            .append(Component.text(" [Accept]", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                                    .clickEvent(ClickEvent.runCommand("/team accept " + teamName))));
                } else {
                    player.sendMessage(Component.text("Cannot invite. You must be team leader.", NamedTextColor.RED));
                }
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /team accept <teamName>", NamedTextColor.RED));
                    return true;
                }
                String teamName = args[1];
                if (!teamManager.hasInvite(player.getUniqueId(), teamName)) {
                    player.sendMessage(Component.text("No invite from that team!", NamedTextColor.RED));
                    return true;
                }
                teamManager.clearInvite(player.getUniqueId(), teamName);
                if (teamManager.joinTeam(teamName, player.getUniqueId())) {
                    player.sendMessage(Component.text("Joined team '" + teamName + "'!", NamedTextColor.GREEN));
                    // Notify team
                    Team team = teamManager.getPlayerTeam(player.getUniqueId());
                    if (team != null) {
                        for (UUID member : team.getAllMembers()) {
                            Player p = Bukkit.getPlayer(member);
                            if (p != null && !p.equals(player)) {
                                p.sendMessage(Component.text(player.getName() + " joined the team!", NamedTextColor.GREEN));
                            }
                        }
                    }
                } else {
                    player.sendMessage(Component.text("Could not join. You may already be in a team.", NamedTextColor.RED));
                }
            }
            case "leave" -> {
                if (teamManager.leaveTeam(player.getUniqueId())) {
                    player.sendMessage(Component.text("Left your team.", NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("You're not in a team!", NamedTextColor.RED));
                }
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /team kick <player>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                UUID targetUuid;
                String targetName;
                if (target != null) {
                    targetUuid = target.getUniqueId();
                    targetName = target.getName();
                } else {
                    var offPlayer = Bukkit.getOfflinePlayer(args[1]);
                    targetUuid = offPlayer.getUniqueId();
                    targetName = offPlayer.getName() != null ? offPlayer.getName() : args[1];
                }

                boolean kicked = teamManager.voteKick(player.getUniqueId(), targetUuid);
                if (kicked) {
                    player.sendMessage(Component.text(targetName + " was kicked from the team! (60% vote reached)", NamedTextColor.GREEN));
                    if (target != null) {
                        target.sendMessage(Component.text("You were voted out of the team!", NamedTextColor.RED));
                    }
                } else {
                    int current = teamManager.getKickVoteCount(targetUuid);
                    int needed = teamManager.getKickVotesNeeded(targetUuid);
                    player.sendMessage(Component.text("Vote registered against " + targetName + " (" + current + "/" + needed + " needed)", NamedTextColor.YELLOW));
                }
            }
            case "chat", "c" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /team chat <message>", NamedTextColor.RED));
                    return true;
                }
                Team team = teamManager.getPlayerTeam(player.getUniqueId());
                if (team == null) {
                    player.sendMessage(Component.text("You're not in a team!", NamedTextColor.RED));
                    return true;
                }
                String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                Component chatMsg = Component.text("[Team] ", NamedTextColor.GREEN)
                        .append(Component.text(player.getName() + ": ", NamedTextColor.WHITE))
                        .append(Component.text(message, NamedTextColor.GRAY));
                for (UUID member : team.getAllMembers()) {
                    Player p = Bukkit.getPlayer(member);
                    if (p != null) p.sendMessage(chatMsg);
                }
            }
            case "info" -> {
                Team team = teamManager.getPlayerTeam(player.getUniqueId());
                if (team == null) {
                    player.sendMessage(Component.text("You're not in a team!", NamedTextColor.RED));
                    return true;
                }
                player.sendMessage(Component.text("--- Team: " + team.getName() + " ---", NamedTextColor.GOLD));
                String leaderName = Bukkit.getOfflinePlayer(team.getLeader()).getName();
                player.sendMessage(Component.text("Leader: " + (leaderName != null ? leaderName : "???"), NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Members (" + team.getAllMembers().size() + "):", NamedTextColor.GRAY));
                for (UUID member : team.getAllMembers()) {
                    String name = Bukkit.getOfflinePlayer(member).getName();
                    player.sendMessage(Component.text("  - " + (name != null ? name : member.toString()), NamedTextColor.WHITE));
                }
            }
            case "disband" -> {
                String teamName = teamManager.getPlayerTeamName(player.getUniqueId());
                if (teamName == null) {
                    player.sendMessage(Component.text("You're not in a team!", NamedTextColor.RED));
                    return true;
                }
                if (teamManager.disbandTeam(teamName, player.getUniqueId())) {
                    player.sendMessage(Component.text("Team disbanded.", NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("Only the leader can disband!", NamedTextColor.RED));
                }
            }
            default -> sendUsage(player);
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("--- Team Commands ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/team create <name>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/team invite <player>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/team accept <teamName>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/team leave", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/team kick <player> (60% vote to kick)", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/team chat <message>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/team info", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/team disband", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return List.of("create", "invite", "accept", "leave", "kick", "chat", "info", "disband").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}

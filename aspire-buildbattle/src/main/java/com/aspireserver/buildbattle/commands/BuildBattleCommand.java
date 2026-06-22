package com.aspireserver.buildbattle.commands;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.arena.Arena;
import com.aspireserver.buildbattle.game.GameMode;
import com.aspireserver.buildbattle.game.GameSession;
import com.aspireserver.buildbattle.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class BuildBattleCommand implements CommandExecutor, TabCompleter {

    private final AspireBuildBattle plugin;
    private final Map<GameMode, GameSession> waitingSessions;

    public BuildBattleCommand(AspireBuildBattle plugin) {
        this.plugin = plugin;
        this.waitingSessions = new EnumMap<>(GameMode.class);
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
            case "join", "queue" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /bb join <solo|teams|prosolo|proteams>", NamedTextColor.RED));
                    return true;
                }
                GameMode mode = parseMode(args[1]);
                if (mode == null) {
                    player.sendMessage(Component.text("Invalid mode! Use: solo, teams, prosolo, proteams", NamedTextColor.RED));
                    return true;
                }
                joinQueue(player, mode);
            }
            case "leave" -> leaveGame(player);
            case "start" -> {
                if (!player.hasPermission("aspire.buildbattle.admin")) {
                    player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /bb start <mode>", NamedTextColor.RED));
                    return true;
                }
                GameMode mode = parseMode(args[1]);
                if (mode == null) {
                    player.sendMessage(Component.text("Invalid mode!", NamedTextColor.RED));
                    return true;
                }
                forceStart(player, mode);
            }
            case "status" -> showStatus(player);
            default -> sendUsage(player);
        }
        return true;
    }

    private void joinQueue(Player player, GameMode mode) {
        if (plugin.getArenaManager().getPlayerSession(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("You are already in a game!", NamedTextColor.RED));
            return;
        }

        GameSession waitingSession = waitingSessions.get(mode);

        if (waitingSession != null && waitingSession.getState() == GameState.WAITING) {
            plugin.getArenaManager().joinSession(player.getUniqueId(), waitingSession);
            player.sendMessage(Component.text("Joined " + mode.getDisplayName() + "! (" + waitingSession.getPlayers().size() + " players waiting)", NamedTextColor.GREEN));
            return;
        }

        Arena arena = plugin.getArenaManager().getAvailableArena();
        if (arena == null) {
            player.sendMessage(Component.text("No arenas available! Please wait.", NamedTextColor.RED));
            return;
        }

        GameSession session = plugin.getArenaManager().createSession(arena, mode);
        waitingSessions.put(mode, session);
        plugin.getArenaManager().joinSession(player.getUniqueId(), session);

        player.sendMessage(Component.text("Joined " + mode.getDisplayName() + "! Lobby countdown started. (1 player)", NamedTextColor.GREEN));

        session.startLobbyCountdown();
    }

    private void leaveGame(Player player) {
        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session != null) {
            plugin.getArenaManager().leaveSession(player.getUniqueId());
            player.teleport(session.getArena().getLobbySpawn());
            player.sendMessage(Component.text("Left the game.", NamedTextColor.YELLOW));

            for (GameMode mode : GameMode.values()) {
                if (waitingSessions.get(mode) == session && session.getPlayers().isEmpty()) {
                    waitingSessions.remove(mode);
                }
            }
            return;
        }

        player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
    }

    private void forceStart(Player player, GameMode mode) {
        GameSession waitingSession = waitingSessions.get(mode);
        if (waitingSession != null && waitingSession.getState() == GameState.WAITING) {
            waitingSession.start();
            waitingSessions.remove(mode);
            player.sendMessage(Component.text("Force-started " + mode.getDisplayName() + " game.", NamedTextColor.GREEN));
            return;
        }

        Arena arena = plugin.getArenaManager().getAvailableArena();
        if (arena == null) {
            player.sendMessage(Component.text("No arenas available!", NamedTextColor.RED));
            return;
        }

        GameSession session = plugin.getArenaManager().createSession(arena, mode);
        plugin.getArenaManager().joinSession(player.getUniqueId(), session);
        session.start();
        player.sendMessage(Component.text("Force-started " + mode.getDisplayName() + " game.", NamedTextColor.GREEN));
    }

    private void showStatus(Player player) {
        List<GameSession> sessions = plugin.getArenaManager().getActiveSessions();
        if (sessions.isEmpty()) {
            player.sendMessage(Component.text("No active games.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("--- Active Games ---", NamedTextColor.GOLD));
        for (GameSession s : sessions) {
            player.sendMessage(Component.text(
                s.getGameMode().getDisplayName() + " [" + s.getState() + "] - " + s.getPlayers().size() + " players - Arena: " + s.getArena().getId(),
                NamedTextColor.GRAY));
        }
    }

    private GameMode parseMode(String input) {
        return switch (input.toLowerCase()) {
            case "solo" -> GameMode.SOLO;
            case "teams", "team" -> GameMode.TEAMS;
            case "prosolo", "pro_solo" -> GameMode.PRO_SOLO;
            case "proteams", "pro_teams" -> GameMode.PRO_TEAMS;
            default -> null;
        };
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("--- Build Battle ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/bb join <mode> - Join a game", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/bb leave - Leave current game", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/bb status - View active games", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Modes: solo, teams, prosolo, proteams", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return List.of("join", "leave", "start", "status").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("start"))) {
            return List.of("solo", "teams", "prosolo", "proteams").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}

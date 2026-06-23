package com.aspireserver.buildbattle.commands;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.admin.PlotManagementGui;
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
    private PlotManagementGui plotGui;

    public BuildBattleCommand(AspireBuildBattle plugin) {
        this.plugin = plugin;
        this.waitingSessions = new EnumMap<>(GameMode.class);
    }

    public void setPlotGui(PlotManagementGui plotGui) {
        this.plotGui = plotGui;
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
            case "removeplot" -> {
                if (!player.hasPermission("aspire.buildbattle.admin")) {
                    player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /bb removeplot <arenaId> <plotIndex>", NamedTextColor.RED));
                    return true;
                }
                handleRemovePlot(player, args[1], args[2]);
            }
            case "listplots" -> {
                if (!player.hasPermission("aspire.buildbattle.admin")) {
                    player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /bb listplots <arenaId>", NamedTextColor.RED));
                    return true;
                }
                handleListPlots(player, args[1]);
            }
            case "plots" -> {
                if (!player.hasPermission("aspire.buildbattle.admin")) {
                    player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /bb plots <arenaId>", NamedTextColor.RED));
                    return true;
                }
                handlePlotsGui(player, args[1]);
            }
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
            if (waitingSession.getPlayers().size() >= mode.getMaxPlayers()) {
                player.sendMessage(Component.text("Game is full! (" + mode.getMaxPlayers() + "/" + mode.getMaxPlayers() + ")", NamedTextColor.RED));
                return;
            }
            plugin.getArenaManager().joinSession(player.getUniqueId(), waitingSession);
            player.sendMessage(Component.text("Joined " + mode.getDisplayName() + "! (" + waitingSession.getPlayers().size() + "/" + mode.getMaxPlayers() + " players)", NamedTextColor.GREEN));
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

        player.sendMessage(Component.text("Joined " + mode.getDisplayName() + "! Lobby countdown started. (1/" + mode.getMaxPlayers() + " players)", NamedTextColor.GREEN));

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

    private void handleRemovePlot(Player player, String arenaId, String indexStr) {
        Arena arena = plugin.getArenaManager().getArena(arenaId);
        if (arena == null) {
            player.sendMessage(Component.text("Arena not found!", NamedTextColor.RED));
            return;
        }
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid plot index!", NamedTextColor.RED));
            return;
        }
        if (arena.removePlot(index)) {
            plugin.getArenaManager().saveArena(arena);
            player.sendMessage(Component.text("Plot " + index + " removed from arena '" + arenaId + "'. (" + arena.getPlots().size() + " remaining)", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Invalid plot index! Arena has " + arena.getPlots().size() + " plots (0-" + (arena.getPlots().size() - 1) + ")", NamedTextColor.RED));
        }
    }

    private void handleListPlots(Player player, String arenaId) {
        Arena arena = plugin.getArenaManager().getArena(arenaId);
        if (arena == null) {
            player.sendMessage(Component.text("Arena not found!", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("--- Plots for '" + arenaId + "' (" + arena.getPlots().size() + ") ---", NamedTextColor.GOLD));
        for (int i = 0; i < arena.getPlots().size(); i++) {
            var plot = arena.getPlots().get(i);
            player.sendMessage(Component.text("  [" + i + "] " + plot.getMinX() + "," + plot.getMinY() + "," + plot.getMinZ()
                + " -> " + plot.getMaxX() + "," + plot.getMaxY() + "," + plot.getMaxZ(), NamedTextColor.GRAY));
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

    private void handlePlotsGui(Player player, String arenaId) {
        Arena arena = plugin.getArenaManager().getArena(arenaId);
        if (arena == null) {
            player.sendMessage(Component.text("Arena not found!", NamedTextColor.RED));
            return;
        }
        if (plotGui != null) {
            plotGui.openGui(player, arena);
        } else {
            player.sendMessage(Component.text("Plot GUI not available.", NamedTextColor.RED));
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("--- Build Battle ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/bb join <mode> - Join a game", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/bb leave - Leave current game", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/bb status - View active games", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/bb plots <arena> - Manage plots (GUI)", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/bb listplots <arena> - List plots", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/bb removeplot <arena> <index> - Remove a plot", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Modes: solo, teams, prosolo, proteams", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return List.of("join", "leave", "start", "status", "plots", "removeplot", "listplots").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("start"))) {
            return List.of("solo", "teams", "prosolo", "proteams").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("removeplot") || args[0].equalsIgnoreCase("listplots") || args[0].equalsIgnoreCase("plots"))) {
            return plugin.getArenaManager().getArenas().stream()
                .map(a -> a.getId())
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}

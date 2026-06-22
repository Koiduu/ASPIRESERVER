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
    private final Map<GameMode, Queue<UUID>> queues;

    public BuildBattleCommand(AspireBuildBattle plugin) {
        this.plugin = plugin;
        this.queues = new EnumMap<>(GameMode.class);
        for (GameMode mode : GameMode.values()) {
            queues.put(mode, new LinkedList<>());
        }
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
            case "leave" -> leaveQueue(player);
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
            default -> sendUsage(player);
        }
        return true;
    }

    private void joinQueue(Player player, GameMode mode) {
        if (plugin.getArenaManager().getPlayerSession(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("You are already in a game!", NamedTextColor.RED));
            return;
        }

        for (Queue<UUID> queue : queues.values()) {
            if (queue.contains(player.getUniqueId())) {
                player.sendMessage(Component.text("You are already in a queue!", NamedTextColor.RED));
                return;
            }
        }

        Queue<UUID> queue = queues.get(mode);
        queue.add(player.getUniqueId());
        player.sendMessage(Component.text("Joined " + mode.getDisplayName() + " queue! (" + queue.size() + " in queue)", NamedTextColor.GREEN));

        int minPlayers = mode.isTeamMode() ? 4 : 2;
        if (queue.size() >= minPlayers) {
            startGame(mode);
        }
    }

    private void leaveQueue(Player player) {
        for (Queue<UUID> queue : queues.values()) {
            if (queue.remove(player.getUniqueId())) {
                player.sendMessage(Component.text("Left the queue.", NamedTextColor.YELLOW));
                return;
            }
        }

        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session != null) {
            plugin.getArenaManager().leaveSession(player.getUniqueId());
            player.teleport(session.getArena().getLobbySpawn());
            player.sendMessage(Component.text("Left the game.", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("You are not in a queue or game!", NamedTextColor.RED));
    }

    private void startGame(GameMode mode) {
        Arena arena = plugin.getArenaManager().getAvailableArena();
        if (arena == null) {
            Queue<UUID> queue = queues.get(mode);
            for (UUID uuid : queue) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendMessage(Component.text("No arenas available! Please wait.", NamedTextColor.RED));
                }
            }
            return;
        }

        Queue<UUID> queue = queues.get(mode);
        int maxPlayers = Math.min(arena.getMaxPlayers(), queue.size());
        if (mode.isTeamMode()) {
            maxPlayers = Math.min(maxPlayers * 2, queue.size());
        }

        GameSession session = plugin.getArenaManager().createSession(arena, mode);

        for (int i = 0; i < maxPlayers; i++) {
            UUID uuid = queue.poll();
            if (uuid == null) break;
            plugin.getArenaManager().joinSession(uuid, session);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.getState() == GameState.WAITING) {
                session.start();
            }
        }, 60L);
    }

    private void forceStart(Player player, GameMode mode) {
        Queue<UUID> queue = queues.get(mode);
        if (queue.isEmpty()) {
            queue.add(player.getUniqueId());
        }
        startGame(mode);
        player.sendMessage(Component.text("Force-started " + mode.getDisplayName() + " game.", NamedTextColor.GREEN));
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
        player.sendMessage(Component.text("/bb join <mode> - Join a queue", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/bb leave - Leave queue/game", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Modes: solo, teams, prosolo, proteams", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return List.of("join", "leave", "start").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("start"))) {
            return List.of("solo", "teams", "prosolo", "proteams").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}

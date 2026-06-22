package com.aspireserver.buildbattle.game;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.arena.Arena;
import com.aspireserver.buildbattle.plot.PlotRegion;
import com.aspireserver.buildbattle.voting.VoteManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

public class GameSession {

    private final AspireBuildBattle plugin;
    private final Arena arena;
    private final GameMode gameMode;
    private final Map<UUID, Integer> playerPlotAssignments;
    private final Map<UUID, UUID> teams;
    private final Set<UUID> players;
    private GameState state;
    private int timeRemaining;
    private BukkitTask timerTask;
    private VoteManager voteManager;
    private String theme;

    private static final List<String> THEMES = List.of(
        "Castle", "Underwater Temple", "Spaceship", "Medieval Village",
        "Treehouse", "Dragon's Lair", "Candy Land", "Pirate Ship",
        "Enchanted Forest", "Volcano", "Ice Palace", "Haunted House",
        "Sky Island", "Ancient Ruins", "Robot Factory", "Farm",
        "Aquarium", "Library", "Roller Coaster", "Mushroom Kingdom"
    );

    public GameSession(AspireBuildBattle plugin, Arena arena, GameMode gameMode) {
        this.plugin = plugin;
        this.arena = arena;
        this.gameMode = gameMode;
        this.playerPlotAssignments = new HashMap<>();
        this.teams = new HashMap<>();
        this.players = new LinkedHashSet<>();
        this.state = GameState.WAITING;
        this.timeRemaining = gameMode.getDurationSeconds();
    }

    public void addPlayer(UUID player) {
        players.add(player);
    }

    public void addTeam(UUID player1, UUID player2) {
        teams.put(player1, player2);
        teams.put(player2, player1);
    }

    public void removePlayer(UUID player) {
        players.remove(player);
        if (teams.containsKey(player)) {
            UUID partner = teams.remove(player);
            teams.remove(partner);
        }
    }

    public boolean hasPlayer(UUID player) {
        return players.contains(player);
    }

    public void start() {
        state = GameState.STARTING;
        theme = THEMES.get(new Random().nextInt(THEMES.size()));

        assignPlots();

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                int plotIndex = playerPlotAssignments.get(uuid);
                PlotRegion plot = arena.getPlots().get(plotIndex);
                player.teleport(plot.getCenter());
                player.sendMessage(Component.text("Theme: ", NamedTextColor.GOLD)
                    .append(Component.text(theme, NamedTextColor.YELLOW)));
                player.showTitle(Title.title(
                    Component.text("Build Battle!", NamedTextColor.GOLD),
                    Component.text("Theme: " + theme, NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
                ));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        }

        state = GameState.BUILDING;
        startTimer();
    }

    private void assignPlots() {
        List<PlotRegion> plots = arena.getPlots();
        int plotIndex = 0;

        if (gameMode.isTeamMode()) {
            Set<UUID> assigned = new HashSet<>();
            for (UUID player : players) {
                if (assigned.contains(player)) continue;
                UUID partner = teams.get(player);
                playerPlotAssignments.put(player, plotIndex);
                if (partner != null) {
                    playerPlotAssignments.put(partner, plotIndex);
                    assigned.add(partner);
                }
                assigned.add(player);
                plotIndex++;
                if (plotIndex >= plots.size()) break;
            }
        } else {
            for (UUID player : players) {
                playerPlotAssignments.put(player, plotIndex);
                plotIndex++;
                if (plotIndex >= plots.size()) break;
            }
        }
    }

    private void startTimer() {
        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (timeRemaining <= 0) {
                    cancel();
                    startVoting();
                    return;
                }

                if (timeRemaining <= 10 || timeRemaining == 30 || timeRemaining == 60) {
                    for (UUID uuid : players) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            player.sendActionBar(Component.text("Time remaining: " + timeRemaining + "s", NamedTextColor.YELLOW));
                            if (timeRemaining <= 5) {
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                            }
                        }
                    }
                }

                timeRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startVoting() {
        state = GameState.VOTING;
        voteManager = new VoteManager(this, plugin);
        voteManager.startVoting();
    }

    public void end(Map<UUID, Integer> scores) {
        state = GameState.ENDING;
        if (timerTask != null && !timerTask.isCancelled()) {
            timerTask.cancel();
        }

        UUID winner = null;
        int highestScore = -1;
        for (Map.Entry<UUID, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > highestScore) {
                highestScore = entry.getValue();
                winner = entry.getKey();
            }
        }

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (uuid.equals(winner)) {
                    player.showTitle(Title.title(
                        Component.text("WINNER!", NamedTextColor.GOLD),
                        Component.text("Score: " + highestScore, NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMillis(500))
                    ));
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                } else {
                    Player winnerPlayer = winner != null ? Bukkit.getPlayer(winner) : null;
                    String winnerName = winnerPlayer != null ? winnerPlayer.getName() : "Unknown";
                    player.showTitle(Title.title(
                        Component.text(winnerName + " wins!", NamedTextColor.GREEN),
                        Component.text("Score: " + highestScore, NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMillis(500))
                    ));
                }
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            clearAllPlots();
            resetSession();
        }, 100L);
    }

    private void clearAllPlots() {
        for (PlotRegion plot : arena.getPlots()) {
            plot.clear();
        }
    }

    private void resetSession() {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.teleport(arena.getLobbySpawn());
            }
        }
        players.clear();
        playerPlotAssignments.clear();
        teams.clear();
        state = GameState.WAITING;
        timeRemaining = gameMode.getDurationSeconds();
        plugin.getArenaManager().releaseArena(arena);
    }

    public PlotRegion getPlayerPlot(UUID player) {
        Integer plotIndex = playerPlotAssignments.get(player);
        if (plotIndex == null) return null;
        List<PlotRegion> plots = arena.getPlots();
        if (plotIndex >= plots.size()) return null;
        return plots.get(plotIndex);
    }

    public GameState getState() {
        return state;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public Set<UUID> getPlayers() {
        return players;
    }

    public Map<UUID, Integer> getPlayerPlotAssignments() {
        return playerPlotAssignments;
    }

    public Arena getArena() {
        return arena;
    }

    public String getTheme() {
        return theme;
    }

    public int getTimeRemaining() {
        return timeRemaining;
    }
}

package com.aspireserver.buildbattle.game;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.arena.Arena;
import com.aspireserver.buildbattle.plot.PlotRegion;
import com.aspireserver.buildbattle.voting.VoteManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

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
    private int lobbyCountdown;
    private BukkitTask timerTask;
    private BukkitTask lobbyTask;
    private VoteManager voteManager;
    private String theme;
    private final Map<UUID, Scoreboard> playerScoreboards;

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
        this.playerScoreboards = new HashMap<>();
        this.state = GameState.WAITING;
        this.timeRemaining = gameMode.getDurationSeconds();
        this.lobbyCountdown = plugin.getConfig().getInt("lobby-countdown", 60);
    }

    public void addPlayer(UUID player) {
        players.add(player);
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            Location waitingLobby = plugin.getArenaManager().getWaitingLobby();
            if (waitingLobby != null) {
                p.teleport(waitingLobby);
            }
        }
    }

    public void addTeam(UUID player1, UUID player2) {
        teams.put(player1, player2);
        teams.put(player2, player1);
    }

    public void removePlayer(UUID player) {
        players.remove(player);
        removeScoreboard(player);
        if (teams.containsKey(player)) {
            UUID partner = teams.remove(player);
            teams.remove(partner);
        }
        if (players.isEmpty() && state != GameState.ENDING) {
            forceEnd();
        }
    }

    public boolean hasPlayer(UUID player) {
        return players.contains(player);
    }

    public void startLobbyCountdown() {
        if (state != GameState.WAITING) return;

        lobbyTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (players.isEmpty()) {
                    cancel();
                    forceEnd();
                    return;
                }

                if (lobbyCountdown <= 0) {
                    cancel();
                    int minPlayers = gameMode.isTeamMode()
                        ? plugin.getConfig().getInt("min-players." + gameMode.name().toLowerCase().replace("_", "-"), 4)
                        : plugin.getConfig().getInt("min-players." + gameMode.name().toLowerCase().replace("_", "-"), 2);
                    if (players.size() >= minPlayers) {
                        start();
                    } else {
                        for (UUID uuid : new HashSet<>(players)) {
                            Player p = Bukkit.getPlayer(uuid);
                            if (p != null) {
                                p.sendMessage(Component.text("Not enough players! Game cancelled.", NamedTextColor.RED));
                                p.teleport(arena.getLobbySpawn());
                            }
                        }
                        forceEnd();
                    }
                    return;
                }

                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.sendActionBar(Component.text("Game starts in " + lobbyCountdown + "s | " + players.size() + " players", NamedTextColor.YELLOW));
                        if (lobbyCountdown <= 5) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                        }
                    }
                }

                lobbyCountdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void start() {
        state = GameState.STARTING;
        if (lobbyTask != null && !lobbyTask.isCancelled()) {
            lobbyTask.cancel();
        }

        theme = THEMES.get(new Random().nextInt(THEMES.size()));
        assignPlots();

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Integer plotIndex = playerPlotAssignments.get(uuid);
                if (plotIndex == null) continue;
                PlotRegion plot = arena.getPlots().get(plotIndex);
                Location spawnLoc = plot.getSafeCenter();
                if (spawnLoc != null) {
                    player.teleport(spawnLoc);
                }
                player.sendMessage(Component.text("Theme: ", NamedTextColor.GOLD)
                    .append(Component.text(theme, NamedTextColor.YELLOW)));
                player.showTitle(Title.title(
                    Component.text("Build Battle!", NamedTextColor.GOLD),
                    Component.text("Theme: " + theme, NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
                ));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                setupScoreboard(player);
            }
        }

        state = GameState.BUILDING;
        giveNetherStar();
        startTimer();
    }

    private void giveNetherStar() {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                ItemStack star = new ItemStack(Material.NETHER_STAR);
                ItemMeta meta = star.getItemMeta();
                meta.displayName(Component.text("Plot Customizer", NamedTextColor.LIGHT_PURPLE));
                meta.lore(List.of(
                    Component.text("Right-click to change your", NamedTextColor.GRAY),
                    Component.text("plot floor material", NamedTextColor.GRAY)
                ));
                star.setItemMeta(meta);
                player.getInventory().setItem(8, star);
            }
        }
    }

    private void assignPlots() {
        List<PlotRegion> plots = arena.getPlots();
        if (plots.isEmpty()) return;
        int plotIndex = 0;

        if (gameMode.isTeamMode()) {
            Set<UUID> assigned = new HashSet<>();
            for (UUID player : players) {
                if (assigned.contains(player)) continue;
                if (plotIndex >= plots.size()) break;
                UUID partner = teams.get(player);
                playerPlotAssignments.put(player, plotIndex);
                if (partner != null) {
                    playerPlotAssignments.put(partner, plotIndex);
                    assigned.add(partner);
                }
                assigned.add(player);
                plotIndex++;
            }
        } else {
            for (UUID player : players) {
                if (plotIndex >= plots.size()) break;
                playerPlotAssignments.put(player, plotIndex);
                plotIndex++;
            }
        }
    }

    private void setupScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective obj = board.registerNewObjective("bb_game", Criteria.DUMMY,
            Component.text("BUILD BATTLE", NamedTextColor.GOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        obj.getScore("").setScore(6);
        obj.getScore("§eTheme:").setScore(5);
        obj.getScore("§f" + theme).setScore(4);
        obj.getScore(" ").setScore(3);
        obj.getScore("§eTime Left:").setScore(2);
        obj.getScore("§f" + formatTime(timeRemaining)).setScore(1);

        player.setScoreboard(board);
        playerScoreboards.put(player.getUniqueId(), board);
    }

    private void updateScoreboards() {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            Scoreboard board = playerScoreboards.get(uuid);
            if (board == null) continue;

            board.getEntries().forEach(board::resetScores);

            Objective obj = board.getObjective("bb_game");
            if (obj == null) continue;

            obj.getScore("").setScore(6);
            obj.getScore("§eTheme:").setScore(5);
            obj.getScore("§f" + theme).setScore(4);
            obj.getScore(" ").setScore(3);
            obj.getScore("§eTime Left:").setScore(2);
            obj.getScore("§f" + formatTime(timeRemaining)).setScore(1);
        }
    }

    private void removeScoreboard(UUID uuid) {
        playerScoreboards.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void removeAllScoreboards() {
        for (UUID uuid : new HashSet<>(playerScoreboards.keySet())) {
            removeScoreboard(uuid);
        }
    }

    private String formatTime(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format("%d:%02d", min, sec);
    }

    private void startTimer() {
        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (players.isEmpty()) {
                    cancel();
                    forceEnd();
                    return;
                }

                if (timeRemaining <= 0) {
                    cancel();
                    removeAllScoreboards();
                    startVoting();
                    return;
                }

                updateScoreboards();

                if (timeRemaining <= 10) {
                    for (UUID uuid : players) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
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
        cancelTasks();
        removeAllScoreboards();

        // Clear all player inventories
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.getInventory().clear();
            }
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

    public void forceEnd() {
        state = GameState.ENDING;
        cancelTasks();
        removeAllScoreboards();

        for (UUID uuid : new HashSet<>(players)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(Component.text("Game ended — all players left.", NamedTextColor.RED));
                player.teleport(arena.getLobbySpawn());
            }
        }

        clearAllPlots();
        players.clear();
        playerPlotAssignments.clear();
        teams.clear();
        state = GameState.ENDING;
        plugin.getArenaManager().releaseArena(arena);
    }

    private void cancelTasks() {
        if (timerTask != null && !timerTask.isCancelled()) {
            timerTask.cancel();
        }
        if (lobbyTask != null && !lobbyTask.isCancelled()) {
            lobbyTask.cancel();
        }
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

    public GameState getState() { return state; }
    public GameMode getGameMode() { return gameMode; }
    public Set<UUID> getPlayers() { return players; }
    public Map<UUID, Integer> getPlayerPlotAssignments() { return playerPlotAssignments; }
    public Arena getArena() { return arena; }
    public String getTheme() { return theme; }
    public int getTimeRemaining() { return timeRemaining; }
    public int getLobbyCountdown() { return lobbyCountdown; }
    public VoteManager getVoteManager() { return voteManager; }
}

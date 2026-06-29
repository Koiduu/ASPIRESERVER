package com.aspireserver.buildbattle.game;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.arena.Arena;
import com.aspireserver.buildbattle.plot.PlotRegion;
import com.aspireserver.buildbattle.pro.ProToolManager;
import com.aspireserver.buildbattle.voting.VoteManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
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
    private BukkitTask themeVoteTask;
    private VoteManager voteManager;
    private String theme;
    private final Map<UUID, Scoreboard> playerScoreboards;
    private final Map<Integer, Material> originalFloors;

    // Theme voting
    private final Map<String, Integer> themeVotes = new HashMap<>();
    private List<String> themeOptions;
    private int themeVoteCountdown = 10;

    private static final List<String> ALL_THEMES = List.of(
        "Castle", "Underwater Temple", "Spaceship", "Medieval Village",
        "Treehouse", "Dragon's Lair", "Candy Land", "Pirate Ship",
        "Enchanted Forest", "Volcano", "Ice Palace", "Haunted House",
        "Sky Island", "Ancient Ruins", "Robot Factory", "Farm",
        "Aquarium", "Library", "Roller Coaster", "Mushroom Kingdom",
        "Garden", "Beach", "Mountain", "Desert", "Forest",
        "Lake", "City", "Park", "Cemetery", "Temple",
        "Bridge", "Windmill", "Lighthouse", "Cottage", "Tower",
        "Ship", "Train", "Campfire", "Waterfall", "Cave"
    );

    public static final String THEME_VOTE_TITLE = "Vote for Theme";

    public GameSession(AspireBuildBattle plugin, Arena arena, GameMode gameMode) {
        this.plugin = plugin;
        this.arena = arena;
        this.gameMode = gameMode;
        this.playerPlotAssignments = new HashMap<>();
        this.teams = new HashMap<>();
        this.players = new LinkedHashSet<>();
        this.playerScoreboards = new HashMap<>();
        this.originalFloors = new HashMap<>();
        this.state = GameState.WAITING;
        this.timeRemaining = gameMode.getDurationSeconds();
        this.lobbyCountdown = plugin.getConfig().getInt("lobby-countdown", 60);
    }

    public void addPlayer(UUID player) {
        players.add(player);
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            String plotType = arena.getPlotType();
            Location waitingLobby = plugin.getArenaManager().getWaitingLobby(plotType);
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
                        startThemeVoting();
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
                        p.sendActionBar(Component.text("Game starts in " + lobbyCountdown + "s | " + players.size() + "/" + gameMode.getMaxPlayers() + " players", NamedTextColor.YELLOW));
                        if (lobbyCountdown <= 5) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                        }
                    }
                }

                lobbyCountdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startThemeVoting() {
        state = GameState.STARTING;
        if (lobbyTask != null && !lobbyTask.isCancelled()) {
            lobbyTask.cancel();
        }

        List<String> shuffled = new ArrayList<>(ALL_THEMES);
        Collections.shuffle(shuffled);
        themeOptions = shuffled.subList(0, Math.min(3, shuffled.size()));
        for (String t : themeOptions) {
            themeVotes.put(t, 0);
        }

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(Component.text("Vote for this round's theme!", NamedTextColor.GOLD));
                openThemeVoteGui(player);
            }
        }

        themeVoteCountdown = 10;
        themeVoteTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (themeVoteCountdown <= 0) {
                    cancel();
                    finalizeThemeVote();
                    return;
                }
                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.sendActionBar(Component.text("Theme voting ends in " + themeVoteCountdown + "s", NamedTextColor.GOLD));
                    }
                }
                themeVoteCountdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void openThemeVoteGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9,
            Component.text(THEME_VOTE_TITLE, NamedTextColor.GOLD));

        for (int i = 0; i < themeOptions.size(); i++) {
            String themeName = themeOptions.get(i);
            ItemStack item = new ItemStack(getThemeIcon(i));
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(themeName, NamedTextColor.YELLOW, TextDecoration.BOLD));
            meta.lore(List.of(Component.text("Click to vote!", NamedTextColor.GRAY)));
            item.setItemMeta(meta);
            gui.setItem(2 + (i * 2), item);
        }

        player.openInventory(gui);
    }

    private Material getThemeIcon(int index) {
        return switch (index) {
            case 0 -> Material.DIAMOND;
            case 1 -> Material.EMERALD;
            case 2 -> Material.AMETHYST_SHARD;
            default -> Material.PAPER;
        };
    }

    public void registerThemeVote(UUID player, int slot) {
        int themeIndex = (slot - 2) / 2;
        if (themeIndex < 0 || themeIndex >= themeOptions.size()) return;
        String chosen = themeOptions.get(themeIndex);
        themeVotes.merge(chosen, 1, Integer::sum);
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            p.closeInventory();
            p.sendMessage(Component.text("Voted for: " + chosen, NamedTextColor.GREEN));
        }
    }

    private void finalizeThemeVote() {
        String winningTheme = themeOptions.get(0);
        int maxVotes = 0;
        for (Map.Entry<String, Integer> entry : themeVotes.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                winningTheme = entry.getKey();
            }
        }
        theme = winningTheme;
        startGame();
    }

    private void startGame() {
        assignPlots();
        saveOriginalFloors();

        plugin.getLogger().info("[BB] Starting game in arena " + arena.getId()
            + " with " + players.size() + " players, " + playerPlotAssignments.size() + " assignments");

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Integer plotIndex = playerPlotAssignments.get(uuid);
                if (plotIndex == null) {
                    plugin.getLogger().warning("[BB] Player " + player.getName() + " has no plot assignment!");
                    continue;
                }
                if (plotIndex >= arena.getPlots().size()) {
                    plugin.getLogger().warning("[BB] Player " + player.getName() + " assigned to invalid plot " + plotIndex);
                    continue;
                }
                PlotRegion plot = arena.getPlots().get(plotIndex);
                Location spawnLoc = plot.getSafeCenter();
                if (spawnLoc != null) {
                    player.teleport(spawnLoc);
                    plugin.getLogger().info("[BB] Teleported " + player.getName() + " to plot " + plotIndex
                        + " at " + spawnLoc.getBlockX() + "," + spawnLoc.getBlockY() + "," + spawnLoc.getBlockZ());
                } else {
                    plugin.getLogger().warning("[BB] No safe center for plot " + plotIndex + " — player " + player.getName());
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

    public void start() {
        state = GameState.STARTING;
        if (lobbyTask != null && !lobbyTask.isCancelled()) {
            lobbyTask.cancel();
        }

        theme = ALL_THEMES.get(new Random().nextInt(ALL_THEMES.size()));
        startGame();
    }

    private void saveOriginalFloors() {
        for (int i = 0; i < arena.getPlots().size(); i++) {
            PlotRegion plot = arena.getPlots().get(i);
            World world = Bukkit.getWorld(plot.getWorldName());
            if (world == null) continue;
            int cx = (plot.getMinX() + plot.getMaxX()) / 2;
            int cz = (plot.getMinZ() + plot.getMaxZ()) / 2;
            Block block = world.getBlockAt(cx, plot.getMinY(), cz);
            originalFloors.put(i, block.getType());
        }
    }

    private void resetFloors() {
        for (int i = 0; i < arena.getPlots().size(); i++) {
            PlotRegion plot = arena.getPlots().get(i);
            Material original = originalFloors.getOrDefault(i, Material.GRASS_BLOCK);
            World world = Bukkit.getWorld(plot.getWorldName());
            if (world == null) continue;
            Material floorMat = original;
            for (int x = plot.getMinX() + 1; x < plot.getMaxX(); x++) {
                for (int z = plot.getMinZ() + 1; z < plot.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, plot.getMinY(), z);
                    block.setType(floorMat, false);
                }
            }
        }
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

                if (gameMode.isWorldEditEnabled()) {
                    player.getInventory().setItem(7, ProToolManager.createProToolItem());
                }
            }
        }
    }

    private void assignPlots() {
        List<PlotRegion> plots = arena.getPlots();
        if (plots.isEmpty()) {
            plugin.getLogger().warning("[BB] Arena " + arena.getId() + " has no plots!");
            return;
        }

        int plotIndex = 0;

        if (gameMode.isTeamMode()) {
            Set<UUID> assigned = new HashSet<>();
            for (UUID player : players) {
                if (assigned.contains(player)) continue;
                if (plotIndex >= plots.size()) {
                    plugin.getLogger().warning("[BB] Ran out of plots at index " + plotIndex + " (have " + plots.size() + ")");
                    break;
                }
                UUID partner = teams.get(player);
                playerPlotAssignments.put(player, plotIndex);
                assigned.add(player);
                if (partner != null && players.contains(partner)) {
                    playerPlotAssignments.put(partner, plotIndex);
                    assigned.add(partner);
                }
                plotIndex++;
            }
        } else {
            for (UUID player : players) {
                if (plotIndex >= plots.size()) {
                    plugin.getLogger().warning("[BB] Ran out of plots at index " + plotIndex + " (have " + plots.size() + ")");
                    break;
                }
                playerPlotAssignments.put(player, plotIndex);
                plugin.getLogger().info("[BB] Assigned player to plot " + plotIndex);
                plotIndex++;
            }
        }

        plugin.getLogger().info("[BB] Assigned " + playerPlotAssignments.size() + " players to " + plotIndex + " plots");
    }

    private void setupScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective obj = board.registerNewObjective("bb_game", Criteria.DUMMY,
            Component.text("BUILD BATTLE", NamedTextColor.GOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        obj.getScore("").setScore(6);
        obj.getScore("\u00a7eTheme:").setScore(5);
        obj.getScore("\u00a7f" + theme).setScore(4);
        obj.getScore(" ").setScore(3);
        obj.getScore("\u00a7eTime Left:").setScore(2);
        obj.getScore("\u00a7f" + formatTime(timeRemaining)).setScore(1);

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
            obj.getScore("\u00a7eTheme:").setScore(5);
            obj.getScore("\u00a7f" + theme).setScore(4);
            obj.getScore(" ").setScore(3);
            obj.getScore("\u00a7eTime Left:").setScore(2);
            obj.getScore("\u00a7f" + formatTime(timeRemaining)).setScore(1);
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

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.getInventory().clear();
            }
        }

        int highestScore = -1;
        for (Map.Entry<UUID, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > highestScore) {
                highestScore = entry.getValue();
            }
        }

        List<UUID> winners = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : scores.entrySet()) {
            if (entry.getValue() == highestScore) {
                winners.add(entry.getKey());
            }
        }

        boolean isTie = winners.size() > 1;

        // Warp all players to the first winner's plot for viewing
        if (!winners.isEmpty()) {
            Integer winnerPlotIdx = playerPlotAssignments.get(winners.get(0));
            if (winnerPlotIdx != null && winnerPlotIdx < arena.getPlots().size()) {
                Location plotCenter = arena.getPlots().get(winnerPlotIdx).getCenter();
                for (UUID uuid : players) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        player.teleport(plotCenter);
                    }
                }
            }
        }

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (isTie) {
                    StringBuilder tiedNames = new StringBuilder();
                    for (int i = 0; i < winners.size(); i++) {
                        Player wp = Bukkit.getPlayer(winners.get(i));
                        if (wp != null) {
                            if (tiedNames.length() > 0) tiedNames.append(", ");
                            tiedNames.append(wp.getName());
                        }
                    }
                    if (winners.contains(uuid)) {
                        player.showTitle(Title.title(
                            Component.text("TIE!", NamedTextColor.GOLD),
                            Component.text("Score: " + highestScore + " | " + tiedNames, NamedTextColor.YELLOW),
                            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMillis(500))
                        ));
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    } else {
                        player.showTitle(Title.title(
                            Component.text("It's a TIE!", NamedTextColor.GREEN),
                            Component.text(tiedNames + " | Score: " + highestScore, NamedTextColor.GRAY),
                            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMillis(500))
                        ));
                    }
                } else if (!winners.isEmpty()) {
                    UUID winner = winners.get(0);
                    if (uuid.equals(winner)) {
                        player.showTitle(Title.title(
                            Component.text("WINNER!", NamedTextColor.GOLD),
                            Component.text("Score: " + highestScore, NamedTextColor.YELLOW),
                            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMillis(500))
                        ));
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    } else {
                        Player winnerPlayer = Bukkit.getPlayer(winner);
                        String winnerName = winnerPlayer != null ? winnerPlayer.getName() : "Unknown";
                        player.showTitle(Title.title(
                            Component.text(winnerName + " wins!", NamedTextColor.GREEN),
                            Component.text("Score: " + highestScore, NamedTextColor.GRAY),
                            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMillis(500))
                        ));
                    }
                }
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            clearAllPlots();
            resetFloors();
            warpToWaitingLobby();
            resetSession();
        }, 100L);
    }

    private void warpToWaitingLobby() {
        String plotType = arena.getPlotType();
        Location waitingLobby = plugin.getArenaManager().getWaitingLobby(plotType);
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (waitingLobby != null) {
                    player.teleport(waitingLobby);
                } else {
                    player.teleport(arena.getLobbySpawn());
                }
            }
        }
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
        resetFloors();
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
        if (themeVoteTask != null && !themeVoteTask.isCancelled()) {
            themeVoteTask.cancel();
        }
    }

    private void clearAllPlots() {
        for (PlotRegion plot : arena.getPlots()) {
            plot.clear();
        }
    }

    private void resetSession() {
        // Remove all players from ArenaManager's session tracking
        for (UUID uuid : players) {
            plugin.getArenaManager().removePlayerTracking(uuid);
        }
        players.clear();
        playerPlotAssignments.clear();
        teams.clear();
        originalFloors.clear();
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

    public List<String> getThemeOptions() { return themeOptions; }
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

package com.aspireserver.bedwars.ui;

import com.aspireserver.bedwars.AspireBedwars;
import com.aspireserver.bedwars.game.PlayerData;
import com.aspireserver.bedwars.team.BedwarsTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class ScoreboardUI {

    private final AspireBedwars plugin;
    private BukkitTask task;
    // Per-player scoreboard, reused across ticks so we never re-assign a fresh board
    // (re-assigning interrupts client item use, e.g. shield blocking).
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public ScoreboardUI(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getGameManager().isInBedwarsWorld(p)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }

    private void updateAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getGameManager().isInBedwarsWorld(p)) render(p);
        }
    }

    private void render(Player player) {
        UUID pid = player.getUniqueId();
        Scoreboard board = boards.get(pid);
        boolean assign = false;
        if (board == null || player.getScoreboard() != board) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            boards.put(pid, board);
            assign = true;
        }
        Objective obj = board.getObjective("bw");
        if (obj == null) {
            obj = board.registerNewObjective("bw", Criteria.DUMMY,
                    Component.text("BED WARS", NamedTextColor.YELLOW, TextDecoration.BOLD));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        // Clear previous lines in place (keeps the same board object).
        for (String entry : new HashSet<>(board.getEntries())) board.resetScores(entry);
        for (Team t : new HashSet<>(board.getTeams())) t.unregister();

        int line = 15;
        setLine(board, obj, line--, Component.text(timeString(), NamedTextColor.GRAY));
        setBlank(board, obj, line--);

        for (BedwarsTeam team : plugin.getTeamManager().getTeams()) {
            Component status;
            if (team.isBedAlive()) {
                status = Component.text("\u2714", NamedTextColor.GREEN);
            } else {
                int alive = team.aliveCount(uuid -> Bukkit.getPlayer(uuid) != null);
                status = alive > 0 ? Component.text(String.valueOf(alive), NamedTextColor.RED)
                        : Component.text("\u2718", NamedTextColor.DARK_GRAY);
            }
            Component you = team.isMember(player.getUniqueId()) ? Component.text(" YOU", NamedTextColor.GRAY) : Component.empty();
            Component row = Component.text(team.getColor().displayName().charAt(0) + " ", team.getColor().textColor())
                    .append(Component.text(team.getColor().displayName() + " ", NamedTextColor.WHITE))
                    .append(status).append(you);
            setLine(board, obj, line--, row);
            if (line < 2) break;
        }

        setBlank(board, obj, line--);
        PlayerData data = plugin.getGameManager().getPlayerData(player.getUniqueId());
        int kills = data != null ? data.kills : 0;
        int fk = data != null ? data.finalKills : 0;
        setLine(board, obj, line--, Component.text("Kills: ", NamedTextColor.GRAY)
                .append(Component.text(kills, NamedTextColor.WHITE)));
        setLine(board, obj, line--, Component.text("Final Kills: ", NamedTextColor.GRAY)
                .append(Component.text(fk, NamedTextColor.WHITE)));
        setLine(board, obj, line--, Component.text("aspire.server", NamedTextColor.YELLOW));

        if (assign) player.setScoreboard(board);
    }

    private String timeString() {
        long elapsed = (System.currentTimeMillis() - plugin.getGameManager().getMatchStartMillis()) / 1000;
        int phaseMin = plugin.getSetupConfig().getDiamondTier2Min();
        long m = elapsed / 60;
        long s = elapsed % 60;
        String phase = switch (plugin.getGameManager().getState()) {
            case SUDDEN_DEATH -> "Sudden Death";
            default -> "Diamond II in " + Math.max(0, phaseMin) + "m";
        };
        return String.format("%02d:%02d  %s", m, s, phase);
    }

    private void setLine(Scoreboard board, Objective obj, int score, Component text) {
        String entry = invisibleEntry(score);
        Team team = board.registerNewTeam("l" + score);
        team.addEntry(entry);
        team.prefix(text);
        obj.getScore(entry).setScore(score);
    }

    private void setBlank(Scoreboard board, Objective obj, int score) {
        obj.getScore(invisibleEntry(score)).setScore(score);
    }

    private String invisibleEntry(int score) {
        String hex = Integer.toHexString(score & 0xF);
        return "\u00A7" + hex + "\u00A7r";
    }
}

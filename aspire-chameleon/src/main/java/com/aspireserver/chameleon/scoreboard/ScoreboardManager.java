package com.aspireserver.chameleon.scoreboard;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.bukkit.scoreboard.Team;

public class ScoreboardManager {

    private final MecchaChameleon plugin;

    public ScoreboardManager(MecchaChameleon plugin) {
        this.plugin = plugin;
    }

    public void startScoreboard(GameManager gm) {
        updateScoreboard(gm);
    }

    public void updateScoreboard(GameManager gm) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!gm.isParticipant(player.getUniqueId())) continue;
            setScoreboard(player, gm);
        }
    }

    private void setScoreboard(Player player, GameManager gm) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("chameleon", Criteria.DUMMY,
                Component.text()
                        .append(Component.text("« ", NamedTextColor.DARK_GREEN))
                        .append(Component.text("CHAMELEON", NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.text(" »", NamedTextColor.DARK_GREEN))
                        .build());
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Nametag hiding team
        Team hideTeam = board.registerNewTeam("cham_hidden");
        hideTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (gm.isParticipant(p.getUniqueId())) {
                hideTeam.addPlayer(p);
            }
        }

        int line = 10;

        // --- MAP section ---
        String mapName = gm.getCurrentMapId() != null ? gm.getCurrentMapId() : "???";
        setLine(board, obj, line--, Component.text()
                .append(Component.text("Map ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(mapName, NamedTextColor.WHITE))
                .build());

        setBlank(board, obj, line--);

        // --- STATUS section ---
        NamedTextColor phaseColor;
        String phase;
        switch (gm.getState()) {
            case HIDING -> { phase = "Hiding"; phaseColor = NamedTextColor.AQUA; }
            case HUNTING -> { phase = "Hunting"; phaseColor = NamedTextColor.RED; }
            case REVEAL -> { phase = "Reveal"; phaseColor = NamedTextColor.LIGHT_PURPLE; }
            default -> { phase = "???"; phaseColor = NamedTextColor.GRAY; }
        }
        setLine(board, obj, line--, Component.text()
                .append(Component.text("Phase: ", NamedTextColor.GRAY))
                .append(Component.text(phase, phaseColor, TextDecoration.BOLD))
                .build());

        int time = gm.getTimeRemaining();
        String timeStr = String.format("%02d:%02d", time / 60, time % 60);
        NamedTextColor timeColor = time <= 30 ? NamedTextColor.RED : NamedTextColor.YELLOW;
        setLine(board, obj, line--, Component.text()
                .append(Component.text("Time: ", NamedTextColor.GRAY))
                .append(Component.text(timeStr, timeColor))
                .build());

        setBlank(board, obj, line--);

        // --- TEAMS section ---
        setLine(board, obj, line--, Component.text()
                .append(Component.text("\u25B6 Hiders: ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(String.valueOf(gm.getHiderCount()), NamedTextColor.WHITE))
                .build());

        setLine(board, obj, line--, Component.text()
                .append(Component.text("\u2620 Hunters: ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(String.valueOf(gm.getSeekerCount()), NamedTextColor.WHITE))
                .build());

        setBlank(board, obj, line--);

        // Footer
        setLine(board, obj, line--, Component.text("aspire.server", NamedTextColor.DARK_GREEN, TextDecoration.ITALIC));

        player.setScoreboard(board);
    }

    // Uses a per-line team so the visible text can be a fully colored Component.
    // Each entry is a unique invisible string built from color codes.
    private void setLine(Scoreboard board, Objective obj, int score, Component text) {
        String entry = invisibleEntry(score);
        Team team = board.registerNewTeam("line_" + score);
        team.addEntry(entry);
        team.prefix(text);
        obj.getScore(entry).setScore(score);
    }

    private void setBlank(Scoreboard board, Objective obj, int score) {
        String entry = invisibleEntry(score);
        obj.getScore(entry).setScore(score);
    }

    private String invisibleEntry(int score) {
        // Unique, invisible per-line identifier using color codes
        String hex = Integer.toHexString(score & 0xF);
        return "\u00A7" + hex + "\u00A7r";
    }

    public void removeScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void clearAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            removeScoreboard(p);
        }
    }
}

package com.aspireserver.chameleon.scoreboard;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.game.GameManager;
import com.aspireserver.chameleon.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

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
                Component.text("CHAMELEON", NamedTextColor.GREEN, TextDecoration.BOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Add nametag hiding team to this custom scoreboard
        Team hideTeam = board.registerNewTeam("cham_hidden");
        hideTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (gm.isParticipant(p.getUniqueId())) {
                hideTeam.addPlayer(p);
            }
        }

        int line = 8;

        // Map
        String mapName = gm.getCurrentMapId() != null ? gm.getCurrentMapId() : "???";
        obj.getScore(toEntry("Map: " + mapName)).setScore(line--);

        // Blank
        obj.getScore(toEntry(" ")).setScore(line--);

        // Phase
        String phase = switch (gm.getState()) {
            case HIDING -> "HIDING";
            case HUNTING -> "HUNTING";
            case REVEAL -> "REVEAL";
            default -> "???";
        };
        obj.getScore(toEntry("Phase: " + phase)).setScore(line--);

        // Time
        int time = gm.getTimeRemaining();
        String timeStr = String.format("%02d:%02d", time / 60, time % 60);
        obj.getScore(toEntry("Time: " + timeStr)).setScore(line--);

        // Blank
        obj.getScore(toEntry("  ")).setScore(line--);

        // Hiders alive
        obj.getScore(toEntry("Hiders: " + gm.getHiderCount())).setScore(line--);

        // Hunters
        obj.getScore(toEntry("Hunters: " + gm.getSeekerCount())).setScore(line--);

        // Blank
        obj.getScore(toEntry("   ")).setScore(line--);

        player.setScoreboard(board);
    }

    private String toEntry(String text) {
        // Ensure uniqueness by padding with invisible chars if needed
        return text;
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

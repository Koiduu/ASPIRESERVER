package com.aspireserver.buildbattle.voting;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.game.GameSession;
import com.aspireserver.buildbattle.plot.PlotRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class VoteManager {

    private final GameSession session;
    private final AspireBuildBattle plugin;
    private final Map<UUID, Map<Integer, VoteRating>> votes;
    private final Set<UUID> reportedPlots;
    private int currentPlotIndex;
    private final int totalPlots;

    public static final String VOTE_GUI_TITLE = "Rate this Build!";
    public static final String REPORT_CONFIRM_TITLE = "Confirm Report?";

    public VoteManager(GameSession session, AspireBuildBattle plugin) {
        this.session = session;
        this.plugin = plugin;
        this.votes = new HashMap<>();
        this.reportedPlots = new HashSet<>();
        this.currentPlotIndex = 0;

        Set<Integer> uniquePlots = new HashSet<>(session.getPlayerPlotAssignments().values());
        this.totalPlots = uniquePlots.size();

        for (UUID player : session.getPlayers()) {
            votes.put(player, new HashMap<>());
        }
    }

    public void startVoting() {
        for (UUID uuid : session.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(Component.text("Building time is over! Voting begins now!", NamedTextColor.GOLD));
            }
        }
        showNextPlot();
    }

    private void showNextPlot() {
        if (currentPlotIndex >= totalPlots) {
            calculateResults();
            return;
        }

        List<PlotRegion> plots = session.getArena().getPlots();
        if (currentPlotIndex >= plots.size()) {
            calculateResults();
            return;
        }

        PlotRegion plot = plots.get(currentPlotIndex);

        for (UUID uuid : session.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.teleport(plot.getCenter());
                player.sendMessage(Component.text("Plot " + (currentPlotIndex + 1) + "/" + totalPlots, NamedTextColor.AQUA));

                Integer playerPlot = session.getPlayerPlotAssignments().get(uuid);
                if (playerPlot == null || playerPlot != currentPlotIndex) {
                    openVoteGui(player);
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                currentPlotIndex++;
                showNextPlot();
            }
        }.runTaskLater(plugin, 15 * 20L);
    }

    public void openVoteGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 18, Component.text(VOTE_GUI_TITLE, NamedTextColor.GOLD));

        // Row 1: F D E C B A S (slots 1-7)
        gui.setItem(1, createVoteItem(Material.TERRACOTTA, "F", NamedTextColor.DARK_RED, 1));
        gui.setItem(2, createVoteItem(Material.RED_TERRACOTTA, "D", NamedTextColor.RED, 2));
        gui.setItem(3, createVoteItem(Material.ORANGE_TERRACOTTA, "E", NamedTextColor.GOLD, 3));
        gui.setItem(4, createVoteItem(Material.YELLOW_TERRACOTTA, "C", NamedTextColor.YELLOW, 4));
        gui.setItem(5, createVoteItem(Material.LIME_TERRACOTTA, "B", NamedTextColor.GREEN, 5));
        gui.setItem(6, createVoteItem(Material.GREEN_TERRACOTTA, "A", NamedTextColor.DARK_GREEN, 6));
        gui.setItem(7, createVoteItem(Material.DIAMOND_BLOCK, "S", NamedTextColor.AQUA, 7));

        // Row 2, slot 13: Report button (barrier)
        ItemStack reportItem = new ItemStack(Material.BARRIER);
        ItemMeta reportMeta = reportItem.getItemMeta();
        reportMeta.displayName(Component.text("Report Build", NamedTextColor.RED, TextDecoration.BOLD));
        reportMeta.lore(List.of(
            Component.text("Click to report this build", NamedTextColor.GRAY),
            Component.text("for inappropriate content", NamedTextColor.GRAY)
        ));
        reportItem.setItemMeta(reportMeta);
        gui.setItem(13, reportItem);

        player.openInventory(gui);
    }

    public void openReportConfirmGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27,
            Component.text(REPORT_CONFIRM_TITLE, NamedTextColor.RED));

        ItemStack confirm = new ItemStack(Material.RED_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.displayName(Component.text("CONFIRM REPORT", NamedTextColor.RED, TextDecoration.BOLD));
        confirmMeta.lore(List.of(
            Component.text("Are you sure you want to", NamedTextColor.GRAY),
            Component.text("report this build?", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("False reports may result", NamedTextColor.DARK_RED),
            Component.text("in punishment.", NamedTextColor.DARK_RED)
        ));
        confirm.setItemMeta(confirmMeta);
        gui.setItem(11, confirm);

        ItemStack cancel = new ItemStack(Material.GREEN_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(Component.text("CANCEL", NamedTextColor.GREEN, TextDecoration.BOLD));
        cancelMeta.lore(List.of(Component.text("Go back to voting", NamedTextColor.GRAY)));
        cancel.setItemMeta(cancelMeta);
        gui.setItem(15, cancel);

        player.openInventory(gui);
    }

    private ItemStack createVoteItem(Material material, String grade, NamedTextColor color, int score) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(grade, color, TextDecoration.BOLD));
        meta.lore(List.of(Component.text(score + " points", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    public void registerVote(UUID player, int score) {
        Map<Integer, VoteRating> playerVotes = votes.get(player);
        if (playerVotes != null) {
            playerVotes.put(currentPlotIndex, VoteRating.fromScore(score));
        }
    }

    public void registerReport(UUID reporter) {
        reportedPlots.add(reporter);
        UUID plotOwner = null;
        for (Map.Entry<UUID, Integer> entry : session.getPlayerPlotAssignments().entrySet()) {
            if (entry.getValue() == currentPlotIndex) {
                plotOwner = entry.getKey();
                break;
            }
        }
        Player reporterPlayer = Bukkit.getPlayer(reporter);
        if (reporterPlayer != null) {
            reporterPlayer.sendMessage(Component.text("Report submitted. Staff will review.", NamedTextColor.RED));
        }
        String ownerName = plotOwner != null ? Bukkit.getOfflinePlayer(plotOwner).getName() : "Unknown";
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("aspire.admin.bypass")) {
                online.sendMessage(Component.text("[Report] ", NamedTextColor.RED)
                    .append(Component.text(reporterPlayer != null ? reporterPlayer.getName() : "???", NamedTextColor.WHITE))
                    .append(Component.text(" reported ", NamedTextColor.GRAY))
                    .append(Component.text(ownerName + "'s build", NamedTextColor.WHITE))
                    .append(Component.text(" (Plot " + (currentPlotIndex + 1) + ")", NamedTextColor.GRAY)));
            }
        }
    }

    public int getCurrentPlotIndex() {
        return currentPlotIndex;
    }

    private void calculateResults() {
        Map<UUID, Integer> scores = new HashMap<>();

        for (Map.Entry<UUID, Integer> entry : session.getPlayerPlotAssignments().entrySet()) {
            scores.putIfAbsent(entry.getKey(), 0);
        }

        for (Map.Entry<UUID, Map<Integer, VoteRating>> voterEntry : votes.entrySet()) {
            for (Map.Entry<Integer, VoteRating> voteEntry : voterEntry.getValue().entrySet()) {
                int plotIndex = voteEntry.getKey();
                int voteScore = voteEntry.getValue().getScore();

                for (Map.Entry<UUID, Integer> plotAssignment : session.getPlayerPlotAssignments().entrySet()) {
                    if (plotAssignment.getValue() == plotIndex) {
                        scores.merge(plotAssignment.getKey(), voteScore, Integer::sum);
                    }
                }
            }
        }

        session.end(scores);
    }
}

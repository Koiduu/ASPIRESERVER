package com.aspireserver.buildbattle.voting;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.game.GameSession;
import com.aspireserver.buildbattle.game.GameState;
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
            clearVotingItems();
            calculateResults();
            return;
        }

        List<PlotRegion> plots = session.getArena().getPlots();
        if (currentPlotIndex >= plots.size()) {
            clearVotingItems();
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
                    giveVotingItems(player);
                } else {
                    player.getInventory().clear();
                    player.sendMessage(Component.text("This is your plot! You cannot vote on it.", NamedTextColor.GRAY));
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

    public void giveVotingItems(Player player) {
        player.getInventory().clear();

        // Slot 0: Red Terracotta = F
        player.getInventory().setItem(0, createVoteItem(Material.RED_TERRACOTTA, "F", NamedTextColor.RED, 1));
        // Slot 1: Pink Terracotta = D
        player.getInventory().setItem(1, createVoteItem(Material.PINK_TERRACOTTA, "D", NamedTextColor.LIGHT_PURPLE, 2));
        // Slot 2: Lime Terracotta = E
        player.getInventory().setItem(2, createVoteItem(Material.LIME_TERRACOTTA, "E", NamedTextColor.GREEN, 3));
        // Slot 3: Green Terracotta = C
        player.getInventory().setItem(3, createVoteItem(Material.GREEN_TERRACOTTA, "C", NamedTextColor.DARK_GREEN, 4));
        // Slot 4: Purple Terracotta = B
        player.getInventory().setItem(4, createVoteItem(Material.PURPLE_TERRACOTTA, "B", NamedTextColor.DARK_PURPLE, 5));
        // Slot 5: Yellow Terracotta = A
        player.getInventory().setItem(5, createVoteItem(Material.YELLOW_TERRACOTTA, "A", NamedTextColor.YELLOW, 6));
        // Slot 6: Gold Block = S
        player.getInventory().setItem(6, createVoteItem(Material.GOLD_BLOCK, "S", NamedTextColor.GOLD, 7));
        // Slot 8: Barrier = Report
        ItemStack reportItem = new ItemStack(Material.BARRIER);
        ItemMeta reportMeta = reportItem.getItemMeta();
        reportMeta.displayName(Component.text("Report Build", NamedTextColor.RED, TextDecoration.BOLD));
        reportMeta.lore(List.of(
            Component.text("Right-click to report this build", NamedTextColor.GRAY),
            Component.text("for inappropriate content", NamedTextColor.GRAY)
        ));
        reportItem.setItemMeta(reportMeta);
        player.getInventory().setItem(8, reportItem);
    }

    private void clearVotingItems() {
        for (UUID uuid : session.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.getInventory().clear();
            }
        }
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
        meta.displayName(Component.text("Vote: " + grade, color, TextDecoration.BOLD));
        meta.lore(List.of(Component.text("Right-click to vote " + grade, NamedTextColor.GRAY)));
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

    public GameSession getSession() {
        return session;
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

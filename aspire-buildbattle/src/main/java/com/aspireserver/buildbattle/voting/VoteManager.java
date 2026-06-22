package com.aspireserver.buildbattle.voting;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.game.GameSession;
import com.aspireserver.buildbattle.plot.PlotRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    private int currentPlotIndex;
    private final int totalPlots;

    public VoteManager(GameSession session, AspireBuildBattle plugin) {
        this.session = session;
        this.plugin = plugin;
        this.votes = new HashMap<>();
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
        Inventory gui = Bukkit.createInventory(null, 9, Component.text("Vote!", NamedTextColor.GOLD));

        gui.setItem(1, createVoteItem(Material.BROWN_DYE, "Super Poop", NamedTextColor.DARK_RED, 1));
        gui.setItem(2, createVoteItem(Material.DIRT, "Poop", NamedTextColor.RED, 2));
        gui.setItem(3, createVoteItem(Material.COAL, "Okay", NamedTextColor.GRAY, 3));
        gui.setItem(4, createVoteItem(Material.IRON_INGOT, "Good", NamedTextColor.WHITE, 4));
        gui.setItem(5, createVoteItem(Material.GOLD_INGOT, "Epic", NamedTextColor.GOLD, 5));
        gui.setItem(6, createVoteItem(Material.DIAMOND, "Legendary", NamedTextColor.AQUA, 6));

        player.openInventory(gui);
    }

    private ItemStack createVoteItem(Material material, String name, NamedTextColor color, int score) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name + " (" + score + " pts)", color));
        item.setItemMeta(meta);
        return item;
    }

    public void registerVote(UUID player, int score) {
        Map<Integer, VoteRating> playerVotes = votes.get(player);
        if (playerVotes != null) {
            playerVotes.put(currentPlotIndex, VoteRating.fromScore(score));
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

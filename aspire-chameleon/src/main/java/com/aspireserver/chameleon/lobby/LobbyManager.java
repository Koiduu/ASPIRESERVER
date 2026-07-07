package com.aspireserver.chameleon.lobby;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.config.ConfigManager;
import com.aspireserver.chameleon.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class LobbyManager {

    private final MecchaChameleon plugin;
    private final ConfigManager configManager;
    private final GameManager gameManager;

    private final Set<UUID> lobbyPlayers = new HashSet<>();
    private BukkitTask countdownTask;
    private int countdownTime;
    private boolean countdownActive = false;

    // Votes
    private final Map<UUID, String> mapVotes = new HashMap<>();
    private final Map<UUID, Integer> durationVotes = new HashMap<>();
    private final Map<UUID, Integer> hidingVotes = new HashMap<>();
    private final Map<UUID, Integer> seekerVotes = new HashMap<>();

    public LobbyManager(MecchaChameleon plugin, ConfigManager configManager, GameManager gameManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.gameManager = gameManager;
    }

    public void addPlayer(Player player) {
        lobbyPlayers.add(player.getUniqueId());
        giveVotingItem(player);
        checkStartCountdown();
    }

    public void removePlayer(Player player) {
        lobbyPlayers.remove(player.getUniqueId());
        mapVotes.remove(player.getUniqueId());
        durationVotes.remove(player.getUniqueId());
        hidingVotes.remove(player.getUniqueId());
        seekerVotes.remove(player.getUniqueId());

        if (lobbyPlayers.size() < configManager.getMinPlayers() && countdownActive) {
            cancelCountdown();
        }
    }

    private void giveVotingItem(Player player) {
        player.getInventory().clear();
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(Component.text("Vote Menu", NamedTextColor.AQUA));
        meta.lore(List.of(Component.text("Right-click to open voting!", NamedTextColor.GRAY)));
        paper.setItemMeta(meta);
        player.getInventory().setItem(4, paper);
    }

    private void checkStartCountdown() {
        if (countdownActive) return;
        if (gameManager.isGameActive()) return;
        if (lobbyPlayers.size() >= configManager.getMinPlayers()) {
            startCountdown();
        }
    }

    private void startCountdown() {
        countdownActive = true;
        countdownTime = configManager.getLobbyCountdown();
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (countdownTime <= 0) {
                launchGame();
                return;
            }
            if (countdownTime <= 5 || countdownTime % 10 == 0) {
                broadcastLobby(Component.text("Game starting in " + countdownTime + "s!", NamedTextColor.YELLOW));
            }
            countdownTime--;
        }, 20L, 20L);
    }

    private void cancelCountdown() {
        countdownActive = false;
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        broadcastLobby(Component.text("Not enough players! Countdown cancelled.", NamedTextColor.RED));
    }

    private void launchGame() {
        countdownActive = false;
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        // Tally votes
        String selectedMap = tallyMapVotes();
        int selectedDuration = tallyIntVotes(durationVotes, configManager.getDefaultRoundDuration());
        int selectedHiding = tallyIntVotes(hidingVotes, configManager.getDefaultHidingDuration());
        int selectedSeekers = tallyIntVotes(seekerVotes, configManager.getDefaultSeekerCount());

        List<Player> participants = new ArrayList<>();
        for (UUID uuid : lobbyPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                participants.add(p);
            }
        }

        if (participants.size() < configManager.getMinPlayers()) {
            broadcastLobby(Component.text("Not enough players! Game cancelled.", NamedTextColor.RED));
            return;
        }

        lobbyPlayers.clear();
        mapVotes.clear();
        durationVotes.clear();
        hidingVotes.clear();
        seekerVotes.clear();

        gameManager.startGame(selectedMap, selectedDuration, selectedHiding, selectedSeekers, participants);
    }

    private String tallyMapVotes() {
        Map<String, Integer> counts = new HashMap<>();
        for (String vote : mapVotes.values()) {
            counts.merge(vote, 1, Integer::sum);
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        if (best == null) {
            // Pick random map
            Set<String> ids = configManager.getMapIds();
            if (!ids.isEmpty()) {
                List<String> list = new ArrayList<>(ids);
                Collections.shuffle(list);
                best = list.get(0);
            }
        }
        return best;
    }

    private int tallyIntVotes(Map<UUID, Integer> votes, int defaultVal) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int v : votes.values()) {
            counts.merge(v, 1, Integer::sum);
        }
        int best = defaultVal;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    public void openVotingGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 36,
                Component.text("Chameleon Voting", NamedTextColor.GOLD));

        // Row 1: Map selection (slots 0-8)
        int slot = 0;
        for (String mapId : configManager.getMapIds()) {
            if (slot >= 9) break;
            ConfigManager.MapData data = configManager.getMap(mapId);
            ItemStack item = new ItemStack(Material.FILLED_MAP);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(data.displayName, NamedTextColor.GREEN));
            meta.lore(List.of(
                    Component.text("Vote for this map", NamedTextColor.GRAY),
                    Component.text("ID: " + mapId, NamedTextColor.DARK_GRAY)
            ));
            item.setItemMeta(meta);
            gui.setItem(slot++, item);
        }

        // Row 2: Round Duration (slots 9-17)
        int[] durations = {180, 300, 420};
        String[] durationNames = {"3 Minutes", "5 Minutes", "7 Minutes"};
        for (int i = 0; i < 3; i++) {
            ItemStack item = new ItemStack(Material.CLOCK, i + 1);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(durationNames[i], NamedTextColor.AQUA));
            meta.lore(List.of(Component.text("Vote for round duration", NamedTextColor.GRAY)));
            item.setItemMeta(meta);
            gui.setItem(9 + i, item);
        }

        // Row 3: Hiding Duration (slots 18-26)
        int[] hidingTimes = {20, 30, 45};
        for (int i = 0; i < 3; i++) {
            ItemStack item = new ItemStack(Material.LEATHER_BOOTS, hidingTimes[i] / 10);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(hidingTimes[i] + " Seconds", NamedTextColor.YELLOW));
            meta.lore(List.of(Component.text("Vote for hiding phase duration", NamedTextColor.GRAY)));
            item.setItemMeta(meta);
            gui.setItem(18 + i, item);
        }

        // Row 4: Seeker Count (slots 27-35)
        for (int i = 1; i <= 3; i++) {
            ItemStack item = new ItemStack(Material.IRON_SWORD, i);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(i + " Seeker" + (i > 1 ? "s" : ""), NamedTextColor.RED));
            meta.lore(List.of(Component.text("Vote for number of seekers", NamedTextColor.GRAY)));
            item.setItemMeta(meta);
            gui.setItem(27 + i - 1, item);
        }

        player.openInventory(gui);
    }

    public void handleVote(Player player, int slot) {
        // Map votes: slots 0-8
        if (slot < 9) {
            List<String> mapIds = new ArrayList<>(configManager.getMapIds());
            if (slot < mapIds.size()) {
                mapVotes.put(player.getUniqueId(), mapIds.get(slot));
                player.sendMessage(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                        .append(Component.text("Voted for map: " + mapIds.get(slot), NamedTextColor.AQUA)));
            }
        }
        // Duration votes: slots 9-11
        else if (slot >= 9 && slot <= 11) {
            int[] durations = {180, 300, 420};
            durationVotes.put(player.getUniqueId(), durations[slot - 9]);
            player.sendMessage(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                    .append(Component.text("Voted for " + (durations[slot - 9] / 60) + " min rounds", NamedTextColor.AQUA)));
        }
        // Hiding votes: slots 18-20
        else if (slot >= 18 && slot <= 20) {
            int[] hidingTimes = {20, 30, 45};
            hidingVotes.put(player.getUniqueId(), hidingTimes[slot - 18]);
            player.sendMessage(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                    .append(Component.text("Voted for " + hidingTimes[slot - 18] + "s hiding", NamedTextColor.AQUA)));
        }
        // Seeker votes: slots 27-29
        else if (slot >= 27 && slot <= 29) {
            seekerVotes.put(player.getUniqueId(), slot - 26);
            player.sendMessage(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                    .append(Component.text("Voted for " + (slot - 26) + " seeker(s)", NamedTextColor.AQUA)));
        }
    }

    public boolean isInLobby(UUID uuid) {
        return lobbyPlayers.contains(uuid);
    }

    private void broadcastLobby(Component message) {
        Component full = Component.text("[Chameleon] ", NamedTextColor.GREEN).append(message);
        for (UUID uuid : lobbyPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(full);
        }
    }
}

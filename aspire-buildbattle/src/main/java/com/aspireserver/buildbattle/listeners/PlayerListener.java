package com.aspireserver.buildbattle.listeners;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.game.GameSession;
import com.aspireserver.buildbattle.game.GameState;
import com.aspireserver.buildbattle.voting.VoteManager;
import com.aspireserver.buildbattle.voting.VoteRating;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerListener implements Listener {

    private final AspireBuildBattle plugin;

    public PlayerListener(AspireBuildBattle plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session != null) {
            plugin.getArenaManager().leaveSession(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component title = event.getView().title();
        String titleText = PlainTextComponentSerializer.plainText().serialize(title);

        if (titleText.equals(VoteManager.VOTE_GUI_TITLE)) {
            handleVoteClick(event, player);
        } else if (titleText.equals(VoteManager.REPORT_CONFIRM_TITLE)) {
            handleReportConfirmClick(event, player);
        }
    }

    private void handleVoteClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session == null || session.getState() != GameState.VOTING) return;

        int slot = event.getSlot();

        if (slot == 13 && clicked.getType() == Material.BARRIER) {
            session.getVoteManager().openReportConfirmGui(player);
            return;
        }

        int score = switch (slot) {
            case 1 -> VoteRating.F.getScore();
            case 2 -> VoteRating.D.getScore();
            case 3 -> VoteRating.E.getScore();
            case 4 -> VoteRating.C.getScore();
            case 5 -> VoteRating.B.getScore();
            case 6 -> VoteRating.A.getScore();
            case 7 -> VoteRating.S.getScore();
            default -> -1;
        };

        if (score > 0) {
            session.getVoteManager().registerVote(player.getUniqueId(), score);
            player.sendMessage(Component.text("Vote registered!", NamedTextColor.GREEN));
            player.closeInventory();
        }
    }

    private void handleReportConfirmClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session == null || session.getState() != GameState.VOTING) return;

        int slot = event.getSlot();
        if (slot == 11) {
            session.getVoteManager().registerReport(player.getUniqueId());
            player.closeInventory();
        } else if (slot == 15) {
            session.getVoteManager().openVoteGui(player);
        }
    }
}

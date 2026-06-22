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
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session == null || session.getState() != GameState.VOTING) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        Material type = item.getType();
        int score = getScoreFromMaterial(type);

        if (score > 0) {
            event.setCancelled(true);
            VoteManager vm = session.getVoteManager();
            if (vm != null) {
                vm.registerVote(player.getUniqueId(), score);
                player.sendMessage(Component.text("Vote registered! (" + VoteRating.fromScore(score).name() + ")", NamedTextColor.GREEN));
            }
        } else if (type == Material.BARRIER) {
            event.setCancelled(true);
            VoteManager vm = session.getVoteManager();
            if (vm != null) {
                vm.openReportConfirmGui(player);
            }
        }
    }

    private int getScoreFromMaterial(Material material) {
        return switch (material) {
            case RED_TERRACOTTA -> VoteRating.F.getScore();
            case PINK_TERRACOTTA -> VoteRating.D.getScore();
            case LIME_TERRACOTTA -> VoteRating.E.getScore();
            case GREEN_TERRACOTTA -> VoteRating.C.getScore();
            case PURPLE_TERRACOTTA -> VoteRating.B.getScore();
            case YELLOW_TERRACOTTA -> VoteRating.A.getScore();
            case GOLD_BLOCK -> VoteRating.S.getScore();
            default -> -1;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component title = event.getView().title();
        String titleText = PlainTextComponentSerializer.plainText().serialize(title);

        if (titleText.equals(VoteManager.REPORT_CONFIRM_TITLE)) {
            handleReportConfirmClick(event, player);
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
            player.closeInventory();
        }
    }
}

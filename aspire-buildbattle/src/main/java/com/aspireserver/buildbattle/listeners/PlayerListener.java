package com.aspireserver.buildbattle.listeners;

import com.aspireserver.buildbattle.AspireBuildBattle;
import com.aspireserver.buildbattle.game.GameSession;
import com.aspireserver.buildbattle.voting.VoteRating;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
        plugin.getArenaManager().leaveSession(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component title = event.getView().title();
        String titleText = PlainTextComponentSerializer.plainText().serialize(title);
        if (!titleText.equals("Vote!")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        GameSession session = plugin.getArenaManager().getPlayerSession(player.getUniqueId());
        if (session == null) return;

        int slot = event.getSlot();
        int score = switch (slot) {
            case 1 -> VoteRating.SUPER_POOP.getScore();
            case 2 -> VoteRating.POOP.getScore();
            case 3 -> VoteRating.OKAY.getScore();
            case 4 -> VoteRating.GOOD.getScore();
            case 5 -> VoteRating.EPIC.getScore();
            case 6 -> VoteRating.LEGENDARY.getScore();
            default -> -1;
        };

        if (score > 0) {
            player.sendMessage(Component.text("Vote registered!", NamedTextColor.GREEN));
            player.closeInventory();
        }
    }
}

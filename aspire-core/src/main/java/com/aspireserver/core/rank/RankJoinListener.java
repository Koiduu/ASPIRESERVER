package com.aspireserver.core.rank;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class RankJoinListener implements Listener {

    private final RankManager rankManager;

    public RankJoinListener(RankManager rankManager) {
        this.rankManager = rankManager;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Component displayName = rankManager.getDisplayName(player);
        player.displayName(displayName);
        player.playerListName(displayName);
    }
}

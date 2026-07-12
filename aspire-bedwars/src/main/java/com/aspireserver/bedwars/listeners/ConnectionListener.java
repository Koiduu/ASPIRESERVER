package com.aspireserver.bedwars.listeners;

import com.aspireserver.bedwars.AspireBedwars;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConnectionListener implements Listener {

    private final AspireBedwars plugin;

    public ConnectionListener(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getGameManager().isInBedwarsWorld(event.getPlayer())) {
            plugin.getGameManager().handleReconnect(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getGameManager().isInBedwarsWorld(event.getPlayer())
                || plugin.getGameManager().getLobbyPlayers().contains(event.getPlayer().getUniqueId())) {
            plugin.getGameManager().handleDisconnect(event.getPlayer());
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        String bw = plugin.getSetupConfig().getWorldName();
        boolean nowIn = event.getPlayer().getWorld().getName().equalsIgnoreCase(bw);
        boolean wasIn = event.getFrom().getName().equalsIgnoreCase(bw);
        if (nowIn && !wasIn) {
            plugin.getGameManager().joinLobby(event.getPlayer());
        } else if (wasIn && !nowIn) {
            plugin.getGameManager().leaveLobby(event.getPlayer().getUniqueId());
            plugin.getSpectatorManager().removeSpectator(event.getPlayer());
            event.getPlayer().setScoreboard(plugin.getServer().getScoreboardManager().getMainScoreboard());
        }
    }
}

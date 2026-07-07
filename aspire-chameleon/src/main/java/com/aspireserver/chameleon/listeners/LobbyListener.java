package com.aspireserver.chameleon.listeners;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.config.ConfigManager;
import com.aspireserver.chameleon.lobby.LobbyManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class LobbyListener implements Listener {

    private final MecchaChameleon plugin;

    public LobbyListener(MecchaChameleon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        ConfigManager cfg = plugin.getConfigManager();

        // Entered chameleon world
        if (player.getWorld().getName().equalsIgnoreCase(cfg.getWorldName())) {
            if (!plugin.getGameManager().isGameActive()) {
                plugin.getLobbyManager().addPlayer(player);
            }
        }

        // Left chameleon world
        if (event.getFrom().getName().equalsIgnoreCase(cfg.getWorldName())) {
            plugin.getLobbyManager().removePlayer(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getName().equalsIgnoreCase(plugin.getConfigManager().getWorldName())) {
            if (!plugin.getGameManager().isGameActive()) {
                plugin.getLobbyManager().addPlayer(player);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getLobbyManager().removePlayer(player);
        plugin.getGameManager().handleDisconnect(player);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equalsIgnoreCase(plugin.getConfigManager().getWorldName())) return;
        if (!event.getAction().isRightClick()) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PAPER) return;
        if (!item.hasItemMeta()) return;

        String name = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        if ("Vote Menu".equals(name)) {
            event.setCancelled(true);
            plugin.getLobbyManager().openVotingGui(player);
        }
    }
}

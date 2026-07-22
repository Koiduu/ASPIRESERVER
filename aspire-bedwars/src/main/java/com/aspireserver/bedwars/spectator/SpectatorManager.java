package com.aspireserver.bedwars.spectator;

import com.aspireserver.bedwars.AspireBedwars;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SpectatorManager {

    private final AspireBedwars plugin;
    private final Set<UUID> spectators = new HashSet<>();

    public SpectatorManager(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    public boolean isSpectator(UUID uuid) { return spectators.contains(uuid); }
    public Set<UUID> getSpectators() { return spectators; }

    public void makeSpectator(Player player) {
        spectators.add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear();
        plugin.getChatManager().setSpectator(player.getUniqueId(), true);

        // Hide from living players
        for (Player other : player.getServer().getOnlinePlayers()) {
            if (!spectators.contains(other.getUniqueId())) {
                other.hidePlayer(plugin, player);
            }
        }
        player.sendMessage(Component.text("You are now spectating. Type in chat to talk to other spectators.", NamedTextColor.GRAY));
    }

    public void removeSpectator(Player player) {
        spectators.remove(player.getUniqueId());
        plugin.getChatManager().setSpectator(player.getUniqueId(), false);
        for (Player other : player.getServer().getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }
    }

    public void clearAll() {
        for (UUID uuid : new HashSet<>(spectators)) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) removeSpectator(p);
        }
        spectators.clear();
    }
}

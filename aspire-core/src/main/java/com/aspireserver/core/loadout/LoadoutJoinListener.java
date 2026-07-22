package com.aspireserver.core.loadout;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class LoadoutJoinListener implements Listener {

    private final LoadoutManager manager;
    private final JavaPlugin plugin;

    public LoadoutJoinListener(LoadoutManager manager, JavaPlugin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String autoName = manager.getAutoLoadout(player.getUniqueId());
        if (autoName == null) return;

        ItemStack[] contents = manager.getLoadout(player.getUniqueId(), autoName);
        if (contents == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.getInventory().setContents(contents);
                    player.sendMessage(Component.text("Auto-loaded inventory: " + autoName, NamedTextColor.GREEN));
                }
            }
        }.runTaskLater(plugin, 5L);
    }
}

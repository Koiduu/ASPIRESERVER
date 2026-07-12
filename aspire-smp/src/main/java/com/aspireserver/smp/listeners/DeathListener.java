package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathListener implements Listener {

    private final AspireSMP plugin;

    public DeathListener(AspireSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        // Vanilla behavior: drop all items on death
        event.setKeepInventory(false);
        event.setKeepLevel(false);
    }
}

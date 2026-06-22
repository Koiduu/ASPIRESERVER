package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SleepListener implements Listener {

    private final AspireSMP plugin;
    private final Set<UUID> sleepingPlayers;

    public SleepListener(AspireSMP plugin) {
        this.plugin = plugin;
        this.sleepingPlayers = new HashSet<>();
    }

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        Player player = event.getPlayer();
        sleepingPlayers.add(player.getUniqueId());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (sleepingPlayers.contains(player.getUniqueId())) {
                skipNight(player.getWorld());
            }
        }, 100L);

        int sleeping = sleepingPlayers.size();
        Bukkit.broadcast(Component.text(player.getName() + " is sleeping (" + sleeping + " sleeping)", NamedTextColor.YELLOW));
    }

    @EventHandler
    public void onBedLeave(PlayerBedLeaveEvent event) {
        sleepingPlayers.remove(event.getPlayer().getUniqueId());
    }

    private void skipNight(World world) {
        if (world.getTime() >= 12542 && world.getTime() <= 23459) {
            world.setTime(0);
            world.setStorm(false);
            world.setThundering(false);
            Bukkit.broadcast(Component.text("Good morning! The night was skipped.", NamedTextColor.GOLD));
            sleepingPlayers.clear();
        }
    }
}

package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.Statistic;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import org.bukkit.GameMode;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class SmpJoinListener implements Listener {

    private final AspireSMP plugin;
    private final Map<UUID, Long> damageImmunity = new ConcurrentHashMap<>();
    private final Set<UUID> firstJoinPlayers;
    private File firstJoinFile;
    private FileConfiguration firstJoinData;
    private static final int IMMUNITY_SECONDS = 7;
    private static final int SLOW_CHUNK_VIEW = 15;
    private static final int NORMAL_CHUNK_VIEW = 15;
    private static final int SCORE_THRESHOLD = 200;

    public SmpJoinListener(AspireSMP plugin) {
        this.plugin = plugin;
        this.firstJoinPlayers = ConcurrentHashMap.newKeySet();
        loadFirstJoinData();
    }

    private void loadFirstJoinData() {
        firstJoinFile = new File(plugin.getDataFolder(), "first-joins.yml");
        if (!firstJoinFile.exists()) {
            try {
                firstJoinFile.getParentFile().mkdirs();
                firstJoinFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create first-joins.yml");
            }
        }
        firstJoinData = YamlConfiguration.loadConfiguration(firstJoinFile);
        if (firstJoinData.contains("players")) {
            for (String uuid : firstJoinData.getStringList("players")) {
                firstJoinPlayers.add(UUID.fromString(uuid));
            }
        }
    }

    private void saveFirstJoinData() {
        firstJoinData.set("players", firstJoinPlayers.stream().map(UUID::toString).toList());
        try {
            firstJoinData.save(firstJoinFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save first-joins.yml");
        }
    }

    private boolean hasJoinedSmBefore(UUID uuid) {
        return firstJoinPlayers.contains(uuid);
    }

    private void markFirstJoin(UUID uuid) {
        firstJoinPlayers.add(uuid);
        saveFirstJoinData();
    }

    private boolean isSmpWorld(World world) {
        String smpWorld = plugin.getConfig().getString("smp-world", "");
        return !smpWorld.isEmpty() && world.getName().equalsIgnoreCase(smpWorld);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isSmpWorld(player.getWorld())) {
            player.setGameMode(GameMode.SURVIVAL);
            if (!hasJoinedSmBefore(player.getUniqueId()) && !isExperiencedPlayer(player)) {
                handleFirstJoin(player);
            } else {
                markFirstJoin(player.getUniqueId());
                applyImmunity(player);
            }
            applySlowChunks(player);
        } else if (isSmpWorld(event.getFrom())) {
            resetChunkView(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isSmpWorld(player.getWorld())) {
                player.setGameMode(GameMode.SURVIVAL);
                if (!hasJoinedSmBefore(player.getUniqueId()) && !isExperiencedPlayer(player)) {
                    handleFirstJoin(player);
                } else {
                    markFirstJoin(player.getUniqueId());
                    applyImmunity(player);
                }
                applySlowChunks(player);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPetTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Tameable tameable)) return;
        if (tameable.getOwner() == null) return;
        if (event.getTo() == null) return;
        World fromWorld = event.getFrom().getWorld();
        World toWorld = event.getTo().getWorld();
        if (fromWorld == null || toWorld == null) return;
        if (isSmpWorld(fromWorld) && !isSmpWorld(toWorld)) {
            event.setCancelled(true);
        }
        if (!isSmpWorld(fromWorld) && isSmpWorld(toWorld)) {
            event.setCancelled(true);
        }
    }

    private boolean isExperiencedPlayer(Player player) {
        int score = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20;
        return score >= SCORE_THRESHOLD;
    }

    private void handleFirstJoin(Player player) {
        markFirstJoin(player.getUniqueId());
        applyImmunity(player);
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("══════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("  Welcome to SMP!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("  To set your spawn correctly,", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("  please use ", NamedTextColor.YELLOW)
                .append(Component.text("/kill", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.suggestCommand("/kill")))
                .append(Component.text(" to respawn.", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("  (Click the red /kill text!)", NamedTextColor.GRAY));
        player.sendMessage(Component.text("══════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.empty());
    }

    private void applyImmunity(Player player) {
        damageImmunity.put(player.getUniqueId(), System.currentTimeMillis() + (IMMUNITY_SECONDS * 1000L));
        player.sendMessage(Component.text("You have " + IMMUNITY_SECONDS + "s damage immunity.", NamedTextColor.GREEN));

        new BukkitRunnable() {
            @Override
            public void run() {
                damageImmunity.remove(player.getUniqueId());
                if (player.isOnline()) {
                    player.sendMessage(Component.text("Damage immunity expired.", NamedTextColor.YELLOW));
                }
            }
        }.runTaskLater(plugin, IMMUNITY_SECONDS * 20L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isSmpWorld(player.getWorld())) return;

        Long expiry = damageImmunity.get(player.getUniqueId());
        if (expiry != null && System.currentTimeMillis() < expiry) {
            event.setCancelled(true);
        }
    }

    private void applySlowChunks(Player player) {
        player.setViewDistance(SLOW_CHUNK_VIEW);
    }

    private void resetChunkView(Player player) {
        player.setViewDistance(NORMAL_CHUNK_VIEW);
    }

    private final Map<UUID, Long> lastGlideCheck = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        if (!isSmpWorld(player.getWorld())) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - lastGlideCheck.getOrDefault(uuid, 0L) < 2000) return;
        lastGlideCheck.put(uuid, now);

        if (player.isGliding()) {
            if (player.getViewDistance() != NORMAL_CHUNK_VIEW) {
                player.setViewDistance(NORMAL_CHUNK_VIEW);
            }
        } else {
            if (player.getViewDistance() != SLOW_CHUNK_VIEW) {
                player.setViewDistance(SLOW_CHUNK_VIEW);
            }
        }
    }
}

package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class SmpJoinListener implements Listener {

    private final AspireSMP plugin;
    private final Map<UUID, Long> damageImmunity = new ConcurrentHashMap<>();
    private final Set<UUID> pendingFirstSpawnKill = ConcurrentHashMap.newKeySet();
    private final Set<UUID> firstJoinPlayers;
    private File firstJoinFile;
    private FileConfiguration firstJoinData;
    private static final int IMMUNITY_SECONDS = 7;
    private static final int SLOW_CHUNK_VIEW = 4;
    private static final int NORMAL_CHUNK_VIEW = 10;

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
            if (!hasJoinedSmBefore(player.getUniqueId()) && isInventoryEmpty(player)) {
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
                if (!hasJoinedSmBefore(player.getUniqueId()) && isInventoryEmpty(player)) {
                    handleFirstJoin(player);
                } else {
                    markFirstJoin(player.getUniqueId());
                    applyImmunity(player);
                }
                applySlowChunks(player);
            }
        }, 5L);
    }

    private boolean isInventoryEmpty(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) return false;
        }
        return true;
    }

    private void handleFirstJoin(Player player) {
        pendingFirstSpawnKill.add(player.getUniqueId());
        markFirstJoin(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isSmpWorld(player.getWorld())) {
                player.setHealth(0);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFirstJoinDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (pendingFirstSpawnKill.contains(player.getUniqueId())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFirstJoinRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (pendingFirstSpawnKill.remove(player.getUniqueId())) {
            applyImmunity(player);
        }
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

package com.aspireserver.chameleon.game;

import com.aspireserver.chameleon.AspireChameleon;
import com.aspireserver.chameleon.config.ConfigManager;
import com.aspireserver.chameleon.config.ConfigManager.SkinEntry;
import com.aspireserver.chameleon.skin.SkinCache;
import com.aspireserver.chameleon.skin.SkinCache.CachedSkin;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GameManager {

    private final AspireChameleon plugin;
    private final ConfigManager configManager;
    private final SkinCache skinCache;

    private boolean gameActive = false;
    private String currentMap = null;
    private BukkitTask graceTask = null;
    private boolean gracePeriodActive = false;

    private final Set<UUID> activePlayers = new HashSet<>();
    private final Map<UUID, PlayerProfile> originalProfiles = new HashMap<>();

    public GameManager(AspireChameleon plugin, ConfigManager configManager, SkinCache skinCache) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.skinCache = skinCache;
    }

    public boolean startGame(String mapName) {
        if (gameActive) return false;

        // Allow starting even without skins — spawn must be set OR map must exist in config
        Location mapSpawn = configManager.getMapSpawn(mapName);

        this.currentMap = mapName;
        this.gameActive = true;
        this.gracePeriodActive = true;

        List<SkinEntry> skins = configManager.getSkinsForMap(mapName);

        for (Player player : Bukkit.getOnlinePlayers()) {
            activePlayers.add(player.getUniqueId());
            originalProfiles.put(player.getUniqueId(), player.getPlayerProfile());

            // Teleport to map spawn if set
            if (mapSpawn != null) {
                player.teleport(mapSpawn);
            }

            shrinkPlayer(player);

            // Only open skin menu if skins are configured
            if (!skins.isEmpty()) {
                openSkinMenu(player);
            }
        }

        Bukkit.broadcast(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                .append(Component.text("Game started on map: " + configManager.getMapDisplayName(mapName), NamedTextColor.YELLOW)));
        if (!skins.isEmpty()) {
            Bukkit.broadcast(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                    .append(Component.text("You have " + configManager.getGracePeriod() + "s to pick your camo skin!", NamedTextColor.AQUA)));
        }

        // End grace period after configured time
        graceTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            gracePeriodActive = false;
            Bukkit.broadcast(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                    .append(Component.text("Grace period over! Hide!", NamedTextColor.RED)));
        }, configManager.getGracePeriod() * 20L);

        return true;
    }

    public void stopGame() {
        if (!gameActive) return;

        gameActive = false;
        gracePeriodActive = false;
        currentMap = null;

        if (graceTask != null) {
            graceTask.cancel();
            graceTask = null;
        }

        for (UUID uuid : activePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                restorePlayer(player);
            }
        }

        activePlayers.clear();
        originalProfiles.clear();

        Bukkit.broadcast(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                .append(Component.text("Game ended! All players restored.", NamedTextColor.YELLOW)));
    }

    public void shrinkPlayer(Player player) {
        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(configManager.getScaleFactor());
        }
    }

    public void restorePlayer(Player player) {
        // Restore scale
        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(1.0);
        }

        // Restore original skin
        PlayerProfile original = originalProfiles.get(player.getUniqueId());
        if (original != null) {
            player.setPlayerProfile(original);
        }
    }

    public void applySkin(Player player, CachedSkin skin) {
        PlayerProfile profile = player.getPlayerProfile();
        profile.getProperties().removeIf(p -> "textures".equals(p.getName()));
        profile.getProperties().add(new ProfileProperty("textures", skin.textureValue(), skin.textureSignature()));
        player.setPlayerProfile(profile);

        player.sendMessage(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                .append(Component.text("Skin changed to: " + skin.name(), NamedTextColor.AQUA)));
    }

    public void openSkinMenu(Player player) {
        if (currentMap == null) return;
        List<SkinEntry> skins = configManager.getSkinsForMap(currentMap);
        if (skins.isEmpty()) return;

        int size = Math.min(54, ((skins.size() + 8) / 9) * 9);
        Inventory gui = Bukkit.createInventory(null, size, Component.text("Camo Skin Selection", NamedTextColor.DARK_GREEN));

        for (int i = 0; i < skins.size() && i < 54; i++) {
            SkinEntry entry = skins.get(i);
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(entry.name(), NamedTextColor.GREEN));
            meta.lore(List.of(
                    Component.text("Click to apply this camouflage", NamedTextColor.GRAY),
                    Component.text("UUID: " + entry.uuid(), NamedTextColor.DARK_GRAY)
            ));
            item.setItemMeta(meta);
            gui.setItem(i, item);
        }

        player.openInventory(gui);
    }

    public void handlePlayerDisconnect(UUID uuid) {
        if (!activePlayers.contains(uuid)) return;
        // Keep their data — restore on next login
    }

    public void handlePlayerReconnect(Player player) {
        if (!activePlayers.contains(player.getUniqueId())) return;

        if (gameActive) {
            shrinkPlayer(player);
        } else {
            // Game ended while they were offline — restore
            restorePlayer(player);
            activePlayers.remove(player.getUniqueId());
            originalProfiles.remove(player.getUniqueId());
        }
    }

    public boolean isGameActive() { return gameActive; }
    public boolean isGracePeriodActive() { return gracePeriodActive; }
    public String getCurrentMap() { return currentMap; }
    public Set<UUID> getActivePlayers() { return activePlayers; }
}

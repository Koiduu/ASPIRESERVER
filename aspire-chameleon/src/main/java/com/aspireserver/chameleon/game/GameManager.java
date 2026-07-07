package com.aspireserver.chameleon.game;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.config.ConfigManager;
import com.aspireserver.chameleon.config.ConfigManager.MapData;
import com.aspireserver.chameleon.config.ConfigManager.SkinEntry;
import com.aspireserver.chameleon.scoreboard.ScoreboardManager;
import com.aspireserver.chameleon.skin.SkinCache;
import com.aspireserver.chameleon.skin.SkinCache.CachedSkin;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager {

    private final MecchaChameleon plugin;
    private final ConfigManager configManager;
    private final SkinCache skinCache;
    private final ScoreboardManager scoreboardManager;

    private GameState state = GameState.INACTIVE;
    private String currentMapId;
    private int roundDuration;
    private int hidingDuration;
    private int seekerCount;
    private int timeRemaining;

    private BukkitTask gameTickTask;
    private BukkitTask revealTask;

    private final Map<UUID, PlayerRole> playerRoles = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerProfile> originalProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastHiderLocations = new ConcurrentHashMap<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final List<ArmorStand> revealEntities = new ArrayList<>();

    public GameManager(MecchaChameleon plugin, ConfigManager configManager, SkinCache skinCache, ScoreboardManager scoreboardManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.skinCache = skinCache;
        this.scoreboardManager = scoreboardManager;
    }

    public boolean startGame(String mapId, int roundDuration, int hidingDuration, int seekerCount, List<Player> participants) {
        if (state != GameState.INACTIVE && state != GameState.LOBBY) return false;

        MapData mapData = configManager.getMap(mapId);
        if (mapData == null) return false;

        this.currentMapId = mapId;
        this.roundDuration = roundDuration;
        this.hidingDuration = hidingDuration;
        this.seekerCount = seekerCount;
        this.timeRemaining = hidingDuration;
        this.state = GameState.HIDING;

        // Select seekers randomly
        List<Player> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled);
        int actualSeekers = Math.min(seekerCount, shuffled.size() - 1);

        for (int i = 0; i < shuffled.size(); i++) {
            Player p = shuffled.get(i);
            originalProfiles.put(p.getUniqueId(), p.getPlayerProfile());

            if (i < actualSeekers) {
                playerRoles.put(p.getUniqueId(), PlayerRole.SEEKER);
                setupSeeker(p);
            } else {
                playerRoles.put(p.getUniqueId(), PlayerRole.HIDER);
                setupHider(p, mapData, i - actualSeekers);
            }
        }

        // Hide nametags for all game participants
        hideNametags();

        // Start scoreboard
        scoreboardManager.startScoreboard(this);

        // Start game tick
        gameTickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::onTick, 20L, 20L);

        broadcastGame(Component.text("Game started! ", NamedTextColor.GREEN)
                .append(Component.text("Hiders have " + hidingDuration + "s to hide!", NamedTextColor.AQUA)));

        return true;
    }

    private void setupSeeker(Player player) {
        player.getInventory().clear();
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);

        // Teleport to hunter room during hiding phase
        Location hunterRoom = configManager.getHunterRoom();
        if (hunterRoom != null) {
            player.teleport(hunterRoom);
        }

        // Blind during hiding phase
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, hidingDuration * 20, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, hidingDuration * 20, 255, false, false));

        player.showTitle(Title.title(
                Component.text("SEEKER", NamedTextColor.RED),
                Component.text("Wait for the hiding phase to end...", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
        ));
    }

    private void setupHider(Player player, MapData mapData, int index) {
        player.getInventory().clear();
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);

        // Teleport to a hider spawn
        if (!mapData.hiderSpawns.isEmpty()) {
            Location spawn = mapData.hiderSpawns.get(index % mapData.hiderSpawns.size());
            player.teleport(spawn);
        }

        // Shrink
        shrinkPlayer(player);

        // Give hider items
        giveHiderItems(player);

        // Make hiders invisible to each other
        for (UUID otherId : playerRoles.keySet()) {
            if (otherId.equals(player.getUniqueId())) continue;
            if (playerRoles.get(otherId) == PlayerRole.HIDER) {
                Player other = Bukkit.getPlayer(otherId);
                if (other != null) {
                    other.hidePlayer(plugin, player);
                    player.hidePlayer(plugin, other);
                }
            }
        }

        player.showTitle(Title.title(
                Component.text("HIDER", NamedTextColor.GREEN),
                Component.text("Find a hiding spot! Use your items!", NamedTextColor.AQUA),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
        ));
    }

    private void giveHiderItems(Player player) {
        // Slot 0 - Pose Changer
        ItemStack pose = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta poseMeta = pose.getItemMeta();
        poseMeta.displayName(Component.text("Pose Changer", NamedTextColor.GREEN));
        poseMeta.lore(List.of(Component.text("Right-click to change pose", NamedTextColor.GRAY)));
        pose.setItemMeta(poseMeta);
        player.getInventory().setItem(0, pose);

        // Slot 1 - Whistle/Taunt
        ItemStack whistle = new ItemStack(Material.RED_CONCRETE);
        ItemMeta whistleMeta = whistle.getItemMeta();
        whistleMeta.displayName(Component.text("Whistle", NamedTextColor.RED));
        whistleMeta.lore(List.of(Component.text("Right-click to taunt seekers!", NamedTextColor.GRAY)));
        whistle.setItemMeta(whistleMeta);
        player.getInventory().setItem(1, whistle);

        // Slot 2 - Free-Cam
        ItemStack freeCam = new ItemStack(Material.YELLOW_CONCRETE);
        ItemMeta freeCamMeta = freeCam.getItemMeta();
        freeCamMeta.displayName(Component.text("Free-Cam", NamedTextColor.YELLOW));
        freeCamMeta.lore(List.of(Component.text("Right-click to toggle free-cam view", NamedTextColor.GRAY)));
        freeCam.setItemMeta(freeCamMeta);
        player.getInventory().setItem(2, freeCam);

        // Slot 3 - Lock Position
        ItemStack lock = new ItemStack(Material.ORANGE_CONCRETE);
        ItemMeta lockMeta = lock.getItemMeta();
        lockMeta.displayName(Component.text("Lock Position", NamedTextColor.GOLD));
        lockMeta.lore(List.of(Component.text("Right-click to freeze in place", NamedTextColor.GRAY)));
        lock.setItemMeta(lockMeta);
        player.getInventory().setItem(3, lock);

        // Slot 8 - Skin Selection
        ItemStack skinItem = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta skinMeta = skinItem.getItemMeta();
        skinMeta.displayName(Component.text("Skin Selection", NamedTextColor.LIGHT_PURPLE));
        skinMeta.lore(List.of(Component.text("Right-click to choose your camo skin", NamedTextColor.GRAY)));
        skinItem.setItemMeta(skinMeta);
        player.getInventory().setItem(8, skinItem);
    }

    private void onTick() {
        timeRemaining--;

        if (state == GameState.HIDING) {
            if (timeRemaining <= 0) {
                transitionToHunting();
            }
        } else if (state == GameState.HUNTING) {
            if (timeRemaining <= 0) {
                endGame(true); // Hiders win (time ran out)
            }
        }

        // Track hider positions
        for (Map.Entry<UUID, PlayerRole> entry : playerRoles.entrySet()) {
            if (entry.getValue() == PlayerRole.HIDER) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    lastHiderLocations.put(entry.getKey(), p.getLocation().clone());
                }
            }
        }

        scoreboardManager.updateScoreboard(this);
    }

    private void transitionToHunting() {
        state = GameState.HUNTING;
        timeRemaining = roundDuration;

        MapData mapData = configManager.getMap(currentMapId);

        // Release seekers
        for (Map.Entry<UUID, PlayerRole> entry : playerRoles.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;

            if (entry.getValue() == PlayerRole.SEEKER) {
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                p.removePotionEffect(PotionEffectType.SLOWNESS);

                // Teleport seeker to map
                if (mapData != null && mapData.seekerSpawn != null) {
                    p.teleport(mapData.seekerSpawn);
                } else if (mapData != null && !mapData.hiderSpawns.isEmpty()) {
                    p.teleport(mapData.hiderSpawns.get(0));
                }

                giveSeekerLoadout(p);

                p.showTitle(Title.title(
                        Component.text("HUNT!", NamedTextColor.RED),
                        Component.text("Find the hiders!", NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
                ));
            }
        }

        broadcastGame(Component.text("Hunting phase started! Seekers are released!", NamedTextColor.RED));
    }

    private void giveSeekerLoadout(Player player) {
        player.getInventory().clear();

        // Iron Sword
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta swordMeta = sword.getItemMeta();
        swordMeta.displayName(Component.text("Hunter's Blade", NamedTextColor.RED));
        sword.setItemMeta(swordMeta);
        player.getInventory().setItem(0, sword);

        // Netherite Hoe - Quake Gun
        ItemStack quakeGun = new ItemStack(Material.NETHERITE_HOE);
        ItemMeta gunMeta = quakeGun.getItemMeta();
        gunMeta.displayName(Component.text("Quake Gun", NamedTextColor.GOLD));
        gunMeta.lore(List.of(
                Component.text("Right-click to fire!", NamedTextColor.GRAY),
                Component.text("Cooldown: " + (configManager.getQuakeGunCooldown() / 20.0) + "s", NamedTextColor.DARK_GRAY)
        ));
        quakeGun.setItemMeta(gunMeta);
        player.getInventory().setItem(1, quakeGun);
    }

    public void onHiderCaught(Player hider, Player seeker) {
        if (state != GameState.HUNTING) return;
        if (getRole(hider.getUniqueId()) != PlayerRole.HIDER) return;

        // Convert hider to seeker (infection)
        playerRoles.put(hider.getUniqueId(), PlayerRole.SEEKER);

        // Restore scale
        restoreScale(hider);

        // Restore skin
        PlayerProfile original = originalProfiles.get(hider.getUniqueId());
        if (original != null) {
            hider.setPlayerProfile(original);
        }

        // Unfreeze
        frozenPlayers.remove(hider.getUniqueId());

        // Show to all hiders again
        for (Map.Entry<UUID, PlayerRole> entry : playerRoles.entrySet()) {
            if (entry.getValue() == PlayerRole.HIDER) {
                Player otherHider = Bukkit.getPlayer(entry.getKey());
                if (otherHider != null) {
                    otherHider.showPlayer(plugin, hider);
                    hider.showPlayer(plugin, otherHider);
                }
            }
        }

        // Give seeker loadout
        giveSeekerLoadout(hider);

        hider.showTitle(Title.title(
                Component.text("CAUGHT!", NamedTextColor.RED),
                Component.text("You are now a Seeker!", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));

        broadcastGame(Component.text(hider.getName() + " was caught! ", NamedTextColor.RED)
                .append(Component.text(getHiderCount() + " hiders remaining.", NamedTextColor.YELLOW)));

        // Check if all hiders are caught
        if (getHiderCount() == 0) {
            endGame(false); // Seekers win
        }
    }

    public void endGame(boolean hidersWin) {
        if (state == GameState.REVEAL || state == GameState.INACTIVE) return;

        state = GameState.REVEAL;
        if (gameTickTask != null) {
            gameTickTask.cancel();
            gameTickTask = null;
        }

        if (hidersWin) {
            broadcastGame(Component.text("Hiders WIN! Time ran out!", NamedTextColor.GREEN));
        } else {
            broadcastGame(Component.text("Seekers WIN! All hiders caught!", NamedTextColor.RED));
        }

        // Spawn ghost entities at hider positions
        spawnRevealGhosts();

        broadcastGame(Component.text("Reveal phase — 30 seconds to see hiding spots!", NamedTextColor.AQUA));

        // After 30s, cleanup and return to lobby
        revealTask = Bukkit.getScheduler().runTaskLater(plugin, this::cleanupGame, 30 * 20L);
    }

    private void spawnRevealGhosts() {
        for (Map.Entry<UUID, Location> entry : lastHiderLocations.entrySet()) {
            Location loc = entry.getValue();
            if (loc == null || loc.getWorld() == null) continue;

            ArmorStand ghost = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
                as.setVisible(true);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setGlowing(true);
                as.setSmall(true);
                as.setCustomNameVisible(true);
                Player p = Bukkit.getPlayer(entry.getKey());
                String name = p != null ? p.getName() : "Unknown";
                as.customName(Component.text(name + "'s spot", NamedTextColor.AQUA));
            });
            revealEntities.add(ghost);
        }
    }

    private void cleanupGame() {
        state = GameState.INACTIVE;

        // Remove reveal entities
        for (ArmorStand as : revealEntities) {
            as.remove();
        }
        revealEntities.clear();

        // Restore all players
        for (UUID uuid : playerRoles.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                restorePlayer(p);
            }
        }

        playerRoles.clear();
        originalProfiles.clear();
        lastHiderLocations.clear();
        frozenPlayers.clear();
        currentMapId = null;

        scoreboardManager.clearAll();
        restoreNametags();

        // Teleport everyone back to lobby and auto-restart
        Location lobby = configManager.getLobbySpawn();
        List<Player> chameleonPlayers = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isInChameleonWorld(p)) {
                chameleonPlayers.add(p);
                if (lobby != null) {
                    p.teleport(lobby);
                }
            }
        }

        // Re-add players to lobby with voting paper after a short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : chameleonPlayers) {
                if (p.isOnline() && isInChameleonWorld(p)) {
                    plugin.getLobbyManager().addPlayer(p);
                }
            }
        }, 40L); // 2 second delay before new lobby starts
    }

    public void restorePlayer(Player player) {
        // Clean up free-cam if active
        plugin.getHiderItemListener().cleanupFreeCam(player);

        restoreScale(player);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);

        // Clear camo armor
        plugin.getCamoManager().clearCamo(player);

        // Restore skin
        PlayerProfile original = originalProfiles.get(player.getUniqueId());
        if (original != null) {
            player.setPlayerProfile(original);
        }

        // Show player to everyone
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
            player.showPlayer(plugin, other);
        }

        // Clear effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Restore pose
        player.setSwimming(false);
        if (player.getVehicle() instanceof ArmorStand as && as.hasMetadata("chameleon_seat")) {
            as.remove();
        }
        player.removeMetadata("chameleon_pose", plugin);

        frozenPlayers.remove(player.getUniqueId());
        scoreboardManager.removeScoreboard(player);
    }

    private void hideNametags() {
        Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team hideTeam = mainBoard.getTeam("cham_hidden");
        if (hideTeam == null) {
            hideTeam = mainBoard.registerNewTeam("cham_hidden");
        }
        hideTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        for (UUID uuid : playerRoles.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                hideTeam.addPlayer(p);
            }
        }
    }

    private void restoreNametags() {
        Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team hideTeam = mainBoard.getTeam("cham_hidden");
        if (hideTeam != null) {
            hideTeam.unregister();
        }
    }

    public void forceStop() {
        if (gameTickTask != null) gameTickTask.cancel();
        if (revealTask != null) revealTask.cancel();
        cleanupGame();
    }

    public void handleDisconnect(Player player) {
        if (!playerRoles.containsKey(player.getUniqueId())) return;
        restoreScale(player);
        PlayerProfile original = originalProfiles.get(player.getUniqueId());
        if (original != null) {
            player.setPlayerProfile(original);
        }
        playerRoles.remove(player.getUniqueId());
        originalProfiles.remove(player.getUniqueId());
        frozenPlayers.remove(player.getUniqueId());

        if (state == GameState.HUNTING && getHiderCount() == 0) {
            endGame(false);
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

    // --- Utility ---

    public void shrinkPlayer(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.SCALE);
        if (attr != null) attr.setBaseValue(configManager.getScaleFactor());
    }

    private void restoreScale(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.SCALE);
        if (attr != null) attr.setBaseValue(1.0);
    }

    public boolean isInChameleonWorld(Player player) {
        return player.getWorld().getName().equalsIgnoreCase(configManager.getWorldName());
    }

    private void broadcastGame(Component message) {
        Component full = Component.text("[Chameleon] ", NamedTextColor.GREEN).append(message);
        for (UUID uuid : playerRoles.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(full);
        }
    }

    // --- Getters ---

    public boolean isGameActive() { return state != GameState.INACTIVE; }
    public GameState getState() { return state; }
    public String getCurrentMapId() { return currentMapId; }
    public int getTimeRemaining() { return timeRemaining; }
    public PlayerRole getRole(UUID uuid) { return playerRoles.getOrDefault(uuid, null); }
    public boolean isParticipant(UUID uuid) { return playerRoles.containsKey(uuid); }
    public boolean isFrozen(UUID uuid) { return frozenPlayers.contains(uuid); }

    public void toggleFreeze(Player player) {
        if (frozenPlayers.contains(player.getUniqueId())) {
            frozenPlayers.remove(player.getUniqueId());
            player.sendMessage(Component.text("[Chameleon] Position unlocked!", NamedTextColor.YELLOW));
        } else {
            frozenPlayers.add(player.getUniqueId());
            player.sendMessage(Component.text("[Chameleon] Position locked! You can't move.", NamedTextColor.GOLD));
        }
    }

    public int getHiderCount() {
        return (int) playerRoles.values().stream().filter(r -> r == PlayerRole.HIDER).count();
    }

    public int getSeekerCount() {
        return (int) playerRoles.values().stream().filter(r -> r == PlayerRole.SEEKER).count();
    }

    public void openSkinMenu(Player player) {
        if (currentMapId == null) return;
        MapData mapData = configManager.getMap(currentMapId);

        List<SkinEntry> skins = mapData != null ? mapData.skins : List.of();
        // Need room for skins + color picker button
        int needed = skins.size() + 1;
        int size = Math.min(54, ((needed + 8) / 9) * 9);
        if (size < 9) size = 9;

        org.bukkit.inventory.Inventory gui = Bukkit.createInventory(null, size,
                Component.text("Camo Skin Selection", NamedTextColor.DARK_GREEN));

        for (int i = 0; i < skins.size() && i < size - 1; i++) {
            SkinEntry entry = skins.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();

            // Apply cached texture to the head
            CachedSkin cached = skinCache.getSkin(entry.uuid());
            if (cached != null) {
                PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), entry.name());
                profile.getProperties().add(new ProfileProperty("textures", cached.textureValue(), cached.textureSignature()));
                meta.setPlayerProfile(profile);
            }

            meta.displayName(Component.text(entry.name(), NamedTextColor.GREEN));
            meta.lore(List.of(
                    Component.text("Click to apply this camo", NamedTextColor.GRAY),
                    Component.text("UUID: " + entry.uuid(), NamedTextColor.DARK_GRAY)
            ));
            head.setItemMeta(meta);
            gui.setItem(i, head);
        }

        // Color Picker button in last slot
        ItemStack colorBtn = new ItemStack(Material.PAINTING);
        ItemMeta colorMeta = colorBtn.getItemMeta();
        colorMeta.displayName(Component.text("Color Picker", NamedTextColor.LIGHT_PURPLE));
        colorMeta.lore(List.of(
                Component.text("Click to open RGB color picker", NamedTextColor.GRAY),
                Component.text("Paint yourself any color!", NamedTextColor.YELLOW)
        ));
        colorBtn.setItemMeta(colorMeta);
        gui.setItem(size - 1, colorBtn);

        player.openInventory(gui);
    }
}

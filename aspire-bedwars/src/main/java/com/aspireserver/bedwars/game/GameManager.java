package com.aspireserver.bedwars.game;

import com.aspireserver.bedwars.AspireBedwars;
import com.aspireserver.bedwars.team.BedwarsTeam;
import com.aspireserver.bedwars.team.TeamColor;
import com.aspireserver.bedwars.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

public class GameManager {

    private final AspireBedwars plugin;

    private GameState state = GameState.WAITING;
    private final Set<UUID> lobbyPlayers = new HashSet<>();
    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private final Map<UUID, Long> disconnectDeadline = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();
    private final Set<UUID> invulnerable = new HashSet<>();
    // Players whose death is currently being processed — blocks re-entrant death handling
    // (e.g. repeated VOID damage ticks while falling below the world).
    private final Set<UUID> pendingDeath = new HashSet<>();
    // Ender chest contents captured at match start, restored on reset so bedwars
    // never leaks into a player's global (e.g. SMP) ender chest.
    private final Map<UUID, ItemStack[]> enderSnapshots = new HashMap<>();
    // Blocks overwritten by auto-placed beds, restored on reset.
    private final Map<Location, BlockData> bedBlockOriginals = new HashMap<>();

    private BukkitTask countdownTask;
    private BukkitTask fieldTask;
    private final List<BukkitTask> timelineTasks = new ArrayList<>();
    private long matchStartMillis;

    public GameManager(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    public GameState getState() { return state; }
    public boolean isRunning() { return state == GameState.RUNNING || state == GameState.SUDDEN_DEATH; }
    public long getMatchStartMillis() { return matchStartMillis; }

    public PlayerData getPlayerData(UUID uuid) { return playerData.get(uuid); }
    public boolean isInvulnerable(UUID uuid) { return invulnerable.contains(uuid); }
    public boolean isPendingDeath(UUID uuid) { return pendingDeath.contains(uuid); }
    public Set<UUID> getLobbyPlayers() { return lobbyPlayers; }

    public boolean isInBedwarsWorld(Player player) {
        return player.getWorld().getName().equalsIgnoreCase(plugin.getSetupConfig().getWorldName());
    }

    // ---------------- Lobby ----------------
    public void joinLobby(Player player) {
        if (state == GameState.RUNNING || state == GameState.SUDDEN_DEATH || state == GameState.ENDING) {
            // Reconnect grace handled elsewhere; late joiners spectate
            plugin.getSpectatorManager().makeSpectator(player);
            return;
        }
        lobbyPlayers.add(player.getUniqueId());
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        giveLobbyItems(player);
        Location lobby = plugin.getSetupConfig().getLobbySpawn();
        if (lobby != null) player.teleport(lobby);

        broadcast(Component.text(player.getName() + " joined ", NamedTextColor.GRAY)
                .append(Component.text("(" + lobbyPlayers.size() + "/" + maxPlayers() + ")", NamedTextColor.YELLOW)));

        if (state == GameState.WAITING && lobbyPlayers.size() >= plugin.getSetupConfig().getMinPlayers()) {
            startCountdown();
        }
    }

    public void leaveLobby(Player player) {
        lobbyPlayers.remove(player.getUniqueId());
        plugin.getTeamManager().getPreferences().remove(player.getUniqueId());
        if (state == GameState.COUNTDOWN && lobbyPlayers.size() < plugin.getSetupConfig().getMinPlayers()) {
            cancelCountdown();
        }
    }

    private void giveLobbyItems(Player player) {
        player.getInventory().setItem(0, new ItemBuilder(Material.COMPASS)
                .name("Team Selector", NamedTextColor.GREEN).build());
        player.getInventory().setItem(8, new ItemBuilder(Material.RED_BED)
                .name("Leave", NamedTextColor.RED).build());
    }

    private int maxPlayers() {
        return plugin.getSetupConfig().getMaxTeams() * plugin.getSetupConfig().getTeamSize();
    }

    private void startCountdown() {
        state = GameState.COUNTDOWN;
        final int[] time = { plugin.getSetupConfig().getLobbyCountdown() };
        countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (lobbyPlayers.size() < plugin.getSetupConfig().getMinPlayers()) {
                cancelCountdown();
                return;
            }
            if (time[0] <= 0) {
                cancelCountdown();
                startMatch();
                return;
            }
            if (time[0] <= 5 || time[0] == 10 || time[0] % 10 == 0) {
                for (UUID uuid : lobbyPlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.sendActionBar(Component.text("Starting in " + time[0] + "s", NamedTextColor.YELLOW));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                    }
                }
            }
            time[0]--;
        }, 20L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (state == GameState.COUNTDOWN) state = GameState.WAITING;
    }

    // ---------------- Match start ----------------
    public boolean startMatch() {
        Set<TeamColor> configured = plugin.getSetupConfig().configuredTeams();
        if (configured.isEmpty()) {
            broadcast(Component.text("Cannot start: no teams configured. Use /bw sb.", NamedTextColor.RED));
            state = GameState.WAITING;
            return false;
        }
        cancelCountdown();
        state = GameState.RUNNING;
        matchStartMillis = System.currentTimeMillis();

        plugin.getTeamManager().initTeams(configured);
        plugin.getArenaReset().beginMatch();

        List<Player> players = new ArrayList<>();
        for (UUID uuid : lobbyPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) players.add(p);
        }
        plugin.getTeamManager().balanceAndAssign(players, plugin.getSetupConfig().getTeamSize());

        // Sync bed alive state from configured beds
        for (BedwarsTeam team : plugin.getTeamManager().getTeams()) {
            team.setSpawn(plugin.getSetupConfig().getTeamSpawns().get(team.getColor()));
            team.setBed(plugin.getSetupConfig().getTeamBeds().get(team.getColor()));
            team.setBedAlive(team.getBed() != null);
        }
        placeBeds();

        plugin.getChatManager().setGameRunning(true);
        enderSnapshots.clear();
        for (Player p : players) {
            // Snapshot the global ender chest so bedwars usage is reverted on reset.
            enderSnapshots.put(p.getUniqueId(), cloneContents(p.getEnderChest().getContents()));
            p.getEnderChest().clear();
            PlayerData data = new PlayerData(p.getUniqueId());
            playerData.put(p.getUniqueId(), data);
            plugin.getStatsManager().ensurePlayer(p.getUniqueId(), p.getName());
            BedwarsTeam team = plugin.getTeamManager().getTeam(p.getUniqueId());
            plugin.getChatManager().setTeam(p.getUniqueId(), team != null ? team.getColor() : null);
            spawnPlayer(p, true);
        }

        plugin.getGeneratorManager().start();
        plugin.getNpcManager().spawnAll();
        plugin.getScoreboardUI().start();
        scheduleTimeline();
        startFieldTask();

        broadcast(Component.text("The game has started! Protect your bed!", NamedTextColor.GREEN));
        return true;
    }

    private final Set<UUID> nearEnemyBed = new HashSet<>();

    private void startFieldTask() {
        fieldTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!isRunning()) return;
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (!isInBedwarsWorld(p)) continue;
                if (plugin.getSpectatorManager().isSpectator(p.getUniqueId())) continue;
                BedwarsTeam own = plugin.getTeamManager().getTeam(p.getUniqueId());
                if (own == null) continue;

                // Void fallback: on 1.8/ViaVersion clients the VOID damage event can be
                // unreliable. If a live participant is below the world floor, kill them once.
                if (p.getLocation().getY() < p.getWorld().getMinHeight() - 2
                        && !pendingDeath.contains(p.getUniqueId())) {
                    handleDeath(p, null);
                    continue;
                }

                // Heal pool near own bed
                if (own.hasHealPool() && own.getBed() != null && own.getBed().getWorld() != null
                        && p.getWorld().equals(own.getBed().getWorld())
                        && p.getLocation().distanceSquared(own.getBed()) <= 49) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, true, false));
                }

                // Enemy trap proximity
                boolean nearAny = false;
                for (BedwarsTeam enemy : plugin.getTeamManager().getTeams()) {
                    if (enemy == own || enemy.getBed() == null || enemy.getBed().getWorld() == null) continue;
                    if (!p.getWorld().equals(enemy.getBed().getWorld())) continue;
                    if (p.getLocation().distanceSquared(enemy.getBed()) <= 49) {
                        nearAny = true;
                        if (!nearEnemyBed.contains(p.getUniqueId())) {
                            triggerTraps(enemy, p);
                        }
                    }
                }
                if (nearAny) nearEnemyBed.add(p.getUniqueId());
                else nearEnemyBed.remove(p.getUniqueId());
            }
        }, 20L, 20L);
    }

    private void triggerTraps(BedwarsTeam defender, Player intruder) {
        if (defender.getPurchasedTraps().isEmpty()) return;
        boolean fired = false;
        if (defender.getPurchasedTraps().remove("blindness")) {
            intruder.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
            intruder.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
            fired = true;
        }
        if (defender.getPurchasedTraps().remove("alarm")) {
            intruder.removePotionEffect(PotionEffectType.INVISIBILITY);
            fired = true;
        }
        if (fired) {
            for (UUID uuid : defender.getMembers()) {
                Player d = Bukkit.getPlayer(uuid);
                if (d != null) {
                    d.showTitle(Title.title(Component.text("TRAP TRIGGERED!", NamedTextColor.RED),
                            Component.text(intruder.getName() + " entered your base!", NamedTextColor.YELLOW),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
                    d.playSound(d.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
                }
            }
        }
    }

    private void scheduleTimeline() {
        int t2 = plugin.getSetupConfig().getDiamondTier2Min();
        int t3 = t2 + plugin.getSetupConfig().getDiamondTier3Min();
        int bedDestroy = t3 + plugin.getSetupConfig().getBedDestructionMin();
        int sudden = bedDestroy + plugin.getSetupConfig().getSuddenDeathMin();
        int gameOver = sudden + plugin.getSetupConfig().getGameOverMin();

        timelineTasks.add(delayMinutes(t2, () -> {
            plugin.getGeneratorManager().setPublicTier(2);
            broadcastTitle("Generators upgraded!", "Diamond & Emerald: Tier II", NamedTextColor.AQUA);
        }));
        timelineTasks.add(delayMinutes(t3, () -> {
            plugin.getGeneratorManager().setPublicTier(3);
            broadcastTitle("Generators upgraded!", "Diamond & Emerald: Tier III", NamedTextColor.AQUA);
        }));
        timelineTasks.add(delayMinutes(bedDestroy, () -> {
            for (BedwarsTeam team : plugin.getTeamManager().getTeams()) {
                if (team.isBedAlive()) destroyBed(team, null, true);
            }
            broadcastTitle("BED DESTRUCTION", "All beds have been destroyed!", NamedTextColor.RED);
        }));
        timelineTasks.add(delayMinutes(sudden, this::startSuddenDeath));
        timelineTasks.add(delayMinutes(gameOver, this::hardGameOver));
    }

    private BukkitTask delayMinutes(int minutes, Runnable r) {
        return plugin.getServer().getScheduler().runTaskLater(plugin, r, minutes * 60L * 20L);
    }

    // ---------------- Spawning / gear ----------------
    public void spawnPlayer(Player player, boolean fresh) {
        BedwarsTeam team = plugin.getTeamManager().getTeam(player.getUniqueId());
        if (team == null) return;
        pendingDeath.remove(player.getUniqueId());
        Location spawn = team.getSpawn();
        if (spawn != null) player.teleport(spawn);
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        for (PotionEffect e : player.getActivePotionEffects()) player.removePotionEffect(e.getType());

        giveBaseInventory(player);
        applyArmor(player);
        applyTools(player);
        applyTeamUpgrades(team);

        // Brief spawn invulnerability
        invulnerable.add(player.getUniqueId());
        int invulnTicks = plugin.getSetupConfig().getRespawnInvulnSeconds() * 20;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> invulnerable.remove(player.getUniqueId()), invulnTicks);
    }

    /**
     * Physically places a team-colored bed at each configured bed location so beds
     * are always visible in-game, regardless of whether the map was pre-built.
     * The head faces toward the team spawn when known. Overwritten blocks are
     * captured so they can be restored on reset.
     */
    private void placeBeds() {
        bedBlockOriginals.clear();
        for (BedwarsTeam team : plugin.getTeamManager().getTeams()) {
            Location bedLoc = team.getBed();
            if (bedLoc == null || bedLoc.getWorld() == null) continue;
            Block foot = bedLoc.getBlock();
            BlockFace facing = bedFacing(bedLoc, team.getSpawn());
            Block head = foot.getRelative(facing);

            bedBlockOriginals.put(foot.getLocation().clone(), foot.getBlockData().clone());
            bedBlockOriginals.put(head.getLocation().clone(), head.getBlockData().clone());

            Bed footData = (Bed) team.getColor().bed().createBlockData();
            footData.setPart(Bed.Part.FOOT);
            footData.setFacing(facing);
            Bed headData = (Bed) team.getColor().bed().createBlockData();
            headData.setPart(Bed.Part.HEAD);
            headData.setFacing(facing);

            foot.setBlockData(footData, false);
            head.setBlockData(headData, false);
        }
    }

    private BlockFace bedFacing(Location bed, Location spawn) {
        if (spawn == null || spawn.getWorld() == null || !spawn.getWorld().equals(bed.getWorld())) {
            return BlockFace.NORTH;
        }
        double dx = spawn.getX() - bed.getX();
        double dz = spawn.getZ() - bed.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private void restoreBeds() {
        for (Map.Entry<Location, BlockData> e : bedBlockOriginals.entrySet()) {
            Location loc = e.getKey();
            if (loc.getWorld() == null) continue;
            loc.getBlock().setBlockData(e.getValue(), false);
        }
        bedBlockOriginals.clear();
    }

    private void giveBaseInventory(Player player) {
        player.getInventory().clear();
        for (com.aspireserver.bedwars.config.LoadoutItem item : plugin.getSetupConfig().getLoadout()) {
            ItemBuilder b = new ItemBuilder(item.material, item.amount);
            if (item.unbreakable) b.unbreakable();
            if (isSwordMaterial(item.material)) {
                placeAtSlot(player, plugin.getSetupConfig().getHotbarSlot("sword"), b.build());
            } else {
                player.getInventory().addItem(b.build());
            }
        }
    }

    public static boolean isSwordMaterial(Material mat) {
        return mat == Material.WOODEN_SWORD || mat == Material.STONE_SWORD
                || mat == Material.GOLDEN_SWORD || mat == Material.IRON_SWORD
                || mat == Material.DIAMOND_SWORD || mat == Material.NETHERITE_SWORD;
    }

    public void applyArmor(Player player) {
        BedwarsTeam team = plugin.getTeamManager().getTeam(player.getUniqueId());
        if (team == null) return;
        PlayerData data = playerData.get(player.getUniqueId());
        int tier = data != null ? data.armorTier : 0;

        ItemStack helmet = new ItemBuilder(Material.LEATHER_HELMET).leatherColor(team.getColor().armorColor()).unbreakable().build();
        ItemStack chest = new ItemBuilder(Material.LEATHER_CHESTPLATE).leatherColor(team.getColor().armorColor()).unbreakable().build();

        Material legMat, bootMat;
        switch (tier) {
            case 1 -> { legMat = Material.CHAINMAIL_LEGGINGS; bootMat = Material.CHAINMAIL_BOOTS; }
            case 2 -> { legMat = Material.IRON_LEGGINGS; bootMat = Material.IRON_BOOTS; }
            case 3 -> { legMat = Material.DIAMOND_LEGGINGS; bootMat = Material.DIAMOND_BOOTS; }
            default -> { legMat = Material.LEATHER_LEGGINGS; bootMat = Material.LEATHER_BOOTS; }
        }
        ItemBuilder legs = new ItemBuilder(legMat).unbreakable();
        ItemBuilder boots = new ItemBuilder(bootMat).unbreakable();
        if (tier == 0) { legs.leatherColor(team.getColor().armorColor()); boots.leatherColor(team.getColor().armorColor()); }
        if (team.getProtectionLevel() > 0) {
            helmet.addUnsafeEnchantment(Enchantment.PROTECTION, team.getProtectionLevel());
            chest.addUnsafeEnchantment(Enchantment.PROTECTION, team.getProtectionLevel());
        }
        ItemStack legItem = legs.build();
        ItemStack bootItem = boots.build();
        if (team.getProtectionLevel() > 0) {
            legItem.addUnsafeEnchantment(Enchantment.PROTECTION, team.getProtectionLevel());
            bootItem.addUnsafeEnchantment(Enchantment.PROTECTION, team.getProtectionLevel());
        }
        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chest);
        player.getInventory().setLeggings(legItem);
        player.getInventory().setBoots(bootItem);
    }

    public void applyTools(Player player) {
        PlayerData data = playerData.get(player.getUniqueId());
        if (data == null) return;
        // Remove existing tools first
        player.getInventory().remove(Material.WOODEN_PICKAXE);
        player.getInventory().remove(Material.IRON_PICKAXE);
        player.getInventory().remove(Material.GOLDEN_PICKAXE);
        player.getInventory().remove(Material.DIAMOND_PICKAXE);
        player.getInventory().remove(Material.WOODEN_AXE);
        player.getInventory().remove(Material.IRON_AXE);
        player.getInventory().remove(Material.GOLDEN_AXE);
        player.getInventory().remove(Material.DIAMOND_AXE);
        player.getInventory().remove(Material.SHEARS);

        if (data.pickaxeTier > 0) placeAtSlot(player, plugin.getSetupConfig().getHotbarSlot("pickaxe"), pickaxeFor(data.pickaxeTier));
        if (data.axeTier > 0) placeAtSlot(player, plugin.getSetupConfig().getHotbarSlot("axe"), axeFor(data.axeTier));
        if (data.shears) placeAtSlot(player, plugin.getSetupConfig().getHotbarSlot("shears"), new ItemBuilder(Material.SHEARS).unbreakable().build());
    }

    /** Places an item at the given hotbar slot (0-8), relocating any displaced item; falls back to addItem. */
    public void placeAtSlot(Player player, int slot, ItemStack item) {
        var inv = player.getInventory();
        if (slot < 0 || slot > 8) {
            inv.addItem(item);
            return;
        }
        ItemStack existing = inv.getItem(slot);
        inv.setItem(slot, item);
        if (existing != null && existing.getType() != Material.AIR) {
            inv.addItem(existing);
        }
    }

    private ItemStack pickaxeFor(int tier) {
        Material mat = switch (tier) {
            case 1 -> Material.WOODEN_PICKAXE;
            case 2 -> Material.IRON_PICKAXE;
            case 3 -> Material.GOLDEN_PICKAXE;
            default -> Material.DIAMOND_PICKAXE;
        };
        ItemBuilder b = new ItemBuilder(mat).unbreakable().enchant(Enchantment.EFFICIENCY, Math.min(3, tier));
        return b.build();
    }

    private ItemStack axeFor(int tier) {
        Material mat = switch (tier) {
            case 1 -> Material.WOODEN_AXE;
            case 2 -> Material.IRON_AXE;
            case 3 -> Material.GOLDEN_AXE;
            default -> Material.DIAMOND_AXE;
        };
        return new ItemBuilder(mat).unbreakable().enchant(Enchantment.EFFICIENCY, Math.min(3, tier)).build();
    }

    public void applyTeamUpgrades(BedwarsTeam team) {
        int sharp = Math.min(plugin.getSetupConfig().getSharpnessCap(), team.getSharpnessLevel());
        for (UUID uuid : team.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            // re-apply armor to sync protection
            if (p.getGameMode() == GameMode.SURVIVAL) applyArmor(p);
            // sharpness on all swords
            if (sharp > 0) {
                for (ItemStack is : p.getInventory().getContents()) {
                    if (is != null && is.getType().name().endsWith("_SWORD")) {
                        is.addUnsafeEnchantment(Enchantment.SHARPNESS, sharp);
                    }
                }
            }
            // haste
            if (team.getHasteLevel() > 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, Integer.MAX_VALUE, team.getHasteLevel() - 1, false, false));
            }
        }
    }

    // ---------------- Death / respawn ----------------
    public void handleDeath(Player player, Player killer) {
        BedwarsTeam team = plugin.getTeamManager().getTeam(player.getUniqueId());
        if (team == null) return;
        // Guard against re-entrant death handling (void damage fires every tick).
        if (!pendingDeath.add(player.getUniqueId())) return;
        PlayerData data = playerData.get(player.getUniqueId());

        // Immediately lift the player out of any void so they stop falling / glitching.
        player.setFallDistance(0f);
        Location safe = team.getSpawn() != null ? team.getSpawn() : plugin.getSetupConfig().getLobbySpawn();
        if (safe != null) player.teleport(safe);

        boolean finalDeath = !team.isBedAlive();

        // Credit killer
        if (killer != null && !killer.equals(player)) {
            PlayerData kd = playerData.get(killer.getUniqueId());
            if (kd != null) {
                if (finalDeath) { kd.finalKills++; plugin.getStatsManager().increment(killer.getUniqueId(), "final_kills", 1); }
                else { kd.kills++; plugin.getStatsManager().increment(killer.getUniqueId(), "kills", 1); }
            }
        }

        if (finalDeath) {
            broadcast(deathMessage(player, killer, true));
            team.eliminate(player.getUniqueId());
            plugin.getStatsManager().increment(player.getUniqueId(), "losses", 0);
            plugin.getSpectatorManager().makeSpectator(player);
            pendingDeath.remove(player.getUniqueId());
            checkWin();
        } else {
            broadcast(deathMessage(player, killer, false));
            if (data != null) data.downgradeToolsOnDeath();
            startRespawn(player);
        }
    }

    private void startRespawn(Player player) {
        plugin.getSpectatorManager().getSpectators().remove(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear();
        final int[] secs = { plugin.getSetupConfig().getRespawnSeconds() };
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                if (!isRunning()) { cancelRespawn(player.getUniqueId()); return; }
                if (secs[0] <= 0) {
                    cancelRespawn(player.getUniqueId());
                    spawnPlayer(player, false);
                    player.showTitle(Title.title(Component.text("Respawned!", NamedTextColor.GREEN), Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(200))));
                    return;
                }
                player.showTitle(Title.title(Component.text("You died!", NamedTextColor.RED),
                        Component.text("Respawning in " + secs[0] + "s", NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)));
                secs[0]--;
            }
        }, 0L, 20L);
        respawnTasks.put(player.getUniqueId(), task);
    }

    private void cancelRespawn(UUID uuid) {
        BukkitTask t = respawnTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    private Component deathMessage(Player player, Player killer, boolean finalKill) {
        BedwarsTeam team = plugin.getTeamManager().getTeam(player.getUniqueId());
        Component name = Component.text(player.getName(), team != null ? team.getColor().textColor() : NamedTextColor.WHITE);
        if (killer != null && !killer.equals(player)) {
            BedwarsTeam kt = plugin.getTeamManager().getTeam(killer.getUniqueId());
            Component kn = Component.text(killer.getName(), kt != null ? kt.getColor().textColor() : NamedTextColor.WHITE);
            return name.append(Component.text(" was killed by ", NamedTextColor.GRAY)).append(kn)
                    .append(finalKill ? Component.text(". FINAL KILL!", NamedTextColor.RED) : Component.empty());
        }
        return name.append(Component.text(" died" + (finalKill ? ". FINAL KILL!" : ""), NamedTextColor.GRAY));
    }

    // ---------------- Bed ----------------
    public void destroyBed(BedwarsTeam team, Player breaker, boolean auto) {
        if (!team.isBedAlive()) return;
        team.setBedAlive(false);

        Component breakerName = breaker != null
                ? Component.text(breaker.getName(), NamedTextColor.WHITE)
                : Component.text("time", NamedTextColor.GRAY);
        broadcastTitleColored(team);
        broadcast(Component.text("BED DESTRUCTION > ", NamedTextColor.GOLD)
                .append(Component.text(team.getColor().displayName() + " Bed ", team.getColor().textColor()))
                .append(Component.text("was destroyed by ", NamedTextColor.GRAY))
                .append(breakerName));

        if (breaker != null) {
            PlayerData bd = playerData.get(breaker.getUniqueId());
            if (bd != null) bd.bedsBroken++;
            plugin.getStatsManager().increment(breaker.getUniqueId(), "beds_broken", 1);
        }

        // Alert the defending team
        for (UUID uuid : team.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.showTitle(Title.title(Component.text("BED DESTROYED!", NamedTextColor.RED),
                        Component.text("You will no longer respawn!", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
            }
        }
    }

    private void broadcastTitleColored(BedwarsTeam team) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (isInBedwarsWorld(p)) p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.6f, 1f);
        }
    }

    // ---------------- Win / end ----------------
    public void checkWin() {
        if (!isRunning()) return;
        BedwarsTeam survivor = plugin.getTeamManager().soleSurvivor();
        if (survivor != null) endMatch(survivor);
    }

    public void endMatch(BedwarsTeam winner) {
        if (state == GameState.ENDING) return;
        state = GameState.ENDING;
        cancelTimeline();

        if (winner != null) {
            broadcastTitle("GAME OVER", winner.getColor().displayName() + " Team wins!", winner.getColor().textColor());
            broadcast(Component.text(winner.getColor().displayName() + " Team won the game!", winner.getColor().textColor()));
            for (UUID uuid : winner.getMembers()) {
                plugin.getStatsManager().increment(uuid, "wins", 1);
            }
        } else {
            broadcastTitle("GAME OVER", "Draw", NamedTextColor.YELLOW);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, this::fullReset, 20L * 8L);
    }

    private void startSuddenDeath() {
        if (!isRunning()) return;
        state = GameState.SUDDEN_DEATH;
        broadcastTitle("SUDDEN DEATH", "Dragons incoming!", NamedTextColor.DARK_RED);
        plugin.getDragonManager().spawnDragons();
    }

    private void hardGameOver() {
        if (!isRunning()) return;
        // Declare winner by remaining team with most alive members, then most kills
        BedwarsTeam best = null;
        int bestScore = -1;
        for (BedwarsTeam team : plugin.getTeamManager().getTeams()) {
            if (!team.isAlive()) continue;
            int alive = team.aliveCount(uuid -> Bukkit.getPlayer(uuid) != null);
            int kills = 0;
            for (UUID uuid : team.getMembers()) {
                PlayerData d = playerData.get(uuid);
                if (d != null) kills += d.kills + d.finalKills * 2;
            }
            int score = alive * 1000 + kills;
            if (score > bestScore) { bestScore = score; best = team; }
        }
        endMatch(best);
    }

    private void cancelTimeline() {
        for (BukkitTask t : timelineTasks) t.cancel();
        timelineTasks.clear();
    }

    public void fullReset() {
        cancelTimeline();
        cancelCountdown();
        if (fieldTask != null) { fieldTask.cancel(); fieldTask = null; }
        nearEnemyBed.clear();
        for (BukkitTask t : respawnTasks.values()) t.cancel();
        respawnTasks.clear();

        plugin.getGeneratorManager().stop();
        plugin.getNpcManager().despawnAll();
        plugin.getScoreboardUI().stop();
        plugin.getDragonManager().clear();
        restoreBeds();
        plugin.getArenaReset().resetArena();
        plugin.getSpectatorManager().clearAll();
        plugin.getChatManager().clear();

        // Reset players back to lobby
        for (UUID uuid : new HashSet<>(playerData.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && isInBedwarsWorld(p)) {
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
                restoreEnderChest(p);
                for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
                Location lobby = plugin.getSetupConfig().getLobbySpawn();
                if (lobby != null) p.teleport(lobby);
                giveLobbyItems(p);
            }
        }
        // Restore ender chests for any snapshotted player who is now offline/elsewhere.
        for (UUID uuid : new HashSet<>(enderSnapshots.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) restoreEnderChest(p);
        }
        enderSnapshots.clear();

        playerData.clear();
        invulnerable.clear();
        pendingDeath.clear();
        disconnectDeadline.clear();
        for (BedwarsTeam t : plugin.getTeamManager().getTeams()) t.reset();
        plugin.getTeamManager().clear();

        state = GameState.WAITING;
        // Re-evaluate countdown for whoever is still in the lobby world
        lobbyPlayers.removeIf(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            return p == null || !isInBedwarsWorld(p);
        });
        if (lobbyPlayers.size() >= plugin.getSetupConfig().getMinPlayers()) startCountdown();
    }

    private static ItemStack[] cloneContents(ItemStack[] src) {
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) out[i] = src[i] == null ? null : src[i].clone();
        return out;
    }

    private void restoreEnderChest(Player p) {
        ItemStack[] snapshot = enderSnapshots.remove(p.getUniqueId());
        p.getEnderChest().clear();
        if (snapshot != null) p.getEnderChest().setContents(cloneContents(snapshot));
    }

    // ---------------- Disconnect / reconnect ----------------
    public void handleDisconnect(Player player) {
        UUID uuid = player.getUniqueId();
        if (state == GameState.WAITING || state == GameState.COUNTDOWN) {
            leaveLobby(player);
            return;
        }
        if (isRunning() && playerData.containsKey(uuid)) {
            long deadline = System.currentTimeMillis() + plugin.getSetupConfig().getDisconnectGraceSeconds() * 1000L;
            disconnectDeadline.put(uuid, deadline);
            BedwarsTeam team = plugin.getTeamManager().getTeam(uuid);
            if (team != null) {
                broadcast(Component.text(player.getName() + " disconnected — ", NamedTextColor.GRAY)
                        .append(Component.text(plugin.getSetupConfig().getDisconnectGraceSeconds() + "s to reconnect", NamedTextColor.YELLOW)));
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Long dl = disconnectDeadline.get(uuid);
                if (dl == null) return;
                if (Bukkit.getPlayer(uuid) == null && System.currentTimeMillis() >= dl) {
                    // Timeout — treat as final death
                    disconnectDeadline.remove(uuid);
                    if (team != null) {
                        team.eliminate(uuid);
                        broadcast(Component.text(player.getName() + " did not reconnect and was eliminated.", NamedTextColor.RED));
                        checkWin();
                    }
                }
            }, plugin.getSetupConfig().getDisconnectGraceSeconds() * 20L + 20L);
        }
    }

    public void handleReconnect(Player player) {
        UUID uuid = player.getUniqueId();
        if (isRunning() && playerData.containsKey(uuid) && disconnectDeadline.remove(uuid) != null) {
            BedwarsTeam team = plugin.getTeamManager().getTeam(uuid);
            if (team != null && !team.isEliminated(uuid)) {
                plugin.getChatManager().setTeam(uuid, team.getColor());
                plugin.getChatManager().setGameRunning(true);
                spawnPlayer(player, false);
                broadcast(Component.text(player.getName() + " reconnected!", NamedTextColor.GREEN));
                return;
            }
        }
        // Otherwise treat as lobby join / spectator
        joinLobby(player);
    }

    public void leaveLobby(UUID uuid) {
        lobbyPlayers.remove(uuid);
        plugin.getTeamManager().getPreferences().remove(uuid);
        if (state == GameState.COUNTDOWN && lobbyPlayers.size() < plugin.getSetupConfig().getMinPlayers()) {
            cancelCountdown();
        }
    }

    // ---------------- Utility ----------------
    public void broadcast(Component msg) {
        Component full = Component.text("[Bedwars] ", NamedTextColor.GOLD).append(msg);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (isInBedwarsWorld(p)) p.sendMessage(full);
        }
    }

    public void broadcastTitle(String title, String subtitle, NamedTextColor color) {
        Title t = Title.title(Component.text(title, color), Component.text(subtitle, NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500)));
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (isInBedwarsWorld(p)) p.showTitle(t);
        }
    }
}

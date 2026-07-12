package com.aspireserver.bedwars.config;

import com.aspireserver.bedwars.AspireBedwars;
import com.aspireserver.bedwars.generator.GeneratorType;
import com.aspireserver.bedwars.team.TeamColor;
import com.aspireserver.bedwars.util.ItemBuilder;
import com.aspireserver.bedwars.util.LocationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Map development tooling: setup hotbar, visual markers, list/remove/mirror,
 * and pre-save validation.
 */
public class SetupModeManager implements Listener {

    private final AspireBedwars plugin;
    private final Set<UUID> setupPlayers = new HashSet<>();
    private final Map<UUID, TeamColor> selectedTeam = new HashMap<>();
    private final Map<UUID, GeneratorType> selectedGen = new HashMap<>();
    private final List<ArmorStand> markers = new ArrayList<>();
    private boolean markersShown = false;
    private BukkitTask particleTask;

    public SetupModeManager(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    public boolean isInSetup(Player player) { return setupPlayers.contains(player.getUniqueId()); }

    public void toggle(Player player) {
        if (isInSetup(player)) exit(player);
        else enter(player);
    }

    public void enter(Player player) {
        setupPlayers.add(player.getUniqueId());
        selectedTeam.putIfAbsent(player.getUniqueId(), TeamColor.RED);
        selectedGen.putIfAbsent(player.getUniqueId(), GeneratorType.DIAMOND);
        giveHotbar(player);
        player.sendMessage(Component.text("Entered Bedwars setup mode.", NamedTextColor.GREEN));
        startParticles();
    }

    public void exit(Player player) {
        setupPlayers.remove(player.getUniqueId());
        player.getInventory().clear();
        player.sendMessage(Component.text("Exited setup mode.", NamedTextColor.YELLOW));
        if (setupPlayers.isEmpty() && particleTask != null) { particleTask.cancel(); particleTask = null; }
    }

    private void giveHotbar(Player player) {
        player.getInventory().clear();
        TeamColor team = selectedTeam.get(player.getUniqueId());
        GeneratorType gen = selectedGen.get(player.getUniqueId());
        player.getInventory().setItem(0, new ItemBuilder(team.bed()).name("Bed Placer", NamedTextColor.RED)
                .loreLine("Right-click a block to set " + team.displayName() + " bed", NamedTextColor.GRAY).build());
        player.getInventory().setItem(1, new ItemBuilder(Material.ENDER_PEARL).name("Team Spawn Setter", NamedTextColor.GREEN)
                .loreLine("Right-click to set " + team.displayName() + " spawn at your position", NamedTextColor.GRAY).build());
        player.getInventory().setItem(2, new ItemBuilder(gen.material()).name("Generator Placer", NamedTextColor.AQUA)
                .loreLine("Right-click a block to place a " + gen.name() + " generator", NamedTextColor.GRAY).build());
        player.getInventory().setItem(3, new ItemBuilder(Material.EMERALD).name("Shop NPC Placer", NamedTextColor.GREEN).build());
        player.getInventory().setItem(4, new ItemBuilder(Material.DIAMOND).name("Upgrade NPC Placer", NamedTextColor.AQUA).build());
        player.getInventory().setItem(5, new ItemBuilder(Material.BEACON).name("Lobby Spawn Setter", NamedTextColor.YELLOW).build());
        player.getInventory().setItem(7, new ItemBuilder(team.wool()).name("Selected Team: " + team.displayName(), team.textColor())
                .loreLine("Right-click to cycle team", NamedTextColor.GRAY).build());
        player.getInventory().setItem(8, new ItemBuilder(gen.material()).name("Gen Type: " + gen.name(), NamedTextColor.AQUA)
                .loreLine("Right-click to cycle generator type", NamedTextColor.GRAY).build());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isInSetup(player)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        ItemStack hand = event.getItem();
        if (hand == null) return;
        event.setCancelled(true);

        Location blockLoc = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : player.getLocation();
        TeamColor team = selectedTeam.get(player.getUniqueId());
        GeneratorType gen = selectedGen.get(player.getUniqueId());

        switch (hand.getType()) {
            case ENDER_PEARL -> {
                plugin.getSetupConfig().setTeamSpawn(team, player.getLocation());
                plugin.getSetupConfig().save();
                msg(player, team.displayName() + " spawn set.");
            }
            case EMERALD -> {
                if (hand.getItemMeta() != null && named(hand, "Shop NPC Placer")) {
                    plugin.getSetupConfig().addNpc(new NpcPoint(NpcPoint.NpcType.SHOP, player.getLocation()));
                    plugin.getSetupConfig().save();
                    msg(player, "Shop NPC placed.");
                }
            }
            case DIAMOND -> {
                plugin.getSetupConfig().addNpc(new NpcPoint(NpcPoint.NpcType.UPGRADE, player.getLocation()));
                plugin.getSetupConfig().save();
                msg(player, "Upgrade NPC placed.");
            }
            case BEACON -> {
                plugin.getSetupConfig().setLobbySpawn(player.getLocation());
                plugin.getSetupConfig().save();
                msg(player, "Lobby spawn set.");
            }
            default -> {
                if (named(hand, "Bed Placer")) {
                    plugin.getSetupConfig().setTeamBed(team, blockLoc);
                    plugin.getSetupConfig().save();
                    msg(player, team.displayName() + " bed set at " + coords(blockLoc));
                } else if (named(hand, "Generator Placer")) {
                    TeamColor genTeam = gen.isTeamGenerator() ? team : null;
                    plugin.getSetupConfig().addGenerator(new GeneratorPoint(gen, blockLoc, genTeam));
                    plugin.getSetupConfig().save();
                    msg(player, gen.name() + " generator placed" + (genTeam != null ? " (" + genTeam.displayName() + ")" : "") + ".");
                } else if (named(hand, "Selected Team")) {
                    cycleTeam(player);
                } else if (named(hand, "Gen Type")) {
                    cycleGen(player);
                }
            }
        }
        if (markersShown) refreshMarkers();
    }

    private boolean named(ItemStack item, String contains) {
        if (item.getItemMeta() == null || item.getItemMeta().displayName() == null) return false;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
        return name.contains(contains);
    }

    private void cycleTeam(Player player) {
        TeamColor cur = selectedTeam.get(player.getUniqueId());
        TeamColor[] all = TeamColor.values();
        TeamColor next = all[(cur.ordinal() + 1) % all.length];
        selectedTeam.put(player.getUniqueId(), next);
        giveHotbar(player);
        msg(player, "Selected team: " + next.displayName());
    }

    private void cycleGen(Player player) {
        GeneratorType cur = selectedGen.get(player.getUniqueId());
        GeneratorType[] all = GeneratorType.values();
        GeneratorType next = all[(cur.ordinal() + 1) % all.length];
        selectedGen.put(player.getUniqueId(), next);
        giveHotbar(player);
        msg(player, "Gen type: " + next.name());
    }

    // ---- Markers ----
    public void showMarkers() { markersShown = true; refreshMarkers(); }
    public void hideMarkers() { markersShown = false; clearMarkers(); }

    private void clearMarkers() {
        for (ArmorStand as : markers) if (as != null && !as.isDead()) as.remove();
        markers.clear();
    }

    private void refreshMarkers() {
        clearMarkers();
        SetupConfigManager cfg = plugin.getSetupConfig();
        if (cfg.getLobbySpawn() != null) marker(cfg.getLobbySpawn(), "\u00A7eLobby Spawn");
        for (var e : cfg.getTeamSpawns().entrySet()) marker(e.getValue(), "\u00A7f" + e.getKey().displayName() + " Spawn");
        for (var e : cfg.getTeamBeds().entrySet()) marker(e.getValue().clone().add(0.5, 0.5, 0.5), "\u00A7c" + e.getKey().displayName() + " Bed");
        for (GeneratorPoint g : cfg.getGenerators())
            marker(g.location.clone().add(0.5, 1, 0.5), "\u00A7b" + g.type.name() + (g.team != null ? " (" + g.team.displayName() + ")" : ""));
        for (NpcPoint n : cfg.getNpcs()) marker(n.location, "\u00A7a" + n.type.name() + " NPC");
    }

    private void marker(Location loc, String label) {
        if (loc == null || loc.getWorld() == null) return;
        ArmorStand as = loc.getWorld().spawn(loc.clone().add(0, 1.4, 0), ArmorStand.class, a -> {
            a.setVisible(false);
            a.setGravity(false);
            a.setMarker(true);
            a.setInvulnerable(true);
            a.setCustomNameVisible(true);
        });
        as.customName(Component.text(label));
        markers.add(as);
    }

    private void startParticles() {
        if (particleTask != null) return;
        particleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!markersShown) return;
            SetupConfigManager cfg = plugin.getSetupConfig();
            for (GeneratorPoint g : cfg.getGenerators()) spawnParticle(g.location.clone().add(0.5, 0.5, 0.5));
            for (var e : cfg.getTeamBeds().entrySet()) spawnParticle(e.getValue().clone().add(0.5, 0.5, 0.5));
        }, 10L, 10L);
    }

    private void spawnParticle(Location loc) {
        if (loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 6, 0.3, 0.3, 0.3, 0);
    }

    // ---- List / remove ----
    public List<String> listPoints() {
        List<String> out = new ArrayList<>();
        SetupConfigManager cfg = plugin.getSetupConfig();
        int id = 0;
        out.add("lobby: " + (cfg.getLobbySpawn() != null ? coords(cfg.getLobbySpawn()) : "unset"));
        for (var e : cfg.getTeamSpawns().entrySet()) out.add("[" + (id++) + "] spawn " + e.getKey() + " " + coords(e.getValue()));
        for (var e : cfg.getTeamBeds().entrySet()) out.add("[" + (id++) + "] bed " + e.getKey() + " " + coords(e.getValue()));
        for (GeneratorPoint g : cfg.getGenerators()) out.add("[" + (id++) + "] gen " + g.type + (g.team != null ? " " + g.team : "") + " " + coords(g.location));
        for (NpcPoint n : cfg.getNpcs()) out.add("[" + (id++) + "] npc " + n.type + " " + coords(n.location));
        return out;
    }

    /** Remove by the flat index shown in {@link #listPoints()} (team spawns/beds first, then gens, then npcs). */
    public boolean removePoint(int index) {
        SetupConfigManager cfg = plugin.getSetupConfig();
        int id = 0;
        List<TeamColor> spawnKeys = new ArrayList<>(cfg.getTeamSpawns().keySet());
        for (TeamColor c : spawnKeys) { if (id++ == index) { cfg.removeTeamSpawn(c); cfg.save(); return true; } }
        List<TeamColor> bedKeys = new ArrayList<>(cfg.getTeamBeds().keySet());
        for (TeamColor c : bedKeys) { if (id++ == index) { cfg.removeTeamBed(c); cfg.save(); return true; } }
        for (int i = 0; i < cfg.getGenerators().size(); i++) { if (id++ == index) { cfg.getGenerators().remove(i); cfg.save(); return true; } }
        for (int i = 0; i < cfg.getNpcs().size(); i++) { if (id++ == index) { cfg.getNpcs().remove(i); cfg.save(); return true; } }
        return false;
    }

    /**
     * Point-reflect the source team's bed, spawn, and team generators about the
     * lobby spawn to auto-generate the target team's equivalents. Ideal for
     * symmetric maps where teams sit opposite through the center.
     */
    public boolean mirror(TeamColor source, TeamColor target) {
        SetupConfigManager cfg = plugin.getSetupConfig();
        Location center = cfg.getLobbySpawn();
        if (center == null) return false;
        Location srcSpawn = cfg.getTeamSpawns().get(source);
        Location srcBed = cfg.getTeamBeds().get(source);
        if (srcSpawn == null || srcBed == null) return false;

        cfg.setTeamSpawn(target, reflect(srcSpawn, center, true));
        cfg.setTeamBed(target, reflect(srcBed, center, false));
        for (GeneratorPoint g : new ArrayList<>(cfg.getGenerators())) {
            if (g.team == source) {
                cfg.addGenerator(new GeneratorPoint(g.type, reflect(g.location, center, false), target));
            }
        }
        cfg.save();
        return true;
    }

    private Location reflect(Location p, Location center, boolean rotate) {
        double x = 2 * center.getX() - p.getX();
        double z = 2 * center.getZ() - p.getZ();
        Location out = new Location(p.getWorld(), x, p.getY(), z, p.getYaw(), p.getPitch());
        if (rotate) out.setYaw(p.getYaw() + 180f);
        return out;
    }

    private void msg(Player player, String s) {
        player.sendMessage(Component.text("[Setup] ", NamedTextColor.AQUA).append(Component.text(s, NamedTextColor.WHITE)));
    }

    private String coords(Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}

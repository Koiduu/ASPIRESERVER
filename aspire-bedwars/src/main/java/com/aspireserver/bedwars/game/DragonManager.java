package com.aspireserver.bedwars.game;

import com.aspireserver.bedwars.AspireBedwars;
import com.aspireserver.bedwars.team.BedwarsTeam;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight Sudden Death: one EnderDragon per surviving team that hunts the
 * nearest enemy (never its own team). Manual targeting since EnderDragon has no
 * vanilla setTarget.
 */
public class DragonManager {

    private final AspireBedwars plugin;
    private final List<TeamDragon> dragons = new ArrayList<>();
    private BukkitTask task;

    private record TeamDragon(EnderDragon dragon, BedwarsTeam team) {}

    public DragonManager(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    public void spawnDragons() {
        clear();
        World world = plugin.getServer().getWorld(plugin.getSetupConfig().getWorldName());
        if (world == null) return;

        for (BedwarsTeam team : plugin.getTeamManager().getTeams()) {
            if (!team.isAlive()) continue;
            Location base = team.getSpawn();
            if (base == null) continue;
            Location spawnLoc = base.clone().add(0, 12, 0);
            EnderDragon dragon = world.spawn(spawnLoc, EnderDragon.class, d -> {
                d.setPhase(EnderDragon.Phase.CIRCLING);
                d.setCustomNameVisible(true);
                d.customName(Component.text(team.getColor().displayName() + " Dragon", team.getColor().textColor()));
                d.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(200);
                d.setHealth(200);
                d.setRemoveWhenFarAway(false);
            });
            dragons.add(new TeamDragon(dragon, team));
        }

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    private void tick() {
        for (TeamDragon td : dragons) {
            EnderDragon dragon = td.dragon();
            if (dragon == null || dragon.isDead()) continue;
            Player target = nearestEnemy(dragon.getLocation(), td.team());
            if (target == null) continue;

            Location dl = dragon.getLocation();
            Vector dir = target.getLocation().add(0, 1, 0).toVector().subtract(dl.toVector());
            if (dir.lengthSquared() > 0.01) {
                dragon.setVelocity(dir.normalize().multiply(0.8));
            }
            if (dl.distanceSquared(target.getLocation()) <= 16 && !plugin.getGameManager().isInvulnerable(target.getUniqueId())) {
                target.damage(6.0, dragon);
                target.setVelocity(dir.normalize().multiply(1.2).setY(0.5));
            }
        }
    }

    private Player nearestEnemy(Location from, BedwarsTeam team) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            if (team.isMember(uuid)) continue;
            if (plugin.getSpectatorManager().isSpectator(uuid)) continue;
            if (!p.getWorld().equals(from.getWorld())) continue;
            BedwarsTeam pt = plugin.getTeamManager().getTeam(uuid);
            if (pt == null || !pt.isAlive()) continue;
            double d = p.getLocation().distanceSquared(from);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    public void clear() {
        if (task != null) { task.cancel(); task = null; }
        for (TeamDragon td : dragons) {
            if (td.dragon() != null && !td.dragon().isDead()) td.dragon().remove();
        }
        dragons.clear();
    }
}

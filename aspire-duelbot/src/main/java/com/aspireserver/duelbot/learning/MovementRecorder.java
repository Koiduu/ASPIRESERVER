package com.aspireserver.duelbot.learning;

import com.aspireserver.duelbot.DuelBotPlugin;
import com.aspireserver.duelbot.DuelBotSettings;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records real players' combat micro-movement and post-knockback recovery each tick,
 * strictly within the configured duel world, into the {@link HumanSampleStore}.
 *
 * <p>Every read here is a Bukkit call on the main thread (this is a scheduled sync task);
 * only the store's own save path runs off-thread on immutable snapshots.</p>
 */
public final class MovementRecorder implements Listener, Runnable {

    private static final double SPRINT_SPEED = 0.2806; // ~ blocks/tick, used to normalise deltas

    private final DuelBotPlugin plugin;
    private final HumanSampleStore store;

    private boolean recording;
    private final Map<UUID, Location> lastPos = new HashMap<>();
    private final Map<UUID, Vector> pendingKb = new HashMap<>(); // victim -> horizontal dir away from attacker
    private final Map<UUID, KbCapture> activeKb = new HashMap<>();

    public MovementRecorder(DuelBotPlugin plugin, HumanSampleStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public boolean isRecording() {
        return recording;
    }

    public void setRecording(boolean recording) {
        this.recording = recording;
        if (!recording) {
            lastPos.clear();
            pendingKb.clear();
            activeKb.clear();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!recording) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (CitizensAPI.getNPCRegistry().isNPC(victim)) return;
        if (!inDuelWorld(victim.getWorld())) return;
        if (!isPvpDamager(event)) return;

        Location attacker = attackerLocation(event);
        Vector away = null;
        if (attacker != null) {
            away = victim.getLocation().toVector().subtract(attacker.toVector());
            away.setY(0);
            if (away.lengthSquared() > 1.0e-6) away.normalize();
            else away = null;
        }
        pendingKb.put(victim.getUniqueId(), away); // captured starting next tick, once KB is applied
    }

    @Override
    public void run() {
        if (!recording) return;
        DuelBotSettings s = plugin.getSettings();

        // Promote pending knockback hits to active recovery captures (velocity now includes the KB impulse).
        if (!pendingKb.isEmpty()) {
            for (Map.Entry<UUID, Vector> e : pendingKb.entrySet()) {
                Player p = plugin.getServer().getPlayer(e.getKey());
                if (p == null || !p.isOnline()) continue;
                activeKb.put(p.getUniqueId(), KbCapture.start(p, e.getValue(), s.kbTraceTicks));
            }
            pendingKb.clear();
        }

        // Advance recovery captures.
        activeKb.entrySet().removeIf(entry -> {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) return true;
            KbCapture cap = entry.getValue();
            cap.record(p.getVelocity());
            if (cap.done()) {
                store.addKbTrace(cap.toTrace());
                return true;
            }
            return false;
        });

        // Per-tick movement recording for players actively dueling in the duel world.
        World world = duelWorld();
        Iterable<? extends Player> players = world != null ? world.getPlayers() : plugin.getServer().getOnlinePlayers();
        for (Player p : players) {
            if (!isRecordable(p)) continue;
            Location cur = p.getLocation();
            Location prev = lastPos.get(p.getUniqueId());
            lastPos.put(p.getUniqueId(), cur.clone());

            Player opp = nearestOpponent(p, s.recordRadius);
            if (opp == null || prev == null || !prev.getWorld().equals(cur.getWorld())) continue;

            double dx = cur.getX() - prev.getX();
            double dz = cur.getZ() - prev.getZ();
            boolean jumping = !p.isOnGround() && p.getVelocity().getY() > 0.08;
            if (Math.abs(dx) < 0.01 && Math.abs(dz) < 0.01 && !jumping) continue; // skip idle ticks

            double fx = opp.getLocation().getX() - cur.getX();
            double fz = opp.getLocation().getZ() - cur.getZ();
            double flen = Math.sqrt(fx * fx + fz * fz);
            if (flen < 1.0e-4) continue;
            fx /= flen;
            fz /= flen;
            double rx = -fz, rz = fx; // right axis

            double forward = clamp((dx * fx + dz * fz) / SPRINT_SPEED, -1.5, 1.5);
            double strafe = clamp((dx * rx + dz * rz) / SPRINT_SPEED, -1.5, 1.5);

            int bucket = MovementSample.bucketOf(flen);
            store.addMovement(bucket, new MovementSample(forward, strafe, jumping, p.isSneaking()));
        }
    }

    private boolean isRecordable(Player p) {
        if (p == null || !p.isOnline() || p.isDead()) return false;
        if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) return false;
        if (CitizensAPI.getNPCRegistry().isNPC(p)) return false;
        return inDuelWorld(p.getWorld());
    }

    private Player nearestOpponent(Player p, double radius) {
        Player best = null;
        double bestSq = radius * radius;
        for (Player o : p.getWorld().getPlayers()) {
            if (o == p || !isRecordable(o)) continue;
            double d = o.getLocation().distanceSquared(p.getLocation());
            if (d < bestSq) {
                bestSq = d;
                best = o;
            }
        }
        return best;
    }

    private boolean isPvpDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) return true;
        if (event.getDamager() instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            return src instanceof Player;
        }
        return false;
    }

    private Location attackerLocation(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player pl) return pl.getLocation();
        if (event.getDamager() instanceof Projectile proj) {
            if (proj.getShooter() instanceof Player pl) return pl.getLocation();
            return proj.getLocation();
        }
        return null;
    }

    private World duelWorld() {
        String name = plugin.getSettings().duelWorld;
        if (name == null || name.isEmpty()) return null;
        return plugin.getServer().getWorld(name);
    }

    private boolean inDuelWorld(World world) {
        String name = plugin.getSettings().duelWorld;
        return name == null || name.isEmpty() || name.equalsIgnoreCase(world.getName());
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Accumulates a player's recovery velocity for a fixed window after a knockback hit. */
    private static final class KbCapture {
        private final double[][] rows;
        private final double alongX, alongZ, sideX, sideZ;
        private final double hMag, vMag;
        private int idx;

        private KbCapture(double[][] rows, double alongX, double alongZ, double sideX, double sideZ,
                          double hMag, double vMag) {
            this.rows = rows;
            this.alongX = alongX;
            this.alongZ = alongZ;
            this.sideX = sideX;
            this.sideZ = sideZ;
            this.hMag = hMag;
            this.vMag = vMag;
        }

        static KbCapture start(Player p, Vector fallbackDir, int ticks) {
            Vector v = p.getVelocity();
            double ax = v.getX(), az = v.getZ();
            double hLen = Math.sqrt(ax * ax + az * az);
            if (hLen < 1.0e-4) {
                if (fallbackDir != null) {
                    ax = fallbackDir.getX();
                    az = fallbackDir.getZ();
                } else {
                    ax = 0;
                    az = 1;
                }
                hLen = Math.sqrt(ax * ax + az * az);
                if (hLen < 1.0e-4) {
                    ax = 0;
                    az = 1;
                    hLen = 1;
                }
            }
            double alongX = ax / hLen, alongZ = az / hLen;
            double sideX = -alongZ, sideZ = alongX;
            return new KbCapture(new double[Math.max(1, ticks)][3], alongX, alongZ, sideX, sideZ,
                    hLen, v.getY());
        }

        void record(Vector v) {
            if (idx >= rows.length) return;
            rows[idx][0] = v.getX() * alongX + v.getZ() * alongZ;
            rows[idx][1] = v.getY();
            rows[idx][2] = v.getX() * sideX + v.getZ() * sideZ;
            idx++;
        }

        boolean done() {
            return idx >= rows.length;
        }

        KbTrace toTrace() {
            return new KbTrace(hMag, vMag, rows);
        }
    }
}

package com.aspireserver.duelbot.npc;

import com.aspireserver.duelbot.DuelBotPlugin;
import com.aspireserver.duelbot.DuelBotSettings;
import com.aspireserver.duelbot.blocks.BlockPlacer;
import com.aspireserver.duelbot.blocks.BlockTapPredictor;
import com.aspireserver.duelbot.blocks.BoxInController;
import com.aspireserver.duelbot.blocks.ClutchDetector;
import com.aspireserver.duelbot.combat.AimController;
import com.aspireserver.duelbot.combat.CritJumpController;
import com.aspireserver.duelbot.combat.StrafeController;
import com.aspireserver.duelbot.combat.SwingController;
import com.aspireserver.duelbot.combat.WTapController;
import com.aspireserver.duelbot.config.DifficultyTier;
import com.aspireserver.duelbot.fsm.BotState;
import com.aspireserver.duelbot.fsm.StateMachine;
import com.aspireserver.duelbot.learning.HumanSampleStore;
import com.aspireserver.duelbot.learning.KbTrace;
import com.aspireserver.duelbot.learning.MovementSample;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.api.util.DataKey;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Citizens trait that owns the duel-bot FSM and drives all combat each tick from {@link #run()}.
 */
@TraitName("duelbot_combat")
public class CombatTrait extends Trait {

    private String tierName;
    private Boolean blocksEnabled;

    private DuelBotPlugin plugin;
    private DuelBotSettings settings;
    private BotProfile profile;
    private StateMachine fsm;

    private AimController aim;
    private SwingController swing;
    private CritJumpController critJump;
    private WTapController wtap;
    private StrafeController strafe;
    private ClutchDetector clutch;
    private BoxInController boxIn;
    private BlockTapPredictor blockTap;

    private Player target;
    private int retargetCooldown;
    private int engageReactionTicks;

    private boolean pendingAttack;
    private boolean pendingCrit;
    private int pendingDelay;

    private final Deque<Long> recentHits = new ArrayDeque<>();
    private long tickCounter;

    // Human knockback-recovery replay state (see startKbReplay / tickKbReplay).
    private KbTrace kbTrace;
    private int kbIdx;
    private double kbAlongX, kbAlongZ, kbSideX, kbSideZ;
    private int jumpPending;

    public CombatTrait() {
        super("duelbot_combat");
    }

    @Override
    public void load(DataKey key) {
        this.tierName = key.getString("tier", null);
        if (key.keyExists("blocks")) this.blocksEnabled = key.getBoolean("blocks", true);
    }

    @Override
    public void save(DataKey key) {
        if (tierName != null) key.setString("tier", tierName);
        if (blocksEnabled != null) key.setBoolean("blocks", blocksEnabled);
    }

    public boolean blocksEnabled() {
        return blocksEnabled == null || blocksEnabled;
    }

    public void setBlocksEnabled(boolean enabled) {
        this.blocksEnabled = enabled;
        if (!enabled && boxIn != null) boxIn.abort();
    }

    public void setTier(String tierName) {
        this.tierName = tierName;
        if (isInitialised()) initControllers();
    }

    @Override
    public void onSpawn() {
        this.plugin = DuelBotPlugin.get();
        if (plugin == null) return;
        this.settings = plugin.getSettings();
        if (blocksEnabled == null) blocksEnabled = settings.defaultBlocksEnabled;
        initControllers();

        npc.setProtected(false);
        Entity e = npc.getEntity();
        if (e != null) {
            e.setInvulnerable(false);
            if (e instanceof LivingEntity le) {
                le.setCollidable(true);
                le.setRemoveWhenFarAway(false);
            }
            if (settings.giveKit && e instanceof Player p) giveKit(p);
        }
        if (npc.getNavigator() != null) {
            npc.getNavigator().getDefaultParameters().speedModifier(1.3f);
            npc.getNavigator().getDefaultParameters().range(48f);
            npc.getNavigator().getDefaultParameters().distanceMargin(1.5);
        }
    }

    private boolean isInitialised() {
        return fsm != null && profile != null;
    }

    private void initControllers() {
        DifficultyTier tier = plugin.getDifficultyConfig().get(tierName);
        if (tier == null) tier = plugin.getDifficultyConfig().getDefault();
        this.tierName = tier.name;
        long seed = (npc != null ? npc.getId() : 0L) * 2654435761L + System.nanoTime();
        this.profile = new BotProfile(tier, seed);
        this.aim = new AimController(profile);
        this.swing = new SwingController(profile);
        this.critJump = new CritJumpController();
        this.wtap = new WTapController(profile);
        this.strafe = new StrafeController(profile);
        this.clutch = new ClutchDetector();
        this.boxIn = new BoxInController(tier, settings.buildMaterial);
        this.blockTap = new BlockTapPredictor(settings.buildMaterial, settings.blockTapTicksAhead);
        this.fsm = new StateMachine();
    }

    private void giveKit(Player p) {
        ItemStack sword = new ItemStack(settings.weaponMaterial);
        p.getInventory().setItem(0, sword);
        p.getInventory().setItem(1, new ItemStack(settings.buildMaterial, 64));
        p.getInventory().setItem(2, new ItemStack(Material.GOLDEN_APPLE, 8));
        p.getInventory().setHeldItemSlot(0);
    }

    @Override
    public void run() {
        if (plugin == null || npc == null || !npc.isSpawned()) return;
        if (!isInitialised()) return;
        Entity e = npc.getEntity();
        if (!(e instanceof Player)) return;

        tickCounter++;
        pruneHits();

        if (retargetCooldown > 0) retargetCooldown--;
        if (retargetCooldown <= 0) {
            acquireTarget();
            retargetCooldown = Math.max(1, settings.retargetIntervalTicks);
        }
        if (target != null) blockTap.observe(target);

        // Human knockback-recovery replay owns velocity while it runs, regardless of FSM state.
        tickKbReplay((Player) e);

        fsm.tick(this);
    }

    // ---- Target acquisition ----

    private void acquireTarget() {
        Player bot = bot();
        if (bot == null) return;
        if (target != null && isValidTarget(target)
                && target.getLocation().distance(bot.getLocation()) <= settings.disengageRange) {
            return; // sticky target
        }
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : bot.getWorld().getPlayers()) {
            if (!isValidTarget(p)) continue;
            double d = p.getLocation().distanceSquared(bot.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        if (best != null && Math.sqrt(bestDist) <= settings.aggroRange) {
            target = best;
        } else if (target != null && (!isValidTarget(target)
                || target.getLocation().distance(bot.getLocation()) > settings.disengageRange)) {
            target = null;
        }
    }

    private boolean isValidTarget(Player p) {
        if (p == null || !p.isOnline() || p.isDead()) return false;
        if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) return false;
        if (CitizensAPI.getNPCRegistry().isNPC(p)) return false;
        if (!inDuelWorld(p.getWorld().getName())) return false;
        return true;
    }

    private boolean inDuelWorld(String worldName) {
        return settings.duelWorld == null || settings.duelWorld.isEmpty()
                || settings.duelWorld.equalsIgnoreCase(worldName);
    }

    // ---- FSM context accessors ----

    public boolean hasTarget() {
        return target != null && isValidTarget(target);
    }

    public boolean targetInAggroRange() {
        Player bot = bot();
        return bot != null && target != null && target.getLocation().distance(bot.getLocation()) <= settings.aggroRange;
    }

    public boolean targetOutOfDisengageRange() {
        Player bot = bot();
        return bot == null || target == null || target.getLocation().distance(bot.getLocation()) > settings.disengageRange;
    }

    public boolean shouldClutch() {
        Player bot = bot();
        return bot != null && clutch.shouldClutch(bot);
    }

    public double healthRatio() {
        Player bot = bot();
        if (bot == null) return 1.0;
        double max = maxHealth(bot);
        return max <= 0 ? 1.0 : bot.getHealth() / max;
    }

    public double panicHealthRatio() {
        return settings.panicHealthRatio;
    }

    public double reengageHealthRatio() {
        return settings.reengageHealthRatio;
    }

    public boolean underPressure() {
        return recentHits.size() >= settings.hitCountThreshold;
    }

    public void recordIncomingHit() {
        recentHits.addLast(tickCounter);
        if (engageReactionTicks <= 0) engageReactionTicks = profile.nextReactionDelay();
        startKbReplay();
    }

    /** Begins replaying a sampled human recovery trajectory aligned to the bot's knockback. */
    private void startKbReplay() {
        if (settings == null || !settings.naturalMovement) return;
        Player bot = bot();
        if (bot == null) return;
        HumanSampleStore store = plugin.getSampleStore();
        if (store == null || store.kbCount() == 0) return;

        Vector v = bot.getVelocity();
        double ax = v.getX(), az = v.getZ();
        double h = Math.sqrt(ax * ax + az * az);
        if (h < 1.0e-4) {
            Vector away = target != null ? bot.getLocation().toVector().subtract(target.getLocation().toVector())
                    : new Vector(0, 0, 1);
            away.setY(0);
            if (away.lengthSquared() < 1.0e-6) away = new Vector(0, 0, 1);
            away.normalize();
            ax = away.getX();
            az = away.getZ();
            h = 1.0;
        }
        KbTrace t = store.sampleKbTrace(h, v.getY(), profile.random());
        if (t == null) return;
        kbAlongX = ax / h;
        kbAlongZ = az / h;
        kbSideX = -kbAlongZ;
        kbSideZ = kbAlongX;
        kbTrace = t;
        kbIdx = 0;
    }

    private boolean kbReplayActive() {
        return kbTrace != null && kbIdx < kbTrace.length();
    }

    private void tickKbReplay(Player bot) {
        if (!kbReplayActive()) {
            kbTrace = null;
            return;
        }
        double[] row = kbTrace.vel[kbIdx++];
        java.util.Random r = profile.random();
        double noise = settings.sampleNoise * 0.1;
        double vx = kbAlongX * row[0] + kbSideX * row[2] + (r.nextDouble() * 2 - 1) * noise;
        double vz = kbAlongZ * row[0] + kbSideZ * row[2] + (r.nextDouble() * 2 - 1) * noise;
        double vy = row[1];
        Vector cur = bot.getVelocity();
        double blend = 0.5;
        bot.setVelocity(new Vector(
                cur.getX() + (vx - cur.getX()) * blend,
                cur.getY() + (vy - cur.getY()) * blend,
                cur.getZ() + (vz - cur.getZ()) * blend));
    }

    private void pruneHits() {
        while (!recentHits.isEmpty() && tickCounter - recentHits.peekFirst() > settings.hitWindowTicks) {
            recentHits.pollFirst();
        }
    }

    // ---- State behaviours ----

    public void onEngageStart() {
        aim.rerollOffset();
        engageReactionTicks = profile.nextReactionDelay();
        if (npc.getNavigator() != null) npc.getNavigator().cancelNavigation();
    }

    public void idleTick() {
        // Nothing to do without a target; Citizens keeps the NPC standing.
    }

    public void releaseNavigatorControl() {
        if (npc.getNavigator() != null) npc.getNavigator().cancelNavigation();
    }

    public void faceTarget() {
        Player bot = bot();
        if (bot == null || target == null) return;
        Location look = aim.computeLook(bot, target);
        npc.faceLocation(look);
        bot.setRotation(aim.currentYaw(), aim.currentPitch());
    }

    public void combatMovement() {
        Player bot = bot();
        if (bot == null || target == null) return;
        if (kbReplayActive()) return; // recovery replay owns velocity
        double sep = bot.getLocation().distance(target.getLocation());
        if (sep > settings.combatRange) {
            npc.getNavigator().setTarget(target, true);
            return;
        }
        if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
        if (wtap.active()) return; // W-tap owns velocity this tick

        Vector move = desiredCombatMove(bot, sep);
        // Keep combat spacing: back off if crowding the target, otherwise close in.
        double ideal = Math.max(2.0, settings.combatRange * 0.6);
        if (sep < ideal * 0.75) {
            move.subtract(flatToTarget(bot).multiply(0.8)); // reverse forward to open space
        }
        if (move.lengthSquared() > 1.0e-6) move.normalize();

        Vector desiredVel = move.multiply(settings.moveSpeed);
        Vector v = bot.getVelocity();
        // Acceleration-limited so direction changes are smooth, not teleporty. Less control mid-air.
        double accel = bot.isOnGround() ? 0.35 : 0.10;
        double nx = v.getX() + (desiredVel.getX() - v.getX()) * accel;
        double nz = v.getZ() + (desiredVel.getZ() - v.getZ()) * accel;
        double ny = v.getY();
        if (jumpPending > 0 && bot.isOnGround()) {
            ny = 0.42; // human-like hop sampled from recorded traces
            jumpPending = 0;
        } else if (jumpPending > 0) {
            jumpPending--;
        }
        bot.setVelocity(new Vector(nx, ny, nz));
    }

    /** Sampled human micro-movement when data exists, else the deterministic strafe controller. */
    private Vector desiredCombatMove(Player bot, double sep) {
        if (settings.naturalMovement && plugin.getSampleStore() != null) {
            MovementSample ms = plugin.getSampleStore()
                    .sampleMovement(MovementSample.bucketOf(sep), profile.random());
            if (ms != null) {
                java.util.Random r = profile.random();
                double noise = settings.sampleNoise;
                double fwd = ms.forward() + (r.nextDouble() * 2 - 1) * noise;
                double str = ms.strafe() + (r.nextDouble() * 2 - 1) * noise;
                if (ms.jump() && jumpPending <= 0) jumpPending = 1;
                Vector f = flatToTarget(bot);
                Vector right = new Vector(-f.getZ(), 0, f.getX());
                return f.multiply(fwd).add(right.multiply(str));
            }
        }
        strafe.tick();
        return strafe.desiredMove(bot.getLocation(), target.getLocation());
    }

    private Vector flatToTarget(Player bot) {
        Vector d = target.getLocation().toVector().subtract(bot.getLocation().toVector());
        d.setY(0);
        return d.lengthSquared() < 1.0e-6 ? new Vector(0, 0, 1) : d.normalize();
    }

    public void tickCombatSwing() {
        Player bot = bot();
        if (bot == null || target == null) return;
        swing.tick();
        wtap.tick(bot);

        if (engageReactionTicks > 0) {
            engageReactionTicks--;
            return;
        }
        if (pendingAttack) {
            pendingDelay--;
            bot.setSprinting(false); // keep sprint cancelled so the crit qualifies
            if (pendingDelay <= 0) {
                executeAttack(bot, pendingCrit);
                pendingAttack = false;
                swing.consume();
            }
            return;
        }
        if (!swing.ready()) return;
        if (!swing.inReach(bot, target, profile.tier().reach)) return;
        if (!aim.onTarget(bot, target, settings.aimToleranceDegrees)) return;
        if (!bot.hasLineOfSight(target)) return; // never swing through walls

        boolean wantCrit = critJump.canJump(bot) && profile.rollCrit();
        if (wantCrit) {
            critJump.jump(bot);
            if (profile.rollWTap()) wtap.trigger(bot, horizontalForward(bot));
            pendingAttack = true;
            pendingCrit = true;
            pendingDelay = 2;
        } else {
            executeAttack(bot, false);
            if (profile.rollWTap()) wtap.trigger(bot, horizontalForward(bot));
            swing.consume();
        }
    }

    private void executeAttack(Player bot, boolean attemptCrit) {
        if (target == null || !isValidTarget(target)) return;
        if (!swing.inReach(bot, target, profile.tier().reach)) return;
        if (!bot.hasLineOfSight(target)) return;
        double dmg = weaponDamage(bot);
        boolean crit = attemptCrit && critJump.critValid(bot, bot.isSprinting());
        if (crit) {
            dmg *= 1.5;
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.1);
        }
        bot.swingMainHand();
        target.damage(dmg, bot);
    }

    public void tryBlockTap() {
        if (!blocksEnabled()) return;
        Player bot = bot();
        if (bot == null || target == null) return;
        if (!blockTap.ready()) return;
        blockTap.tryTap(bot, target, profile.tier().reach, settings.blockTapTicksAhead);
    }

    public void beginBoxIn() {
        if (!blocksEnabled()) return;
        if (!boxIn.isActive()) boxIn.start();
    }

    public void tickBoxIn() {
        if (!blocksEnabled()) return;
        Player bot = bot();
        if (bot == null || target == null) return;
        boxIn.tick(bot, target);
    }

    public void tickClutch() {
        if (!blocksEnabled()) return;
        Player bot = bot();
        if (bot == null) return;
        if (clutch.shouldPlaceNow(bot, 2) || bot.getVelocity().getY() < -0.55) {
            bot.setRotation(bot.getLocation().getYaw(), 85f);
            Block target = clutch.clutchTarget(bot);
            BlockPlacer.place(bot, target, settings.buildMaterial);
        }
    }

    // ---- Helpers ----

    private Player bot() {
        Entity e = npc.getEntity();
        return e instanceof Player p ? p : null;
    }

    /** The underlying Bukkit player entity, or null if not spawned as a player. */
    public Player botPlayer() {
        return bot();
    }

    private Vector horizontalForward(Player bot) {
        Vector d = bot.getLocation().getDirection();
        d.setY(0);
        if (d.lengthSquared() < 1.0e-4) return new Vector(0, 0, 1);
        return d.normalize();
    }

    private double weaponDamage(Player bot) {
        ItemStack hand = bot.getInventory().getItemInMainHand();
        double base = switch (hand.getType()) {
            case WOODEN_SWORD, GOLDEN_SWORD -> 4.0;
            case STONE_SWORD -> 5.0;
            case IRON_SWORD -> 6.0;
            case DIAMOND_SWORD -> 7.0;
            case NETHERITE_SWORD -> 8.0;
            case WOODEN_AXE, GOLDEN_AXE -> 3.0;
            case STONE_AXE -> 4.0;
            case IRON_AXE -> 5.0;
            case DIAMOND_AXE -> 6.0;
            case NETHERITE_AXE -> 7.0;
            default -> 1.0;
        };
        int sharp = hand.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharp > 0) base += 1.25 * sharp;
        return base;
    }

    private double maxHealth(Player bot) {
        if (bot.getAttribute(Attribute.MAX_HEALTH) != null) {
            return bot.getAttribute(Attribute.MAX_HEALTH).getValue();
        }
        return 20.0;
    }

    public BotState currentState() {
        return fsm != null ? fsm.currentId() : BotState.IDLE;
    }

    public String tierName() {
        return tierName;
    }

    public Player currentTarget() {
        return target;
    }
}

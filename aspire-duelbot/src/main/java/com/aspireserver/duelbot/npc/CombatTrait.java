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
        double sep = bot.getLocation().distance(target.getLocation());
        if (sep > settings.combatRange) {
            npc.getNavigator().setTarget(target, true);
            return;
        }
        if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
        strafe.tick();
        if (wtap.active()) return; // W-tap owns velocity this tick
        Vector move = strafe.desiredMove(bot.getLocation(), target.getLocation());
        Vector v = bot.getVelocity();
        bot.setVelocity(new Vector(move.getX() * settings.moveSpeed, v.getY(), move.getZ() * settings.moveSpeed));
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

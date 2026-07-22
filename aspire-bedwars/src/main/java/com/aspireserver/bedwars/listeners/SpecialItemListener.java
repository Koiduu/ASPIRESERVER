package com.aspireserver.bedwars.listeners;

import com.aspireserver.bedwars.AspireBedwars;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class SpecialItemListener implements Listener {

    private final AspireBedwars plugin;

    public SpecialItemListener(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    private boolean active(Player player) {
        return plugin.getGameManager().isInBedwarsWorld(player)
                && plugin.getGameManager().isRunning()
                && !plugin.getSpectatorManager().isSpectator(player.getUniqueId())
                && !plugin.getSetupMode().isInSetup(player);
    }

    @EventHandler
    public void onFireball(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        if (hand == null || hand.getType() != Material.FIRE_CHARGE) return;
        if (!active(player)) return;

        event.setCancelled(true);

        // Hypixel-style fireball: launches instantly and flies straight, no gravity, no fire.
        // Spawned just in front of the eyes and driven by acceleration (setDirection) so it
        // shoots off immediately instead of stalling. Vanilla explosion is neutralised (yield 0)
        // and replaced with a custom knockback burst on impact so it launches players and only
        // breaks player-placed blocks.
        Vector dir = player.getEyeLocation().getDirection().normalize();
        Location spawnLoc = player.getEyeLocation().add(dir.clone().multiply(0.8));
        Fireball fireball = player.getWorld().spawn(spawnLoc, Fireball.class);
        fireball.setShooter(player);
        fireball.setYield(0f);
        fireball.setIsIncendiary(false);
        fireball.setDirection(dir.clone().multiply(FIREBALL_SPEED));
        fireball.setVelocity(dir.clone().multiply(FIREBALL_SPEED));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 1f);

        hand.setAmount(hand.getAmount() - 1);
    }

    // Hitting a fireball deflects it: it redirects along the striker's aim and becomes theirs.
    @EventHandler
    public void onFireballDeflect(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball)) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (!fireball.getWorld().getName().equalsIgnoreCase(plugin.getSetupConfig().getWorldName())) return;
        if (!plugin.getGameManager().isRunning()) return;
        if (plugin.getSpectatorManager().isSpectator(player.getUniqueId())) return;

        event.setCancelled(true);
        Vector dir = player.getEyeLocation().getDirection().normalize();
        fireball.setShooter(player);
        fireball.setDirection(dir.clone().multiply(FIREBALL_SPEED));
        fireball.setVelocity(dir.clone().multiply(FIREBALL_SPEED));
        fireball.getWorld().playSound(fireball.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1.4f);
    }

    private static final double FIREBALL_SPEED = 1.2;
    private static final double FIREBALL_RADIUS = 3.0;
    private static final double FIREBALL_KNOCKBACK = 1.35;

    @EventHandler
    public void onFireballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball)) return;
        if (!fireball.getWorld().getName().equalsIgnoreCase(plugin.getSetupConfig().getWorldName())) return;
        if (!plugin.getGameManager().isRunning()) { fireball.remove(); return; }

        Location center = fireball.getLocation();
        Entity shooter = fireball.getShooter() instanceof Entity e ? e : null;
        fireball.remove();

        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 1f);

        // Launch nearby players (thrower included, like Hypixel) with a little damage.
        for (Entity entity : center.getWorld().getNearbyEntities(center, FIREBALL_RADIUS, FIREBALL_RADIUS, FIREBALL_RADIUS)) {
            if (!(entity instanceof Player p)) continue;
            if (plugin.getSpectatorManager().isSpectator(p.getUniqueId())) continue;
            double dist = p.getLocation().distance(center);
            if (dist > FIREBALL_RADIUS) continue;
            double falloff = 1.0 - (dist / FIREBALL_RADIUS);
            Vector push = p.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.01) push = new Vector(0, 1, 0);
            push.normalize().multiply(FIREBALL_KNOCKBACK * falloff);
            push.setY(Math.max(0.45, push.getY()) + 0.35 * falloff);
            p.setVelocity(p.getVelocity().add(push));
            if (shooter instanceof Player shp && shp.equals(p)) continue;
            p.damage(2.0, shooter);
        }

        // Break only player-placed blocks (never map terrain or beds) in the blast.
        int r = (int) Math.ceil(FIREBALL_RADIUS);
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    Block b = center.getBlock().getRelative(x, y, z);
                    if (b.getLocation().add(0.5, 0.5, 0.5).distance(center) > FIREBALL_RADIUS) continue;
                    if (b.getType().isAir()) continue;
                    if (plugin.getArenaReset().isPlayerPlaced(b)) {
                        plugin.getArenaReset().recordBreak(b);
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    // Fireball self/AoE damage is capped low so it launches rather than kills.
    @EventHandler(ignoreCancelled = true)
    public void onFireballDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;
        if (!event.getEntity().getWorld().getName().equalsIgnoreCase(plugin.getSetupConfig().getWorldName())) return;
        if (event.getDamage() > 4.0) event.setDamage(4.0);
    }

    @EventHandler
    public void onTntPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.TNT) return;
        Player player = event.getPlayer();
        if (plugin.getSetupMode().isInSetup(player)) return;
        if (!active(player)) return;

        // Bedwars TNT ignites instantly
        event.setCancelled(true);
        ItemStack hand = event.getItemInHand();
        hand.setAmount(hand.getAmount() - 1);

        TNTPrimed tnt = event.getBlock().getWorld().spawn(event.getBlock().getLocation().add(0.5, 0, 0.5), TNTPrimed.class);
        tnt.setFuseTicks(50);
        tnt.setYield(3.0f);
        tnt.setSource(player);
        // Reduce self-knockback launch — vanilla-ish
        tnt.setVelocity(new Vector(0, 0.1, 0));
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getGameManager().isInBedwarsWorld(player)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }
}

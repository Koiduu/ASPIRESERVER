package com.aspireserver.duelbot.blocks;

import com.aspireserver.duelbot.config.DifficultyTier;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Defensive box-in + heal (Section 4.2). A short stepped sequence: retreat, look down,
 * wall off line-of-sight, then consume a regular golden apple (Regen II over time).
 */
public final class BoxInController {

    private final DifficultyTier tier;
    private final Material wallMaterial;

    private boolean active;
    private int step;
    private int cooldown;

    public BoxInController(DifficultyTier tier, Material wallMaterial) {
        this.tier = tier;
        this.wallMaterial = wallMaterial;
    }

    public boolean isActive() {
        return active;
    }

    public void start() {
        active = true;
        step = 0;
        cooldown = 0;
    }

    public void abort() {
        active = false;
    }

    /** @return true when the sequence has finished this tick. */
    public boolean tick(Player bot, LivingEntity target) {
        if (!active) return false;
        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        switch (step) {
            case 0 -> {
                // Retreat from the target's horizontal direction, look straight down.
                Vector away = bot.getLocation().toVector().subtract(target.getLocation().toVector());
                away.setY(0);
                if (away.lengthSquared() > 1.0e-4) {
                    away.normalize().multiply(0.35);
                    bot.setVelocity(new Vector(away.getX(), bot.getVelocity().getY(), away.getZ()));
                }
                bot.setRotation(bot.getLocation().getYaw(), 80f);
                cooldown = tier.blockPlaceDelayTicks;
                step++;
            }
            case 1 -> {
                placeFacing(bot, 1); // chest height
                cooldown = tier.blockPlaceDelayTicks;
                step++;
            }
            case 2 -> {
                placeFacing(bot, 2); // head height
                cooldown = tier.blockPlaceDelayTicks;
                step++;
            }
            case 3 -> {
                eatGoldenApple(bot);
                active = false;
                return true;
            }
            default -> active = false;
        }
        return false;
    }

    private void placeFacing(Player bot, int heightOffset) {
        Vector facing = bot.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 1.0e-4) return;
        facing.normalize();
        Block base = bot.getLocation().add(facing).getBlock();
        BlockPlacer.place(bot, base.getRelative(0, heightOffset, 0), wallMaterial);
    }

    private void eatGoldenApple(Player bot) {
        // Model a regular golden apple: Regeneration II (5s) + Absorption I (2m).
        bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, false, true));
        bot.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0, false, false, true));
        bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.8f, 1.1f);
    }
}

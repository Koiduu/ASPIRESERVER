package com.aspireserver.duelbot.combat;

import com.aspireserver.duelbot.npc.BotProfile;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * W-tapping (Section 3.4): cancel sprint and briefly reverse input around a hit to
 * kill residual forward momentum (keeps combo range) and to enable the crit.
 */
public final class WTapController {

    private final BotProfile profile;
    private int reverseTicks;
    private Vector reverseDir = new Vector(0, 0, 0);

    public WTapController(BotProfile profile) {
        this.profile = profile;
    }

    /** Trigger a W-tap given the current horizontal forward vector. */
    public void trigger(LivingEntity bot, Vector forwardHorizontal) {
        if (bot instanceof Player p) p.setSprinting(false);
        reverseTicks = Math.max(1, profile.tier().wtapTicks);
        reverseDir = forwardHorizontal.clone().setY(0).normalize().multiply(-1);
    }

    /** Apply the residual-momentum kill for the configured number of ticks. */
    public void tick(LivingEntity bot) {
        if (reverseTicks <= 0) return;
        Vector v = bot.getVelocity();
        double damp = 0.6;
        bot.setVelocity(new Vector(
                v.getX() * damp + reverseDir.getX() * 0.08,
                v.getY(),
                v.getZ() * damp + reverseDir.getZ() * 0.08));
        reverseTicks--;
    }

    public boolean active() {
        return reverseTicks > 0;
    }
}

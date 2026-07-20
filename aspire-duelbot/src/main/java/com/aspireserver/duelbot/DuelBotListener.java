package com.aspireserver.duelbot;

import com.aspireserver.duelbot.npc.CombatTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Feeds incoming-hit timing to the bot's trait so PANIC/reaction logic can respond. */
public final class DuelBotListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!CitizensAPI.getNPCRegistry().isNPC(victim)) return;
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(victim);
        if (npc == null) return;
        CombatTrait trait = npc.getTraitNullable(CombatTrait.class);
        if (trait != null) trait.recordIncomingHit();
    }
}

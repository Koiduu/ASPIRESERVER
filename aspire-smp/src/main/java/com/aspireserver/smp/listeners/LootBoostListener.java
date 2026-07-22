package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;

public class LootBoostListener implements Listener {

    private final AspireSMP plugin;
    private final Random random = new Random();

    public LootBoostListener(AspireSMP plugin) {
        this.plugin = plugin;
    }

    private boolean isSmpWorld(World world) {
        String smpWorld = plugin.getConfig().getString("smp-world", "");
        return !smpWorld.isEmpty() && world.getName().equalsIgnoreCase(smpWorld);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        if (!isSmpWorld(event.getEntity().getWorld())) return;

        List<ItemStack> drops = event.getDrops();
        for (ItemStack drop : drops) {
            if (drop == null) continue;
            int original = drop.getAmount();
            int bonus = (int) Math.ceil(original * 0.25);
            if (bonus > 0 && random.nextDouble() < 0.25 * original) {
                drop.setAmount(original + Math.max(1, bonus));
            }
        }

        int xp = event.getDroppedExp();
        event.setDroppedExp((int) Math.ceil(xp * 1.25));
    }
}

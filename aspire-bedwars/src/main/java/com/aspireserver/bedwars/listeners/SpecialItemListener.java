package com.aspireserver.bedwars.listeners;

import com.aspireserver.bedwars.AspireBedwars;
import org.bukkit.Material;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
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
        Fireball fireball = player.launchProjectile(Fireball.class, player.getEyeLocation().getDirection().multiply(1.5));
        fireball.setYield(2.0f);
        fireball.setIsIncendiary(false);
        fireball.setShooter(player);
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ITEM_FIRECHARGE_USE, 1f, 1f);

        hand.setAmount(hand.getAmount() - 1);
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

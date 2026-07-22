package com.aspireserver.chameleon.listeners;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.game.GameManager;
import com.aspireserver.chameleon.game.GameState;
import com.aspireserver.chameleon.game.PlayerRole;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QuakeGunListener implements Listener {

    private final MecchaChameleon plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public QuakeGunListener(MecchaChameleon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameActive() || gm.getState() != GameState.HUNTING) return;
        if (gm.getRole(player.getUniqueId()) != PlayerRole.SEEKER) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.NETHERITE_HOE) return;
        if (!item.hasItemMeta()) return;

        String name = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        if (!"Quake Gun".equals(name)) return;

        event.setCancelled(true);

        // Cooldown check
        int cooldownTicks = plugin.getConfigManager().getQuakeGunCooldown();
        long cooldownMs = cooldownTicks * 50L;
        long now = System.currentTimeMillis();
        Long lastUse = cooldowns.get(player.getUniqueId());
        if (lastUse != null && now - lastUse < cooldownMs) {
            long remaining = (cooldownMs - (now - lastUse)) / 1000;
            player.sendActionBar(Component.text("Cooldown: " + remaining + "s", NamedTextColor.RED));
            return;
        }
        cooldowns.put(player.getUniqueId(), now);

        // Fire particle beam
        fireBeam(player, gm);
    }

    private void fireBeam(Player shooter, GameManager gm) {
        Location loc = shooter.getEyeLocation().clone();
        Vector direction = loc.getDirection().normalize().multiply(0.5);
        World world = loc.getWorld();

        for (int i = 0; i < 60; i++) { // 30 blocks range
            loc.add(direction);

            // Particle trail
            world.spawnParticle(Particle.CRIT, loc, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.FIREWORK, loc, 1, 0, 0, 0, 0);

            // Check if hit a block
            if (loc.getBlock().getType().isSolid()) break;

            // Check if hit a hider
            for (Player target : world.getNearbyPlayers(loc, 0.6)) {
                if (target.equals(shooter)) continue;
                if (gm.getRole(target.getUniqueId()) == PlayerRole.HIDER) {
                    // Hit!
                    world.spawnParticle(Particle.EXPLOSION, target.getLocation(), 3);
                    world.playSound(target.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.5f, 1.0f);
                    gm.onHiderCaught(target, shooter);
                    shooter.sendMessage(Component.text("[Chameleon] ", NamedTextColor.GREEN)
                            .append(Component.text("Hit " + target.getName() + "!", NamedTextColor.RED)));
                    return;
                }
            }
        }

        // Miss sound
        world.playSound(shooter.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_SHOOT, 0.8f, 1.5f);
    }
}

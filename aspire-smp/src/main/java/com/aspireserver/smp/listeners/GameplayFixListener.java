package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class GameplayFixListener implements Listener {

    private final AspireSMP plugin;

    public GameplayFixListener(AspireSMP plugin) {
        this.plugin = plugin;
        registerShulkerRecipe();
    }

    private boolean isSmpWorld(World world) {
        String smpWorld = plugin.getConfig().getString("smp-world", "");
        return !smpWorld.isEmpty() && world.getName().equalsIgnoreCase(smpWorld);
    }

    // --- Fix: Infestation potion works on all passive mobs (armadillos etc.) ---
    // The "infestation" effect spawns silverfish when an entity is hurt.
    // Paper/Vanilla only applies it to certain mobs. We manually trigger it for others.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!isSmpWorld(living.getWorld())) return;
        if (living instanceof Player) return;
        if (living instanceof Silverfish) return;

        PotionEffect infestation = living.getPotionEffect(PotionEffectType.INFESTED);
        if (infestation == null) return;

        // 30% chance per hit to spawn silverfish (matches vanilla behavior for blocks)
        if (Math.random() > 0.30) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            Silverfish silverfish = (Silverfish) living.getWorld().spawnEntity(
                    living.getLocation(), EntityType.SILVERFISH);
            silverfish.setTarget(living);
        });
    }

    // --- Fix: Infestation potion splash applies to all living entities ---
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!isSmpWorld(event.getEntity().getWorld())) return;

        boolean hasInfestation = event.getPotion().getEffects().stream()
                .anyMatch(e -> e.getType().equals(PotionEffectType.INFESTED));
        if (!hasInfestation) return;

        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player) continue;
            double intensity = event.getIntensity(affected);
            if (intensity <= 0) continue;
            // Apply infestation manually if the entity didn't receive it
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (affected.isValid() && !affected.isDead()) {
                    if (affected.getPotionEffect(PotionEffectType.INFESTED) == null) {
                        affected.addPotionEffect(new PotionEffect(PotionEffectType.INFESTED, 600, 0));
                    }
                }
            }, 1L);
        }
    }

    // --- Fix: Minecart doesn't disappear when zombie converts villager ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityTransform(EntityTransformEvent event) {
        if (!isSmpWorld(event.getEntity().getWorld())) return;

        Entity original = event.getEntity();
        if (!(original instanceof Villager villager)) return;

        // Check if villager was in a vehicle (minecart)
        Entity vehicle = villager.getVehicle();
        if (vehicle == null) return;

        // After transformation, put the zombie villager back in the vehicle
        List<Entity> transformed = event.getTransformedEntities();
        if (transformed.isEmpty()) return;

        Entity newEntity = transformed.get(0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (vehicle.isValid() && newEntity.isValid()) {
                vehicle.addPassenger(newEntity);
            }
        }, 2L);
    }

    // --- Fix: Trial chambers - prevent spawner mob stacking / entity overload ---
    @EventHandler(priority = EventPriority.HIGH)
    public void onTrialSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
        if (!isSmpWorld(event.getLocation().getWorld())) return;
        if (event.getSpawnReason() != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER) return;

        // Limit trial spawner mobs to 12 within 8-block radius
        int nearby = 0;
        for (Entity e : event.getEntity().getNearbyEntities(8, 8, 8)) {
            if (e instanceof LivingEntity && !(e instanceof Player)) {
                nearby++;
            }
        }
        if (nearby >= 12) {
            event.setCancelled(true);
        }
    }

    // --- Custom shulker recipe: obsidian chest ---
    private void registerShulkerRecipe() {
        NamespacedKey key = new NamespacedKey(plugin, "obsidian_shulker");

        // Remove if already exists (reload safety)
        Bukkit.removeRecipe(key);

        ItemStack result = new ItemStack(Material.SHULKER_BOX, 1);

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("OOO", "O O", "OOO");
        recipe.setIngredient('O', Material.OBSIDIAN);

        Bukkit.addRecipe(recipe);
        plugin.getLogger().info("[AspireSMP] Registered obsidian shulker box recipe");
    }
}

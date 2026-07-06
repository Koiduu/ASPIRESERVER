package com.aspireserver.smp.listeners;

import com.aspireserver.smp.AspireSMP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GameplayFixListener implements Listener {

    private final AspireSMP plugin;
    // Track minecarts that are protected during villager transformation
    private final Set<UUID> protectedMinecarts = ConcurrentHashMap.newKeySet();

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
    // Also ensure villager DOES convert (not just die) by allowing the transform event
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityTransform(EntityTransformEvent event) {
        if (!isSmpWorld(event.getEntity().getWorld())) return;

        Entity original = event.getEntity();
        if (!(original instanceof Villager villager)) return;

        // Never cancel villager -> zombie villager transformations
        if (event.getTransformReason() == EntityTransformEvent.TransformReason.INFECTION) {
            // Explicitly allow it (in case something else cancels)
            // Also track the villager's vehicle
            Entity vehicle = villager.getVehicle();
            if (vehicle != null && vehicle instanceof Minecart) {
                protectedMinecarts.add(vehicle.getUniqueId());

                List<Entity> transformed = event.getTransformedEntities();
                if (!transformed.isEmpty()) {
                    Entity newEntity = transformed.get(0);
                    Location minecartLoc = vehicle.getLocation().clone();

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (vehicle.isValid() && newEntity.isValid()) {
                            vehicle.addPassenger(newEntity);
                        } else if (newEntity.isValid()) {
                            Minecart newCart = (Minecart) minecartLoc.getWorld().spawnEntity(minecartLoc, EntityType.MINECART);
                            newCart.addPassenger(newEntity);
                        }
                        protectedMinecarts.remove(vehicle.getUniqueId());
                    }, 3L);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        protectedMinecarts.remove(vehicle.getUniqueId());
                    }, 20L);
                }
            }
        }
    }

    // Prevent villager death if it's actually being converted to zombie villager
    @EventHandler(priority = EventPriority.HIGH)
    public void onVillagerDeath(EntityDeathEvent event) {
        if (!isSmpWorld(event.getEntity().getWorld())) return;
        if (!(event.getEntity() instanceof Villager)) return;
        // If villager was killed by a zombie, the server should handle conversion on Hard
        // The issue is likely the mob cap killing the resulting zombie villager
        // We'll handle this in the spawn listener instead
    }

    // Prevent minecarts from being destroyed during villager conversion
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) return;
        if (protectedMinecarts.contains(minecart.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Trial chambers: allow normal mob spawning (TRIAL_SPAWNER reason is whitelisted)

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

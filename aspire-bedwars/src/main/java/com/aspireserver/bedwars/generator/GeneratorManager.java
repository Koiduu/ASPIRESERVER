package com.aspireserver.bedwars.generator;

import com.aspireserver.bedwars.AspireBedwars;
import com.aspireserver.bedwars.config.GeneratorPoint;
import com.aspireserver.bedwars.team.BedwarsTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class GeneratorManager {

    public static final String GEN_ITEM_META = "bw_gen_item";

    private final AspireBedwars plugin;
    private final List<Generator> generators = new ArrayList<>();
    private BukkitTask task;

    // public (diamond/emerald) tier, 1..3, driven by GameManager timeline
    private int publicTier = 1;

    private int ironInterval;
    private int goldInterval;
    private int diamondInterval;
    private int emeraldInterval;

    public GeneratorManager(AspireBedwars plugin) {
        this.plugin = plugin;
        ironInterval = plugin.getConfig().getInt("generators.iron-interval-ticks", 15);
        goldInterval = plugin.getConfig().getInt("generators.gold-interval-ticks", 70);
        diamondInterval = plugin.getConfig().getInt("generators.diamond-interval-ticks", 600);
        emeraldInterval = plugin.getConfig().getInt("generators.emerald-interval-ticks", 800);
    }

    public void start() {
        stop();
        generators.clear();
        publicTier = 1;

        for (GeneratorPoint gp : plugin.getSetupConfig().getGenerators()) {
            Generator gen = new Generator(gp.type, gp.location.clone().add(0.5, 0, 0.5), gp.team);
            gen.ticksUntilDrop = baseInterval(gp.type);
            generators.add(gen);
            if (!gp.type.isTeamGenerator()) {
                spawnHologram(gen);
            }
        }

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        for (Generator g : generators) {
            for (ArmorStand as : g.holograms) as.remove();
            g.holograms.clear();
        }
        generators.clear();
    }

    public void setPublicTier(int tier) {
        this.publicTier = Math.max(1, Math.min(3, tier));
    }

    public int getPublicTier() { return publicTier; }

    private int baseInterval(GeneratorType type) {
        return switch (type) {
            case IRON -> ironInterval;
            case GOLD -> goldInterval;
            case DIAMOND -> diamondInterval;
            case EMERALD -> emeraldInterval;
        };
    }

    private int effectiveInterval(Generator gen) {
        int base = baseInterval(gen.type);
        if (gen.type.isTeamGenerator() && gen.team != null) {
            BedwarsTeam team = plugin.getTeamManager().getTeam(gen.team);
            if (team != null) {
                // Forge upgrade: each level ~ 20% faster
                double factor = 1.0 - (0.2 * team.getForgeLevel());
                base = Math.max(2, (int) Math.round(base * Math.max(0.2, factor)));
            }
        } else {
            // Public generators speed up with tier
            double factor = switch (publicTier) {
                case 2 -> 0.6;
                case 3 -> 0.35;
                default -> 1.0;
            };
            base = Math.max(20, (int) Math.round(base * factor));
        }
        return base;
    }

    private void tick() {
        for (Generator gen : generators) {
            // Skip team generators whose team is eliminated
            if (gen.type.isTeamGenerator() && gen.team != null) {
                BedwarsTeam team = plugin.getTeamManager().getTeam(gen.team);
                if (team == null || !team.isAlive()) continue;
            }

            gen.ticksUntilDrop--;
            gen.secondsUntilDrop = gen.ticksUntilDrop / 20;

            if (gen.ticksUntilDrop <= 0) {
                dropResource(gen);
                gen.ticksUntilDrop = effectiveInterval(gen);
            }

            if (!gen.holograms.isEmpty()) updateHologram(gen);
        }
    }

    private void dropResource(Generator gen) {
        Location loc = gen.location;
        // Merge/spread guard: cap items of this type on the ground near the gen
        int nearby = 0;
        for (org.bukkit.entity.Entity entity : loc.getWorld().getNearbyEntities(loc, 3.0, 3.0, 3.0)) {
            if (entity instanceof Item item && item.getItemStack().getType() == gen.type.material()) {
                nearby += item.getItemStack().getAmount();
            }
        }
        int cap = switch (gen.type) {
            case IRON -> 48;
            case GOLD -> 16;
            case DIAMOND -> 8;
            case EMERALD -> 4;
        };
        if (nearby >= cap) return;

        ItemStack stack = new ItemStack(gen.type.material(), 1);
        Item dropped = loc.getWorld().dropItem(loc.clone().add(0, 0.5, 0), stack);
        dropped.setVelocity(new Vector(0, 0.1, 0));
        dropped.setMetadata(GEN_ITEM_META, new FixedMetadataValue(plugin, true));
    }

    private void spawnHologram(Generator gen) {
        Location base = gen.location.clone().add(0, 2.2, 0);
        String[] lines = holoLines(gen);
        for (int i = 0; i < lines.length; i++) {
            Location lineLoc = base.clone().add(0, -0.28 * i, 0);
            ArmorStand as = gen.location.getWorld().spawn(lineLoc, ArmorStand.class, a -> {
                a.setVisible(false);
                a.setGravity(false);
                a.setMarker(true);
                a.setInvulnerable(true);
                a.setCustomNameVisible(true);
            });
            as.customName(Component.text(lines[i]));
            gen.holograms.add(as);
        }
    }

    private String[] holoLines(Generator gen) {
        String name = gen.type == GeneratorType.DIAMOND ? "\u00A7bDiamond" : "\u00A72Emerald";
        String tier = "\u00A7eTier " + toRoman(publicTier);
        String timer = "\u00A7fSpawns in \u00A7c" + Math.max(0, gen.secondsUntilDrop) + "s";
        return new String[]{ tier, name, timer };
    }

    private void updateHologram(Generator gen) {
        String[] lines = holoLines(gen);
        for (int i = 0; i < gen.holograms.size() && i < lines.length; i++) {
            gen.holograms.get(i).customName(Component.text(lines[i]));
        }
    }

    private String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(n);
        };
    }
}

package com.aspireserver.duelbot;

import com.aspireserver.duelbot.npc.CombatTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /duelbot spawn|remove|removeall|tier|reload|info */
public final class DuelBotCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "duelbot.admin";
    private final DuelBotPlugin plugin;

    public DuelBotCommand(DuelBotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "spawn" -> handleSpawn(sender, args);
            case "remove" -> handleRemove(sender);
            case "removeall" -> handleRemoveAll(sender);
            case "tier" -> handleTier(sender, args);
            case "reload" -> {
                plugin.reloadSettings();
                sender.sendMessage(ChatColor.GREEN + "DuelBot config reloaded.");
            }
            case "info" -> handleInfo(sender);
            default -> usage(sender);
        }
        return true;
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can spawn a duel bot.");
            return;
        }
        String world = plugin.getSettings().duelWorld;
        if (world != null && !world.isEmpty() && !player.getWorld().getName().equalsIgnoreCase(world)) {
            sender.sendMessage(ChatColor.RED + "Duel bots can only be spawned in the duels world (" + world + ").");
            return;
        }
        String tier = args.length >= 2 ? args[1].toUpperCase() : null;
        if (tier != null && plugin.getDifficultyConfig().get(tier) == null) {
            sender.sendMessage(ChatColor.RED + "Unknown tier '" + tier + "'. Known: " + plugin.getDifficultyConfig().tierNames());
            return;
        }
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER,
                ChatColor.RED + "DuelBot " + ChatColor.GRAY + "[" + (tier != null ? tier : plugin.getDifficultyConfig().getDefault().name) + "]");
        CombatTrait trait = npc.getOrAddTrait(CombatTrait.class);
        trait.setTier(tier);
        npc.spawn(player.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Spawned duel bot #" + npc.getId() + " (" + trait.tierName() + ").");
    }

    private void handleRemove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /duelbot remove.");
            return;
        }
        NPC nearest = null;
        double best = Double.MAX_VALUE;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.getTraitNullable(CombatTrait.class) == null) continue;
            if (!npc.isSpawned() || npc.getEntity() == null) continue;
            if (!npc.getEntity().getWorld().equals(player.getWorld())) continue;
            double d = npc.getEntity().getLocation().distanceSquared(player.getLocation());
            if (d < best) {
                best = d;
                nearest = npc;
            }
        }
        if (nearest == null) {
            sender.sendMessage(ChatColor.RED + "No duel bot found nearby.");
            return;
        }
        int id = nearest.getId();
        nearest.destroy();
        sender.sendMessage(ChatColor.GREEN + "Removed duel bot #" + id + ".");
    }

    private void handleRemoveAll(CommandSender sender) {
        List<NPC> toRemove = new ArrayList<>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.getTraitNullable(CombatTrait.class) != null) toRemove.add(npc);
        }
        for (NPC npc : toRemove) npc.destroy();
        sender.sendMessage(ChatColor.GREEN + "Removed " + toRemove.size() + " duel bot(s).");
    }

    private void handleTier(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /duelbot tier.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /duelbot tier <" + String.join("|", plugin.getDifficultyConfig().tierNames()) + ">");
            return;
        }
        String tier = args[1].toUpperCase();
        if (plugin.getDifficultyConfig().get(tier) == null) {
            sender.sendMessage(ChatColor.RED + "Unknown tier '" + tier + "'.");
            return;
        }
        NPC nearest = null;
        double best = Double.MAX_VALUE;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.getTraitNullable(CombatTrait.class) == null || !npc.isSpawned() || npc.getEntity() == null) continue;
            if (!npc.getEntity().getWorld().equals(player.getWorld())) continue;
            double d = npc.getEntity().getLocation().distanceSquared(player.getLocation());
            if (d < best) {
                best = d;
                nearest = npc;
            }
        }
        if (nearest == null) {
            sender.sendMessage(ChatColor.RED + "No duel bot found nearby.");
            return;
        }
        nearest.getTraitNullable(CombatTrait.class).setTier(tier);
        sender.sendMessage(ChatColor.GREEN + "Set duel bot #" + nearest.getId() + " to tier " + tier + ".");
    }

    private void handleInfo(CommandSender sender) {
        int count = 0;
        StringBuilder sb = new StringBuilder();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            CombatTrait trait = npc.getTraitNullable(CombatTrait.class);
            if (trait == null) continue;
            count++;
            sb.append(ChatColor.GRAY).append("  #").append(npc.getId())
                    .append(" ").append(ChatColor.YELLOW).append(trait.tierName())
                    .append(ChatColor.GRAY).append(" state=").append(trait.currentState())
                    .append(" target=").append(trait.currentTarget() != null ? trait.currentTarget().getName() : "none")
                    .append("\n");
        }
        sender.sendMessage(ChatColor.GREEN + "Duel bots (" + count + "):");
        if (count > 0) sender.sendMessage(sb.toString().trim());
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "/duelbot spawn [tier] " + ChatColor.GRAY + "- spawn a bot at your location");
        sender.sendMessage(ChatColor.GOLD + "/duelbot tier <tier> " + ChatColor.GRAY + "- retune nearest bot");
        sender.sendMessage(ChatColor.GOLD + "/duelbot remove | removeall");
        sender.sendMessage(ChatColor.GOLD + "/duelbot info | reload");
        sender.sendMessage(ChatColor.GRAY + "Tiers: " + plugin.getDifficultyConfig().tierNames());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("spawn", "tier", "remove", "removeall", "info", "reload")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("tier"))) {
            for (String t : plugin.getDifficultyConfig().tierNames()) {
                if (t.startsWith(args[1].toUpperCase())) out.add(t);
            }
        }
        return out;
    }
}

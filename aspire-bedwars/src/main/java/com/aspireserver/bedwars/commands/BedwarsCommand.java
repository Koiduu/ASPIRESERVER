package com.aspireserver.bedwars.commands;

import com.aspireserver.bedwars.AspireBedwars;
import com.aspireserver.bedwars.config.GeneratorPoint;
import com.aspireserver.bedwars.team.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class BedwarsCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "bedwars.admin.setup";
    private final AspireBedwars plugin;

    public BedwarsCommand(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "sb" -> handleSetup(sender, args);
            case "map" -> handleMap(sender, args);
            case "save" -> handleSave(sender);
            case "test" -> handleTest(sender);
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "reload" -> { if (requireAdmin(sender)) { plugin.getSetupConfig().load(); msg(sender, "Config reloaded.", NamedTextColor.GREEN); } }
            case "join" -> handleJoin(sender);
            case "stats" -> handleStats(sender);
            default -> help(sender);
        }
        return true;
    }

    private void handleSetup(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (!(sender instanceof Player player)) { msg(sender, "Players only.", NamedTextColor.RED); return; }
        if (args.length == 1) {
            plugin.getSetupMode().toggle(player);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "show" -> { plugin.getSetupMode().showMarkers(); msg(sender, "Markers shown.", NamedTextColor.GREEN); }
            case "hide" -> { plugin.getSetupMode().hideMarkers(); msg(sender, "Markers hidden.", NamedTextColor.YELLOW); }
            case "list" -> {
                msg(sender, "Setup points:", NamedTextColor.AQUA);
                for (String s : plugin.getSetupMode().listPoints()) {
                    sender.sendMessage(Component.text("  " + s, NamedTextColor.GRAY));
                }
            }
            case "remove" -> {
                if (args.length < 3) { msg(sender, "Usage: /bw sb remove <id>", NamedTextColor.RED); return; }
                try {
                    int id = Integer.parseInt(args[2]);
                    boolean ok = plugin.getSetupMode().removePoint(id);
                    msg(sender, ok ? "Removed point " + id : "No point with id " + id, ok ? NamedTextColor.GREEN : NamedTextColor.RED);
                } catch (NumberFormatException e) { msg(sender, "Invalid id.", NamedTextColor.RED); }
            }
            case "removebed" -> {
                if (args.length < 3) { msg(sender, "Usage: /bw sb removebed <team>", NamedTextColor.RED); return; }
                TeamColor c = TeamColor.fromString(args[2]);
                if (c == null) { msg(sender, "Unknown team.", NamedTextColor.RED); return; }
                boolean had = plugin.getSetupConfig().getTeamBeds().containsKey(c);
                plugin.getSetupConfig().removeTeamBed(c);
                plugin.getSetupConfig().save();
                msg(sender, had ? "Removed " + c.displayName() + " bed." : c.displayName() + " had no bed set.",
                        had ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
            }
            case "mirror" -> {
                if (args.length < 4) { msg(sender, "Usage: /bw sb mirror <sourceTeam> <targetTeam>", NamedTextColor.RED); return; }
                TeamColor src = TeamColor.fromString(args[2]);
                TeamColor tgt = TeamColor.fromString(args[3]);
                if (src == null || tgt == null) { msg(sender, "Unknown team.", NamedTextColor.RED); return; }
                boolean ok = plugin.getSetupMode().mirror(src, tgt);
                msg(sender, ok ? "Mirrored " + src + " -> " + tgt + " about lobby spawn." :
                        "Mirror failed — ensure lobby spawn + source bed/spawn are set.", ok ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            default -> msg(sender, "Unknown sb subcommand.", NamedTextColor.RED);
        }
    }

    private void handleMap(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        var cfg = plugin.getSetupConfig();
        if (args.length < 2) {
            msg(sender, "Usage: /bw map <create|switch|list|delete> [id]", NamedTextColor.YELLOW);
            msg(sender, "Current map: " + cfg.getCurrentMapId(), NamedTextColor.GRAY);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> {
                if (args.length < 3) { msg(sender, "Usage: /bw map create <id>", NamedTextColor.RED); return; }
                String id = args[2].toLowerCase();
                if (!id.matches("[a-z0-9_-]{1,32}")) {
                    msg(sender, "Invalid id. Use letters, numbers, _ or - (max 32).", NamedTextColor.RED);
                    return;
                }
                boolean ok = cfg.createMap(id);
                msg(sender, ok ? "Created map '" + id + "' and switched to it. Set it up with /bw sb."
                        : "A map named '" + id + "' already exists.", ok ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "switch" -> {
                if (args.length < 3) { msg(sender, "Usage: /bw map switch <id>", NamedTextColor.RED); return; }
                if (plugin.getGameManager().isRunning()) {
                    msg(sender, "Cannot switch maps while a game is running.", NamedTextColor.RED);
                    return;
                }
                String id = args[2].toLowerCase();
                boolean ok = cfg.switchMap(id);
                msg(sender, ok ? "Switched to map '" + id + "'." : "No map named '" + id + "'.",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "list" -> {
                msg(sender, "Bedwars maps:", NamedTextColor.AQUA);
                for (String id : cfg.getMapIds()) {
                    boolean cur = id.equals(cfg.getCurrentMapId());
                    sender.sendMessage(Component.text((cur ? "  * " : "    ") + id + (cur ? " (current)" : ""),
                            cur ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                }
            }
            case "delete" -> {
                if (args.length < 3) { msg(sender, "Usage: /bw map delete <id>", NamedTextColor.RED); return; }
                String id = args[2].toLowerCase();
                if (!cfg.hasMap(id)) { msg(sender, "No map named '" + id + "'.", NamedTextColor.RED); return; }
                boolean ok = cfg.deleteMap(id);
                msg(sender, ok ? "Deleted map '" + id + "'. Current map: " + cfg.getCurrentMapId()
                        : "Cannot delete the only remaining map.", ok ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            default -> msg(sender, "Unknown map action. Use create|switch|list|delete.", NamedTextColor.RED);
        }
    }

    private void handleSave(CommandSender sender) {
        if (!requireAdmin(sender)) return;
        List<String> warnings = validate();
        plugin.getSetupConfig().save();
        if (warnings.isEmpty()) {
            msg(sender, "Setup saved. Map looks complete!", NamedTextColor.GREEN);
        } else {
            msg(sender, "Setup saved with warnings:", NamedTextColor.YELLOW);
            for (String w : warnings) sender.sendMessage(Component.text("  ! " + w, NamedTextColor.GOLD));
        }
    }

    private List<String> validate() {
        List<String> warnings = new ArrayList<>();
        var cfg = plugin.getSetupConfig();
        if (cfg.getLobbySpawn() == null) warnings.add("Lobby spawn is not set.");
        Set<TeamColor> withBed = cfg.getTeamBeds().keySet();
        for (TeamColor c : withBed) {
            if (!cfg.getTeamSpawns().containsKey(c)) warnings.add(c + " has a bed but no spawn point.");
            boolean hasGen = false;
            for (GeneratorPoint g : cfg.getGenerators()) {
                if (g.team == c) { hasGen = true; break; }
            }
            if (!hasGen) warnings.add(c + " has no nearby team generator.");
        }
        for (TeamColor c : cfg.getTeamSpawns().keySet()) {
            if (!withBed.contains(c)) warnings.add(c + " has a spawn but no bed.");
        }
        if (cfg.configuredTeams().size() < 2) warnings.add("Fewer than 2 fully-configured teams.");
        boolean pubGen = cfg.getGenerators().stream().anyMatch(g -> g.team == null);
        if (!pubGen) warnings.add("No public (diamond/emerald) generators placed.");
        return warnings;
    }

    private void handleTest(CommandSender sender) {
        if (!requireAdmin(sender)) return;
        if (plugin.getGameManager().isRunning()) { msg(sender, "A game is already running.", NamedTextColor.RED); return; }
        // Populate lobby with everyone currently in the bedwars world
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getGameManager().isInBedwarsWorld(p)) {
                plugin.getGameManager().getLobbyPlayers().add(p.getUniqueId());
            }
        }
        boolean ok = plugin.getGameManager().startMatch();
        msg(sender, ok ? "Test match started from in-memory setup." : "Could not start — check setup.", ok ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    private void handleStart(CommandSender sender) {
        if (!requireAdmin(sender)) return;
        if (plugin.getGameManager().isRunning()) { msg(sender, "Already running.", NamedTextColor.RED); return; }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getGameManager().isInBedwarsWorld(p)) {
                plugin.getGameManager().getLobbyPlayers().add(p.getUniqueId());
            }
        }
        boolean ok = plugin.getGameManager().startMatch();
        msg(sender, ok ? "Game started." : "Could not start — check setup.", ok ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    private void handleStop(CommandSender sender) {
        if (!requireAdmin(sender)) return;
        plugin.getGameManager().fullReset();
        msg(sender, "Game stopped and arena reset.", NamedTextColor.YELLOW);
    }

    private void handleJoin(CommandSender sender) {
        if (!(sender instanceof Player player)) { msg(sender, "Players only.", NamedTextColor.RED); return; }
        var world = plugin.getServer().getWorld(plugin.getSetupConfig().getWorldName());
        if (world == null) { msg(sender, "Bedwars world not loaded.", NamedTextColor.RED); return; }
        var lobby = plugin.getSetupConfig().getLobbySpawn();
        player.teleport(lobby != null ? lobby : world.getSpawnLocation());
    }

    private void handleStats(CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        var sm = plugin.getStatsManager();
        var id = player.getUniqueId();
        msg(sender, "Your Bedwars stats:", NamedTextColor.AQUA);
        sender.sendMessage(Component.text("  Wins: " + sm.getStat(id, "wins")
                + "  Kills: " + sm.getStat(id, "kills")
                + "  Final Kills: " + sm.getStat(id, "final_kills")
                + "  Beds: " + sm.getStat(id, "beds_broken"), NamedTextColor.GRAY));
    }

    private void help(CommandSender sender) {
        msg(sender, "AspireBedwars commands:", NamedTextColor.GOLD);
        sender.sendMessage(Component.text("  /bw join | stats", NamedTextColor.GRAY));
        if (sender.hasPermission(ADMIN)) {
            sender.sendMessage(Component.text("  /bw sb [show|hide|list|remove <id>|removebed <team>|mirror <src> <tgt>]", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /bw map [create|switch|list|delete] <id>", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /bw save | test | start | stop | reload", NamedTextColor.GRAY));
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission(ADMIN)) {
            msg(sender, "You lack permission (" + ADMIN + ").", NamedTextColor.RED);
            return false;
        }
        return true;
    }

    private void msg(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(Component.text("[Bedwars] ", NamedTextColor.GOLD).append(Component.text(text, color)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("join", "stats", "sb", "map", "save", "test", "start", "stop", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sb")) {
            return filter(Arrays.asList("show", "hide", "list", "remove", "removebed", "mirror"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("map")) {
            return filter(Arrays.asList("create", "switch", "list", "delete"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("map")
                && (args[1].equalsIgnoreCase("switch") || args[1].equalsIgnoreCase("delete"))) {
            return filter(new ArrayList<>(plugin.getSetupConfig().getMapIds()), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("sb") && args[1].equalsIgnoreCase("removebed")) {
            List<String> teams = new ArrayList<>();
            for (TeamColor c : TeamColor.values()) teams.add(c.name());
            return filter(teams, args[2]);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("sb") && args[1].equalsIgnoreCase("mirror")) {
            List<String> teams = new ArrayList<>();
            for (TeamColor c : TeamColor.values()) teams.add(c.name());
            return filter(teams, args[args.length - 1]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase().startsWith(prefix.toLowerCase())) out.add(o);
        return out;
    }
}

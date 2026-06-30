package com.aspireserver.creative.listeners;

import com.aspireserver.creative.AspireCreative;
import com.aspireserver.creative.plot.CreativePlot;
import com.aspireserver.creative.plot.PlotManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;

public class WorldEditLimiter implements Listener {

    private final PlotManager plotManager;
    private final AspireCreative plugin;

    private static final Set<String> WE_COMMANDS = Set.of(
        "//set", "//replace", "//fill", "//stack", "//move", "//copy",
        "//paste", "//drain", "//fixwater", "//sphere", "//cyl",
        "//hsphere", "//hcyl", "//pyramid", "//hpyramid",
        "//walls", "//overlay", "//naturalize", "//smooth",
        "//regen", "//forest", "//flora", "//undo", "//redo"
    );

    public WorldEditLimiter(PlotManager plotManager, AspireCreative plugin) {
        this.plotManager = plotManager;
        this.plugin = plugin;
    }

    private static final Set<String> BUILTIN_COMMANDS = Set.of(
        "//wand", "//set", "//replace", "//walls", "//hollow",
        "//line", "//layer", "//clear", "//pos1", "//pos2"
    );

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("aspire.admin.bypass")) return;

        String worldName = player.getWorld().getName();
        if (!worldName.startsWith("creative_")) return;

        String message = event.getMessage().toLowerCase();
        String baseCmd = message.split(" ")[0];

        // Let built-in wand commands pass through to CreativeWandManager
        if (BUILTIN_COMMANDS.contains(baseCmd)) return;

        boolean isWeCommand = WE_COMMANDS.contains(baseCmd)
            || baseCmd.startsWith("//")
            || baseCmd.startsWith("/worldedit")
            || baseCmd.startsWith("/we");

        if (!isWeCommand) return;

        CreativePlot plot = plotManager.getPlotAt(player.getLocation());
        if (plot == null || !plot.canAccess(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(Component.text("WorldEdit is only allowed on your own plot!", NamedTextColor.RED));
            return;
        }

        if (baseCmd.equals("//set") || baseCmd.equals("//replace") || baseCmd.equals("//fill")) {
            int plotVolume = plot.getTier().getSize() * plot.getTier().getSize() * 256;
            if (plotVolume > plotManager.getMaxWorldEditVolume()) {
                player.sendMessage(Component.text("Large operations run async. This may take a moment.", NamedTextColor.YELLOW));
            }
        }
    }
}

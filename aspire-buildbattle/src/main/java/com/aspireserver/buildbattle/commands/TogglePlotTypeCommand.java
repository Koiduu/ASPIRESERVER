package com.aspireserver.buildbattle.commands;

import com.aspireserver.buildbattle.AspireBuildBattle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TogglePlotTypeCommand implements CommandExecutor {

    private final AspireBuildBattle plugin;
    private static final Map<UUID, String> playerPlotType = new HashMap<>();

    public TogglePlotTypeCommand(AspireBuildBattle plugin) {
        this.plugin = plugin;
    }

    public static String getPlotType(UUID player) {
        return playerPlotType.getOrDefault(player, "solo");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("aspire.buildbattle.admin")) {
            player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        String type = switch (label.toLowerCase()) {
            case "togglesolo" -> "solo";
            case "toggleteams" -> "teams";
            case "togglepro" -> "pro";
            default -> "solo";
        };

        playerPlotType.put(player.getUniqueId(), type);
        player.sendMessage(Component.text("Plot mode set to: " + type.toUpperCase(), NamedTextColor.GREEN));
        player.sendMessage(Component.text("Plots you create with /plotset will now be tagged as '" + type + "'.", NamedTextColor.GRAY));
        return true;
    }
}

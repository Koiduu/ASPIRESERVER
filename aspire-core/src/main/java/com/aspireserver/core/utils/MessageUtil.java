package com.aspireserver.core.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

public final class MessageUtil {

    public static final String PREFIX_RAW = "[Aspire] ";
    public static final Component PREFIX = Component.text("[Aspire] ", NamedTextColor.GOLD, TextDecoration.BOLD);

    private MessageUtil() {}

    public static void send(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.GRAY)));
    }

    public static void sendError(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.RED)));
    }

    public static void sendSuccess(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.GREEN)));
    }

    public static void sendInfo(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.AQUA)));
    }

    public static Component colored(String text, NamedTextColor color) {
        return Component.text(text, color);
    }
}

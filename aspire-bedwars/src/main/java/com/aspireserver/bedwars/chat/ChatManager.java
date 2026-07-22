package com.aspireserver.bedwars.chat;

import com.aspireserver.bedwars.AspireBedwars;
import com.aspireserver.bedwars.team.TeamColor;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Team/shout chat routing using the modern async chat event. All team lookups
 * here read from a thread-safe cache (never Bukkit API) because AsyncChatEvent
 * can fire off the main thread.
 */
public class ChatManager implements Listener {

    private final AspireBedwars plugin;
    // thread-safe caches populated on the main thread at assignment time
    private final Map<UUID, TeamColor> teamCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> spectatorCache = new ConcurrentHashMap<>();
    private volatile boolean gameRunning = false;

    public ChatManager(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    public void setTeam(UUID uuid, TeamColor color) {
        if (color == null) teamCache.remove(uuid);
        else teamCache.put(uuid, color);
    }

    public void setSpectator(UUID uuid, boolean spectator) { spectatorCache.put(uuid, spectator); }
    public void setGameRunning(boolean running) { this.gameRunning = running; }

    public void clear() {
        teamCache.clear();
        spectatorCache.clear();
        gameRunning = false;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        // Only touch chat for game participants. Membership is read from the
        // thread-safe caches so we never call a Bukkit API off the main thread.
        if (!gameRunning) return;
        UUID senderId = sender.getUniqueId();
        boolean participant = teamCache.containsKey(senderId) || Boolean.TRUE.equals(spectatorCache.get(senderId));
        if (!participant) return;

        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
        boolean shout = raw.startsWith("!") || raw.startsWith("@");
        final String body = shout ? raw.substring(1).trim() : raw;

        TeamColor senderTeam = teamCache.get(sender.getUniqueId());
        boolean spectator = Boolean.TRUE.equals(spectatorCache.get(sender.getUniqueId()));

        // Determine recipients from the cache (no Bukkit calls)
        event.viewers().removeIf(audience -> {
            if (!(audience instanceof Player p)) return false; // keep console
            if (shout || spectator) {
                // shout goes to everyone in game; spectators only talk to spectators
                if (spectator) return !Boolean.TRUE.equals(spectatorCache.get(p.getUniqueId()));
                return false;
            }
            // team chat: only same-team members
            TeamColor recTeam = teamCache.get(p.getUniqueId());
            return senderTeam == null || recTeam != senderTeam;
        });

        final TeamColor teamColor = senderTeam;
        final boolean isShout = shout;
        final boolean isSpec = spectator;

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component prefix;
            if (isSpec) {
                prefix = Component.text("[SPECTATOR] ", NamedTextColor.GRAY);
            } else if (isShout) {
                prefix = Component.text("[ALL] ", NamedTextColor.YELLOW);
            } else {
                prefix = Component.text("[TEAM] ", NamedTextColor.AQUA);
            }
            Component teamTag = teamColor != null
                    ? Component.text("[" + teamColor.displayName() + "] ", teamColor.textColor())
                    : Component.empty();
            NamedTextColor nameColor = teamColor != null ? teamColor.textColor() : NamedTextColor.WHITE;
            return prefix
                    .append(teamTag)
                    .append(Component.text(source.getName(), nameColor))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(Component.text(body, NamedTextColor.WHITE));
        });
    }
}

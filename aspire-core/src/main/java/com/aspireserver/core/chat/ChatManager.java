package com.aspireserver.core.chat;

import com.aspireserver.core.AspireCore;
import com.aspireserver.core.party.Party;
import com.aspireserver.core.rank.RankManager;
import com.aspireserver.core.utils.MessageUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ChatManager implements Listener {

    private final AspireCore plugin;
    private final Set<UUID> partyChatToggled;
    private final Set<UUID> staffChatToggled;

    public ChatManager(AspireCore plugin) {
        this.plugin = plugin;
        this.partyChatToggled = new HashSet<>();
        this.staffChatToggled = new HashSet<>();
    }

    public void togglePartyChat(UUID player) {
        if (partyChatToggled.contains(player)) {
            partyChatToggled.remove(player);
        } else {
            partyChatToggled.add(player);
            staffChatToggled.remove(player);
        }
    }

    public boolean isInPartyChat(UUID player) {
        return partyChatToggled.contains(player);
    }

    public void toggleStaffChat(UUID player) {
        if (staffChatToggled.contains(player)) {
            staffChatToggled.remove(player);
        } else {
            staffChatToggled.add(player);
            partyChatToggled.remove(player);
        }
    }

    public boolean isInStaffChat(UUID player) {
        return staffChatToggled.contains(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getAdminManager().isMuted(uuid)) {
            event.setCancelled(true);
            MessageUtil.sendError(player, "You are muted!");
            return;
        }

        if (isInStaffChat(uuid)) {
            event.setCancelled(true);
            String msg = PlainTextComponentSerializer.plainText().serialize(event.message());
            Component staffMsg = Component.text("[SC] ", NamedTextColor.DARK_RED)
                .append(Component.text(player.getName() + ": ", NamedTextColor.RED))
                .append(Component.text(msg, NamedTextColor.WHITE));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("aspire.admin.staffchat")) {
                    p.sendMessage(staffMsg);
                }
            }
            return;
        }

        if (isInPartyChat(uuid)) {
            event.setCancelled(true);
            Party party = plugin.getPartyManager().getParty(uuid);
            if (party == null) {
                partyChatToggled.remove(uuid);
                return;
            }
            String msg = PlainTextComponentSerializer.plainText().serialize(event.message());
            plugin.getPartyManager().broadcastToParty(party, "[Party] " + player.getName() + ": " + msg);
            return;
        }

        RankManager rankManager = plugin.getRankManager();
        if (rankManager != null) {
            event.setCancelled(true);
            String msg = PlainTextComponentSerializer.plainText().serialize(event.message());
            Component formatted = rankManager.getChatFormat(player, msg);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(formatted);
            }
            Bukkit.getConsoleSender().sendMessage(formatted);
        }
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (plugin.getAdminManager().isBanned(uuid)) {
            String reason = plugin.getAdminManager().getBanReason(uuid);
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                Component.text("You are banned: " + reason, NamedTextColor.RED));
        }
    }
}

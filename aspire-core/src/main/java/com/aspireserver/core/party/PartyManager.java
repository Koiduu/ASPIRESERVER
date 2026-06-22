package com.aspireserver.core.party;

import com.aspireserver.core.AspireCore;
import com.aspireserver.core.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PartyManager {

    private final AspireCore plugin;
    private final Map<UUID, Party> parties;
    private final Map<UUID, UUID> playerPartyMap;

    public PartyManager(AspireCore plugin) {
        this.plugin = plugin;
        this.parties = new HashMap<>();
        this.playerPartyMap = new HashMap<>();
    }

    public Party getParty(UUID player) {
        UUID partyLeader = playerPartyMap.get(player);
        if (partyLeader == null) return null;
        return parties.get(partyLeader);
    }

    public Party getPartyByLeader(UUID leader) {
        return parties.get(leader);
    }

    public boolean isInParty(UUID player) {
        return playerPartyMap.containsKey(player);
    }

    public void createParty(Player leader) {
        if (isInParty(leader.getUniqueId())) {
            MessageUtil.sendError(leader, "You are already in a party!");
            return;
        }
        Party party = new Party(leader.getUniqueId());
        parties.put(leader.getUniqueId(), party);
        playerPartyMap.put(leader.getUniqueId(), leader.getUniqueId());
        MessageUtil.sendSuccess(leader, "Party created!");
    }

    public void invite(Player inviter, Player target) {
        Party currentParty = getParty(inviter.getUniqueId());
        if (currentParty == null) {
            createParty(inviter);
            currentParty = getParty(inviter.getUniqueId());
        }

        final Party party = currentParty;

        if (!party.isLeader(inviter.getUniqueId()) && !party.isAllInvite()) {
            MessageUtil.sendError(inviter, "Only the party leader can invite players!");
            return;
        }

        if (party.isMember(target.getUniqueId())) {
            MessageUtil.sendError(inviter, target.getName() + " is already in your party!");
            return;
        }

        if (isInParty(target.getUniqueId())) {
            MessageUtil.sendError(inviter, target.getName() + " is already in another party!");
            return;
        }

        party.invite(target.getUniqueId());
        MessageUtil.sendSuccess(inviter, "Invited " + target.getName() + " to your party!");
        MessageUtil.sendInfo(target, inviter.getName() + " invited you to their party! Use /party accept to join.");

        Bukkit.getScheduler().runTaskLater(plugin, () -> party.getInvites().remove(target.getUniqueId()), 60 * 20L);
    }

    public void acceptInvite(Player player) {
        UUID playerUuid = player.getUniqueId();
        for (Party party : parties.values()) {
            if (party.hasInvite(playerUuid)) {
                party.addMember(playerUuid);
                playerPartyMap.put(playerUuid, party.getLeader());
                broadcastToParty(party, player.getName() + " joined the party!");
                return;
            }
        }
        MessageUtil.sendError(player, "You have no pending party invitations!");
    }

    public void leave(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendError(player, "You are not in a party!");
            return;
        }

        if (party.isLeader(player.getUniqueId())) {
            disband(player);
            return;
        }

        party.removeMember(player.getUniqueId());
        playerPartyMap.remove(player.getUniqueId());
        MessageUtil.send(player, "You left the party.");
        broadcastToParty(party, player.getName() + " left the party.");
    }

    public void disband(Player leader) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            MessageUtil.sendError(leader, "You are not a party leader!");
            return;
        }

        broadcastToParty(party, "The party has been disbanded.");
        for (UUID member : party.getMembers()) {
            playerPartyMap.remove(member);
        }
        parties.remove(leader.getUniqueId());
    }

    public void kickOffline(Player leader) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            MessageUtil.sendError(leader, "You are not a party leader!");
            return;
        }

        int kicked = 0;
        for (UUID member : new HashSet<>(party.getMembers())) {
            if (Bukkit.getPlayer(member) == null && !party.isLeader(member)) {
                party.removeMember(member);
                playerPartyMap.remove(member);
                kicked++;
            }
        }
        MessageUtil.sendSuccess(leader, "Kicked " + kicked + " offline player(s).");
    }

    public void togglePrivate(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null || !party.isLeader(player.getUniqueId())) {
            MessageUtil.sendError(player, "You are not a party leader!");
            return;
        }
        party.setPrivateGames(!party.isPrivateGames());
        broadcastToParty(party, "Private games " + (party.isPrivateGames() ? "enabled" : "disabled") + ".");
    }

    public void toggleAllInvite(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null || !party.isLeader(player.getUniqueId())) {
            MessageUtil.sendError(player, "You are not a party leader!");
            return;
        }
        party.setAllInvite(!party.isAllInvite());
        broadcastToParty(party, "All invite " + (party.isAllInvite() ? "enabled" : "disabled") + ".");
    }

    public void listMembers(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendError(player, "You are not in a party!");
            return;
        }

        MessageUtil.sendInfo(player, "--- Party Members (" + party.size() + ") ---");
        for (UUID member : party.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            String name = p != null ? p.getName() : member.toString();
            String role = party.isLeader(member) ? " [Leader]" : "";
            String status = p != null ? " (Online)" : " (Offline)";
            MessageUtil.send(player, "  " + name + role + status);
        }
    }

    public void broadcastToParty(Party party, String message) {
        for (UUID member : party.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null) {
                MessageUtil.send(p, message);
            }
        }
    }

    private static class HashSet<T> extends java.util.HashSet<T> {
        public HashSet(java.util.Collection<? extends T> c) {
            super(c);
        }
    }
}

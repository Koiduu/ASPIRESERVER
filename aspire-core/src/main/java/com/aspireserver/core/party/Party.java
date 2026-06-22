package com.aspireserver.core.party;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Party {

    private final UUID leader;
    private final Set<UUID> members;
    private final Set<UUID> invites;
    private boolean privateGames;
    private boolean allInvite;

    public Party(UUID leader) {
        this.leader = leader;
        this.members = new HashSet<>();
        this.invites = new HashSet<>();
        this.members.add(leader);
        this.privateGames = false;
        this.allInvite = false;
    }

    public UUID getLeader() {
        return leader;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Set<UUID> getInvites() {
        return invites;
    }

    public boolean isPrivateGames() {
        return privateGames;
    }

    public void setPrivateGames(boolean privateGames) {
        this.privateGames = privateGames;
    }

    public boolean isAllInvite() {
        return allInvite;
    }

    public void setAllInvite(boolean allInvite) {
        this.allInvite = allInvite;
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
        invites.remove(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public void invite(UUID uuid) {
        invites.add(uuid);
    }

    public boolean hasInvite(UUID uuid) {
        return invites.contains(uuid);
    }

    public int size() {
        return members.size();
    }
}

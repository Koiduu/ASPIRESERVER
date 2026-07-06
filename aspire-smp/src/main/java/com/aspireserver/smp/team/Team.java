package com.aspireserver.smp.team;

import java.util.*;

public class Team {

    private final String name;
    private final UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> invites = new HashSet<>();

    public Team(String name, UUID leader) {
        this.name = name;
        this.leader = leader;
    }

    public String getName() { return name; }
    public UUID getLeader() { return leader; }
    public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }

    public Set<UUID> getAllMembers() {
        Set<UUID> all = new HashSet<>(members);
        all.add(leader);
        return all;
    }

    public void addMember(UUID uuid) { members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); }

    public void addInvite(UUID uuid) { invites.add(uuid); }
    public boolean hasInvite(UUID uuid) { return invites.contains(uuid); }
    public void removeInvite(UUID uuid) { invites.remove(uuid); }
}

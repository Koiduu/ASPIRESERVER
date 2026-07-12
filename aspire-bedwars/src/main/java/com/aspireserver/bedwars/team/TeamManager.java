package com.aspireserver.bedwars.team;

import com.aspireserver.bedwars.AspireBedwars;
import org.bukkit.entity.Player;

import java.util.*;

public class TeamManager {

    private final AspireBedwars plugin;
    private final Map<TeamColor, BedwarsTeam> teams = new EnumMap<>(TeamColor.class);
    private final Map<UUID, TeamColor> playerTeam = new HashMap<>();
    // Preference chosen in the lobby selector before the game starts
    private final Map<UUID, TeamColor> preferences = new HashMap<>();

    public TeamManager(AspireBedwars plugin) {
        this.plugin = plugin;
    }

    /** Build teams from the configured colors for a new match. */
    public void initTeams(Set<TeamColor> colors) {
        teams.clear();
        playerTeam.clear();
        for (TeamColor c : colors) {
            teams.put(c, new BedwarsTeam(c));
        }
    }

    public void clear() {
        teams.clear();
        playerTeam.clear();
        preferences.clear();
    }

    public Collection<BedwarsTeam> getTeams() { return teams.values(); }
    public BedwarsTeam getTeam(TeamColor color) { return teams.get(color); }

    public BedwarsTeam getTeam(UUID uuid) {
        TeamColor c = playerTeam.get(uuid);
        return c == null ? null : teams.get(c);
    }

    public TeamColor getTeamColor(UUID uuid) { return playerTeam.get(uuid); }

    public boolean sameTeam(UUID a, UUID b) {
        TeamColor ca = playerTeam.get(a);
        TeamColor cb = playerTeam.get(b);
        return ca != null && ca == cb;
    }

    public void assign(Player player, TeamColor color) {
        BedwarsTeam team = teams.get(color);
        if (team == null) return;
        removeFromTeam(player.getUniqueId());
        team.addMember(player.getUniqueId());
        playerTeam.put(player.getUniqueId(), color);
    }

    public void removeFromTeam(UUID uuid) {
        TeamColor c = playerTeam.remove(uuid);
        if (c != null) {
            BedwarsTeam t = teams.get(c);
            if (t != null) t.removeMember(uuid);
        }
    }

    public void setPreference(UUID uuid, TeamColor color) { preferences.put(uuid, color); }
    public TeamColor getPreference(UUID uuid) { return preferences.get(uuid); }
    public Map<UUID, TeamColor> getPreferences() { return preferences; }

    /**
     * Balance players across teams. Honors preferences where capacity allows,
     * then fills remaining players into the emptiest teams.
     */
    public void balanceAndAssign(List<Player> players, int teamSize) {
        List<TeamColor> colors = new ArrayList<>(teams.keySet());
        // Honor preferences first
        List<Player> unassigned = new ArrayList<>();
        for (Player p : players) {
            TeamColor pref = preferences.get(p.getUniqueId());
            if (pref != null && teams.containsKey(pref) && teams.get(pref).getMembers().size() < teamSize) {
                assign(p, pref);
            } else {
                unassigned.add(p);
            }
        }
        // Fill remaining into emptiest team
        for (Player p : unassigned) {
            TeamColor best = null;
            int bestCount = Integer.MAX_VALUE;
            for (TeamColor c : colors) {
                int count = teams.get(c).getMembers().size();
                if (count < bestCount) { bestCount = count; best = c; }
            }
            if (best != null) assign(p, best);
        }
    }

    public int aliveTeamCount(java.util.function.Predicate<UUID> onlineCheck) {
        int count = 0;
        for (BedwarsTeam t : teams.values()) {
            if (t.isAlive()) count++;
        }
        return count;
    }

    public BedwarsTeam soleSurvivor() {
        BedwarsTeam survivor = null;
        for (BedwarsTeam t : teams.values()) {
            if (t.isAlive()) {
                if (survivor != null) return null; // more than one alive
                survivor = t;
            }
        }
        return survivor;
    }
}

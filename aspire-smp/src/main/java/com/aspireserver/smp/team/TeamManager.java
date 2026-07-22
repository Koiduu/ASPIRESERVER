package com.aspireserver.smp.team;

import com.aspireserver.smp.AspireSMP;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamManager {

    private final AspireSMP plugin;
    private final File dataFile;
    private final Map<String, Team> teams = new HashMap<>();
    private final Map<UUID, String> playerTeams = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> kickVotes = new HashMap<>();

    public TeamManager(AspireSMP plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "teams.yml");
        loadTeams();
    }

    public boolean createTeam(String name, UUID leader) {
        if (teams.containsKey(name.toLowerCase())) return false;
        if (playerTeams.containsKey(leader)) return false;

        Team team = new Team(name, leader);
        teams.put(name.toLowerCase(), team);
        playerTeams.put(leader, name.toLowerCase());
        saveTeams();
        return true;
    }

    public boolean disbandTeam(String name, UUID requester) {
        Team team = teams.get(name.toLowerCase());
        if (team == null) return false;
        if (!team.getLeader().equals(requester)) return false;

        for (UUID member : team.getMembers()) {
            playerTeams.remove(member);
        }
        playerTeams.remove(team.getLeader());
        teams.remove(name.toLowerCase());
        saveTeams();
        return true;
    }

    public boolean joinTeam(String name, UUID player) {
        if (playerTeams.containsKey(player)) return false;
        Team team = teams.get(name.toLowerCase());
        if (team == null) return false;

        team.addMember(player);
        playerTeams.put(player, name.toLowerCase());
        saveTeams();
        return true;
    }

    public boolean leaveTeam(UUID player) {
        String teamName = playerTeams.get(player);
        if (teamName == null) return false;

        Team team = teams.get(teamName);
        if (team == null) return false;

        if (team.getLeader().equals(player)) {
            return disbandTeam(teamName, player);
        }

        team.removeMember(player);
        playerTeams.remove(player);
        saveTeams();
        return true;
    }

    public boolean invitePlayer(UUID leader, UUID target) {
        String teamName = playerTeams.get(leader);
        if (teamName == null) return false;
        Team team = teams.get(teamName);
        if (team == null) return false;
        if (!team.getLeader().equals(leader)) return false;
        team.addInvite(target);
        return true;
    }

    public boolean hasInvite(UUID player, String teamName) {
        Team team = teams.get(teamName.toLowerCase());
        if (team == null) return false;
        return team.hasInvite(player);
    }

    public void clearInvite(UUID player, String teamName) {
        Team team = teams.get(teamName.toLowerCase());
        if (team != null) team.removeInvite(player);
    }

    public boolean voteKick(UUID voter, UUID target) {
        String teamName = playerTeams.get(voter);
        if (teamName == null) return false;
        String targetTeam = playerTeams.get(target);
        if (!teamName.equals(targetTeam)) return false;

        Team team = teams.get(teamName);
        if (team == null) return false;

        kickVotes.computeIfAbsent(target, k -> new HashMap<>()).put(voter, System.currentTimeMillis());

        // Check if 60% of team voted
        int teamSize = team.getAllMembers().size();
        Map<UUID, Long> votes = kickVotes.get(target);
        // Remove stale votes (older than 5 minutes)
        votes.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > 300000);

        long validVotes = votes.keySet().stream()
                .filter(uuid -> teamName.equals(playerTeams.get(uuid)))
                .count();

        if (validVotes >= Math.ceil(teamSize * 0.6)) {
            // Kick the player
            team.removeMember(target);
            playerTeams.remove(target);
            kickVotes.remove(target);
            saveTeams();
            return true;
        }
        return false;
    }

    public int getKickVoteCount(UUID target) {
        Map<UUID, Long> votes = kickVotes.get(target);
        if (votes == null) return 0;
        votes.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > 300000);
        return votes.size();
    }

    public int getKickVotesNeeded(UUID target) {
        String teamName = playerTeams.get(target);
        if (teamName == null) return 0;
        Team team = teams.get(teamName);
        if (team == null) return 0;
        return (int) Math.ceil(team.getAllMembers().size() * 0.6);
    }

    public Team getPlayerTeam(UUID player) {
        String teamName = playerTeams.get(player);
        if (teamName == null) return null;
        return teams.get(teamName);
    }

    public String getPlayerTeamName(UUID player) {
        return playerTeams.get(player);
    }

    public boolean areTeammates(UUID player1, UUID player2) {
        String team1 = playerTeams.get(player1);
        String team2 = playerTeams.get(player2);
        if (team1 == null || team2 == null) return false;
        return team1.equals(team2);
    }

    public Collection<Team> getAllTeams() {
        return teams.values();
    }

    private void loadTeams() {
        if (!dataFile.exists()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        if (!config.contains("teams")) return;
        for (String key : config.getConfigurationSection("teams").getKeys(false)) {
            String path = "teams." + key;
            String name = config.getString(path + ".name", key);
            UUID leader = UUID.fromString(config.getString(path + ".leader"));
            Team team = new Team(name, leader);
            playerTeams.put(leader, key);

            List<String> members = config.getStringList(path + ".members");
            for (String m : members) {
                UUID uuid = UUID.fromString(m);
                team.addMember(uuid);
                playerTeams.put(uuid, key);
            }
            teams.put(key, team);
        }
    }

    public void saveTeams() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Team> entry : teams.entrySet()) {
            String path = "teams." + entry.getKey();
            Team team = entry.getValue();
            config.set(path + ".name", team.getName());
            config.set(path + ".leader", team.getLeader().toString());
            List<String> members = new ArrayList<>();
            for (UUID m : team.getMembers()) {
                members.add(m.toString());
            }
            config.set(path + ".members", members);
        }
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save teams.yml");
        }
    }
}

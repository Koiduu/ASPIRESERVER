package com.aspireserver.buildbattle.game;

public enum GameMode {
    SOLO(5 * 60, 24, 1, false, 16),
    TEAMS(7 * 60, 32, 2, false, 32),
    PRO_SOLO(10 * 60, 32, 1, true, 16),
    PRO_TEAMS(10 * 60, 32, 2, true, 32);

    private final int durationSeconds;
    private final int plotSize;
    private final int teamSize;
    private final boolean worldEditEnabled;
    private final int maxPlayers;

    GameMode(int durationSeconds, int plotSize, int teamSize, boolean worldEditEnabled, int maxPlayers) {
        this.durationSeconds = durationSeconds;
        this.plotSize = plotSize;
        this.teamSize = teamSize;
        this.worldEditEnabled = worldEditEnabled;
        this.maxPlayers = maxPlayers;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getPlotSize() {
        return plotSize;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public boolean isWorldEditEnabled() {
        return worldEditEnabled;
    }

    public boolean isTeamMode() {
        return teamSize > 1;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public String getDisplayName() {
        return switch (this) {
            case SOLO -> "Solo";
            case TEAMS -> "Teams";
            case PRO_SOLO -> "Pro Solo";
            case PRO_TEAMS -> "Pro Teams";
        };
    }
}

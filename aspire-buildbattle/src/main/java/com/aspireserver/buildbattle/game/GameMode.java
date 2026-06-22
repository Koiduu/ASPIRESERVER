package com.aspireserver.buildbattle.game;

public enum GameMode {
    SOLO(5 * 60, 24, 1, false),
    TEAMS(7 * 60, 32, 2, false),
    PRO_SOLO(10 * 60, 32, 1, true),
    PRO_TEAMS(10 * 60, 32, 2, true);

    private final int durationSeconds;
    private final int plotSize;
    private final int teamSize;
    private final boolean worldEditEnabled;

    GameMode(int durationSeconds, int plotSize, int teamSize, boolean worldEditEnabled) {
        this.durationSeconds = durationSeconds;
        this.plotSize = plotSize;
        this.teamSize = teamSize;
        this.worldEditEnabled = worldEditEnabled;
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

    public String getDisplayName() {
        return switch (this) {
            case SOLO -> "Solo";
            case TEAMS -> "Teams";
            case PRO_SOLO -> "Pro Solo";
            case PRO_TEAMS -> "Pro Teams";
        };
    }
}

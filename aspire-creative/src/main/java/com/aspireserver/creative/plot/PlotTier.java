package com.aspireserver.creative.plot;

public enum PlotTier {
    SMALL("Small", 75, "creative_small"),
    MEDIUM("Medium", 251, "creative_medium"),
    LARGE("Large", 501, "creative_large");

    private final String displayName;
    private final int size;
    private final String worldName;

    PlotTier(String displayName, int size, String worldName) {
        this.displayName = displayName;
        this.size = size;
        this.worldName = worldName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSize() {
        return size;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getPlotSpacing() {
        return size + 7;
    }
}

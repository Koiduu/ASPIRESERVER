package com.aspireserver.core.title;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public enum BbTitle {

    ROOKIE("Rookie", NamedTextColor.WHITE, false),
    UNTRAINED("Untrained", NamedTextColor.GRAY, false),
    AMATEUR("Amateur", NamedTextColor.DARK_GRAY, false),
    PROSPECT("Prospect", NamedTextColor.GREEN, false),
    APPRENTICE("Apprentice", NamedTextColor.DARK_GREEN, false),
    EXPERIENCED("Experienced", NamedTextColor.AQUA, false),
    SEASONED("Seasoned", NamedTextColor.DARK_AQUA, false),
    TRAINED("Trained", NamedTextColor.BLUE, false),
    SKILLED("Skilled", NamedTextColor.DARK_BLUE, false),
    TALENTED("Talented", NamedTextColor.DARK_PURPLE, false),
    PROFESSIONAL("Professional", NamedTextColor.LIGHT_PURPLE, false),
    ARTISAN("Artisan", NamedTextColor.RED, false),
    EXPERT("Expert", NamedTextColor.DARK_RED, false),
    MASTER("Master", NamedTextColor.GOLD, false),
    LEGEND("Legend", NamedTextColor.GREEN, true),
    GRANDMASTER("Grandmaster", NamedTextColor.AQUA, true),
    CELESTIAL("Celestial", NamedTextColor.LIGHT_PURPLE, true),
    DIVINE("Divine", NamedTextColor.RED, true),
    ASCENDED("Ascended", NamedTextColor.GOLD, true);

    private final String displayName;
    private final NamedTextColor color;
    private final boolean bold;

    BbTitle(String displayName, NamedTextColor color, boolean bold) {
        this.displayName = displayName;
        this.color = color;
        this.bold = bold;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public boolean isBold() {
        return bold;
    }

    public Component format() {
        Component text = Component.text("[" + displayName + "]", color);
        if (bold) {
            text = text.decorate(TextDecoration.BOLD);
        }
        return text;
    }

    public static BbTitle fromString(String name) {
        if (name == null) return null;
        for (BbTitle t : values()) {
            if (t.name().equalsIgnoreCase(name) || t.displayName.equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }
}

package com.aspireserver.core.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public enum Rank {

    DEFAULT("Default", null, null, null),
    VIP("VIP", NamedTextColor.GREEN, null, null),
    VIP_PLUS("VIP+", NamedTextColor.GREEN, NamedTextColor.GOLD, "+"),
    MVP("MVP", NamedTextColor.AQUA, null, null),
    MVP_PLUS("MVP+", NamedTextColor.AQUA, NamedTextColor.RED, "+"),
    MVP_PLUS_PLUS("MVP++", NamedTextColor.GOLD, NamedTextColor.RED, "++");

    private final String displayName;
    private final TextColor baseColor;
    private final TextColor defaultPlusColor;
    private final String plusSymbol;

    Rank(String displayName, TextColor baseColor, TextColor defaultPlusColor, String plusSymbol) {
        this.displayName = displayName;
        this.baseColor = baseColor;
        this.defaultPlusColor = defaultPlusColor;
        this.plusSymbol = plusSymbol;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TextColor getBaseColor() {
        return baseColor;
    }

    public TextColor getDefaultPlusColor() {
        return defaultPlusColor;
    }

    public String getPlusSymbol() {
        return plusSymbol;
    }

    public Component formatTag(TextColor customPlusColor) {
        if (this == DEFAULT) {
            return Component.empty();
        }

        TextColor plusColor = customPlusColor != null ? customPlusColor : defaultPlusColor;

        if (plusSymbol == null) {
            return Component.text("[" + displayName + "] ", baseColor);
        }

        String baseName = displayName.replace("+", "");

        return Component.text("[", baseColor)
                .append(Component.text(baseName, baseColor))
                .append(Component.text(plusSymbol, plusColor))
                .append(Component.text("] ", baseColor));
    }

    public static Rank fromString(String name) {
        if (name == null) return DEFAULT;
        return switch (name.toUpperCase().replace(" ", "_").replace("+", "_PLUS")) {
            case "VIP" -> VIP;
            case "VIP_PLUS", "VIP+" -> VIP_PLUS;
            case "MVP" -> MVP;
            case "MVP_PLUS", "MVP+" -> MVP_PLUS;
            case "MVP_PLUS_PLUS", "MVP++", "MVP_PLUS_PLUS_PLUS" -> MVP_PLUS_PLUS;
            default -> DEFAULT;
        };
    }
}

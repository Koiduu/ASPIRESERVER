package com.aspireserver.core.npc;

public enum NpcAction {
    BUILD_BATTLE_SOLO("Build Battle - Solo", "bb join solo"),
    BUILD_BATTLE_TEAMS("Build Battle - Teams", "bb join teams"),
    BUILD_BATTLE_PRO_SOLO("Build Battle - Pro Solo", "bb join prosolo"),
    BUILD_BATTLE_PRO_TEAMS("Build Battle - Pro Teams", "bb join proteams"),
    WARP_SMP("Warp to SMP", null),
    WARP_CREATIVE("Warp to Creative", null),
    WARP_CHAMELEON("Warp to Chameleon", null),
    WARP_LOBBY("Warp to Lobby", null),
    CUSTOM_COMMAND("Custom Command", null);

    private final String displayName;
    private final String command;

    NpcAction(String displayName, String command) {
        this.displayName = displayName;
        this.command = command;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCommand() {
        return command;
    }
}

package com.aspireserver.core.npc;

import org.bukkit.Location;

import java.util.UUID;

public class NpcData {

    private final String id;
    private final String displayName;
    private NpcAction action;
    private Location location;
    private UUID entityUuid;
    private String skinName;
    private String customCommand;

    public NpcData(String id, String displayName, NpcAction action, Location location) {
        this.id = id;
        this.displayName = displayName;
        this.action = action;
        this.location = location;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public NpcAction getAction() { return action; }
    public void setAction(NpcAction action) { this.action = action; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public UUID getEntityUuid() { return entityUuid; }
    public void setEntityUuid(UUID entityUuid) { this.entityUuid = entityUuid; }
    public String getSkinName() { return skinName; }
    public void setSkinName(String skinName) { this.skinName = skinName; }
    public String getCustomCommand() { return customCommand; }
    public void setCustomCommand(String customCommand) { this.customCommand = customCommand; }
}

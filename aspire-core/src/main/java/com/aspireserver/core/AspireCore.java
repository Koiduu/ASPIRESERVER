package com.aspireserver.core;

import com.aspireserver.core.admin.AdminManager;
import com.aspireserver.core.admin.BanCommand;
import com.aspireserver.core.admin.KickCommand;
import com.aspireserver.core.admin.MuteCommand;
import com.aspireserver.core.admin.TempBanCommand;
import com.aspireserver.core.admin.WarnCommand;
import com.aspireserver.core.chat.ChatManager;
import com.aspireserver.core.chat.StaffChatCommand;
import com.aspireserver.core.commands.LobbyCommand;
import com.aspireserver.core.friends.FriendCommand;
import com.aspireserver.core.friends.FriendManager;
import com.aspireserver.core.npc.NpcCommand;
import com.aspireserver.core.npc.NpcListener;
import com.aspireserver.core.npc.NpcManager;
import com.aspireserver.core.party.PartyCommand;
import com.aspireserver.core.party.PartyManager;
import com.aspireserver.core.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class AspireCore extends JavaPlugin {

    private static AspireCore instance;
    private PartyManager partyManager;
    private FriendManager friendManager;
    private AdminManager adminManager;
    private ChatManager chatManager;
    private NpcManager npcManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        partyManager = new PartyManager(this);
        friendManager = new FriendManager(this);
        adminManager = new AdminManager(this);
        chatManager = new ChatManager(this);
        npcManager = new NpcManager(this);

        registerCommands();
        getServer().getPluginManager().registerEvents(chatManager, this);
        getServer().getPluginManager().registerEvents(new NpcListener(this, npcManager), this);

        Bukkit.getScheduler().runTaskLater(this, () -> npcManager.spawnAllNpcs(), 20L);

        getLogger().info(MessageUtil.PREFIX_RAW + "AspireCore enabled!");
    }

    @Override
    public void onDisable() {
        friendManager.saveData();
        adminManager.saveData();
        npcManager.despawnAll();
        npcManager.saveNpcs();
        getLogger().info(MessageUtil.PREFIX_RAW + "AspireCore disabled.");
    }

    private void registerCommands() {
        PartyCommand partyCommand = new PartyCommand(partyManager);
        getCommand("party").setExecutor(partyCommand);
        getCommand("party").setTabCompleter(partyCommand);
        getCommand("p").setExecutor(partyCommand);
        getCommand("p").setTabCompleter(partyCommand);
        getCommand("pc").setExecutor(partyCommand);

        FriendCommand friendCommand = new FriendCommand(friendManager);
        getCommand("friend").setExecutor(friendCommand);
        getCommand("friend").setTabCompleter(friendCommand);
        getCommand("f").setExecutor(friendCommand);
        getCommand("f").setTabCompleter(friendCommand);

        getCommand("lobby").setExecutor(new LobbyCommand(this));
        getCommand("l").setExecutor(new LobbyCommand(this));

        getCommand("ban").setExecutor(new BanCommand(adminManager));
        getCommand("tempban").setExecutor(new TempBanCommand(adminManager));
        getCommand("mute").setExecutor(new MuteCommand(adminManager));
        getCommand("kick").setExecutor(new KickCommand(adminManager));
        getCommand("warn").setExecutor(new WarnCommand(adminManager));
        getCommand("sc").setExecutor(new StaffChatCommand(chatManager));

        NpcCommand npcCommand = new NpcCommand(npcManager);
        getCommand("npc").setExecutor(npcCommand);
        getCommand("npc").setTabCompleter(npcCommand);
    }

    public static AspireCore getInstance() {
        return instance;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public FriendManager getFriendManager() {
        return friendManager;
    }

    public AdminManager getAdminManager() {
        return adminManager;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public NpcManager getNpcManager() {
        return npcManager;
    }
}

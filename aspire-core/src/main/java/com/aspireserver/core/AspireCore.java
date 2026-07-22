package com.aspireserver.core;

import com.aspireserver.core.admin.AdminManager;
import com.aspireserver.core.admin.BanCommand;
import com.aspireserver.core.admin.KickCommand;
import com.aspireserver.core.admin.MuteCommand;
import com.aspireserver.core.admin.TempBanCommand;
import com.aspireserver.core.admin.WarnCommand;
import com.aspireserver.core.chat.ChatManager;
import com.aspireserver.core.chat.StaffChatCommand;
import com.aspireserver.core.commands.FlySpeedCommand;
import com.aspireserver.core.commands.LobbyCommand;
import com.aspireserver.core.commands.SetLobbyCommand;
import com.aspireserver.core.listeners.LobbyProtectionListener;
import com.aspireserver.core.listeners.WorldProtectionListener;
import com.aspireserver.core.loadout.LoadoutCommand;
import com.aspireserver.core.loadout.LoadoutJoinListener;
import com.aspireserver.core.loadout.LoadoutManager;
import com.aspireserver.core.friends.FriendCommand;
import com.aspireserver.core.friends.FriendManager;
import com.aspireserver.core.npc.NpcCommand;
import com.aspireserver.core.npc.NpcListener;
import com.aspireserver.core.npc.NpcManager;
import com.aspireserver.core.party.PartyCommand;
import com.aspireserver.core.party.PartyManager;
import com.aspireserver.core.rank.NickCommand;
import com.aspireserver.core.rank.RankCommand;
import com.aspireserver.core.rank.RankJoinListener;
import com.aspireserver.core.rank.RankManager;
import com.aspireserver.core.title.TitleCommand;
import com.aspireserver.core.title.TitleManager;
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
    private LoadoutManager loadoutManager;
    private RankManager rankManager;
    private TitleManager titleManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        partyManager = new PartyManager(this);
        friendManager = new FriendManager(this);
        adminManager = new AdminManager(this);
        chatManager = new ChatManager(this);
        npcManager = new NpcManager(this);
        loadoutManager = new LoadoutManager(this);
        rankManager = new RankManager(this);
        titleManager = new TitleManager(this);

        registerCommands();
        getServer().getPluginManager().registerEvents(chatManager, this);
        getServer().getPluginManager().registerEvents(new NpcListener(this, npcManager), this);
        getServer().getPluginManager().registerEvents(new LoadoutJoinListener(loadoutManager, this), this);
        getServer().getPluginManager().registerEvents(new WorldProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new LobbyProtectionListener(this), this);
        RankCommand rankCmd = new RankCommand(rankManager);
        getServer().getPluginManager().registerEvents(rankCmd, this);
        getServer().getPluginManager().registerEvents(new RankJoinListener(rankManager), this);
        TitleCommand titleCmd = new TitleCommand(titleManager);
        getServer().getPluginManager().registerEvents(titleCmd, this);

        Bukkit.getScheduler().runTaskLater(this, () -> npcManager.spawnAllNpcs(), 60L);

        getLogger().info(MessageUtil.PREFIX_RAW + "AspireCore enabled!");
    }

    @Override
    public void onDisable() {
        friendManager.saveData();
        adminManager.saveData();
        loadoutManager.save();
        rankManager.saveData();
        titleManager.saveData();
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
        getCommand("setlobby").setExecutor(new SetLobbyCommand(this));

        getCommand("ban").setExecutor(new BanCommand(adminManager));
        getCommand("tempban").setExecutor(new TempBanCommand(adminManager));
        getCommand("mute").setExecutor(new MuteCommand(adminManager));
        getCommand("kick").setExecutor(new KickCommand(adminManager));
        getCommand("warn").setExecutor(new WarnCommand(adminManager));
        getCommand("sc").setExecutor(new StaffChatCommand(chatManager));

        NpcCommand npcCommand = new NpcCommand(npcManager);
        getCommand("npc").setExecutor(npcCommand);
        getCommand("npc").setTabCompleter(npcCommand);

        FlySpeedCommand flySpeedCmd = new FlySpeedCommand();
        getCommand("flyspeed").setExecutor(flySpeedCmd);
        getCommand("flyspeed").setTabCompleter(flySpeedCmd);

        LoadoutCommand loadoutCmd = new LoadoutCommand(loadoutManager);
        getCommand("loadout").setExecutor(loadoutCmd);
        getCommand("loadout").setTabCompleter(loadoutCmd);

        RankCommand rankCommand = new RankCommand(rankManager);
        getCommand("rankgive").setExecutor(rankCommand);
        getCommand("rankgive").setTabCompleter(rankCommand);

        NickCommand nickCommand = new NickCommand(rankManager);
        getCommand("nick").setExecutor(nickCommand);
        getCommand("nick").setTabCompleter(nickCommand);

        TitleCommand titleCommand = new TitleCommand(titleManager);
        getCommand("titlegive").setExecutor(titleCommand);
        getCommand("titlegive").setTabCompleter(titleCommand);
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

    public RankManager getRankManager() {
        return rankManager;
    }

    public TitleManager getTitleManager() {
        return titleManager;
    }
}

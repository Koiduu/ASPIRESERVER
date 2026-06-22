package com.aspireserver.core.chat;

import com.aspireserver.core.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class StaffChatCommand implements CommandExecutor {

    private final ChatManager chatManager;

    public StaffChatCommand(ChatManager chatManager) {
        this.chatManager = chatManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("aspire.admin.staffchat")) {
            MessageUtil.sendError(player, "No permission!");
            return true;
        }

        chatManager.toggleStaffChat(player.getUniqueId());
        if (chatManager.isInStaffChat(player.getUniqueId())) {
            MessageUtil.sendSuccess(player, "Staff chat enabled. All messages go to staff channel.");
        } else {
            MessageUtil.send(player, "Staff chat disabled. Back to normal chat.");
        }
        return true;
    }
}

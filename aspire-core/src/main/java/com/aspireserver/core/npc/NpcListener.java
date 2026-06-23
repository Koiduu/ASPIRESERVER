package com.aspireserver.core.npc;

import com.aspireserver.core.AspireCore;
import com.aspireserver.core.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NpcListener implements Listener {

    private final AspireCore plugin;
    private final NpcManager npcManager;
    private final Map<UUID, Long> clickCooldown;

    public NpcListener(AspireCore plugin, NpcManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.clickCooldown = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Villager)) return;

        NpcData npc = npcManager.getNpcByEntity(entity.getUniqueId());
        if (npc == null) return;

        event.setCancelled(true);
        handleNpcClick(event.getPlayer(), npc);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof Villager)) return;

        NpcData npc = npcManager.getNpcByEntity(entity.getUniqueId());
        if (npc == null) return;

        event.setCancelled(true);
        handleNpcClick((Player) event.getDamager(), npc);
    }

    private void handleNpcClick(Player player, NpcData npc) {
        long now = System.currentTimeMillis();
        Long last = clickCooldown.get(player.getUniqueId());
        if (last != null && now - last < 1000) return;
        clickCooldown.put(player.getUniqueId(), now);

        NpcAction action = npc.getAction();
        switch (action) {
            case BUILD_BATTLE_SOLO, BUILD_BATTLE_TEAMS, BUILD_BATTLE_PRO_SOLO, BUILD_BATTLE_PRO_TEAMS -> {
                if (action.getCommand() != null) {
                    player.performCommand(action.getCommand());
                }
            }
            case WARP_SMP -> {
                String worldName = plugin.getConfig().getString("warps.smp.world", "world");
                double x = plugin.getConfig().getDouble("warps.smp.x", 0);
                double y = plugin.getConfig().getDouble("warps.smp.y", 100);
                double z = plugin.getConfig().getDouble("warps.smp.z", 0);
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    player.teleport(new Location(world, x, y, z));
                    MessageUtil.sendSuccess(player, "Warped to SMP!");
                } else {
                    MessageUtil.sendError(player, "SMP world not found!");
                }
            }
            case WARP_CREATIVE -> {
                String worldName = plugin.getConfig().getString("warps.creative.world", "creative_small");
                double x = plugin.getConfig().getDouble("warps.creative.x", 0);
                double y = plugin.getConfig().getDouble("warps.creative.y", 65);
                double z = plugin.getConfig().getDouble("warps.creative.z", 0);
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    player.teleport(new Location(world, x, y, z));
                    MessageUtil.sendSuccess(player, "Warped to Creative!");
                } else {
                    MessageUtil.sendError(player, "Creative world not found!");
                }
            }
            case WARP_LOBBY -> {
                player.performCommand("lobby");
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component title = event.getView().title();
        String titleText = PlainTextComponentSerializer.plainText().serialize(title);
        if (!titleText.startsWith("Edit NPC: ")) return;

        event.setCancelled(true);

        String npcId = titleText.substring("Edit NPC: ".length());
        NpcData npc = npcManager.getNpc(npcId);
        if (npc == null) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        int slot = event.getSlot();
        NpcAction[] actions = NpcAction.values();
        if (slot >= 0 && slot < actions.length) {
            npc.setAction(actions[slot]);
            npcManager.spawnNpc(npc);
            npcManager.saveNpcs();
            player.closeInventory();
            MessageUtil.sendSuccess(player, "NPC action set to: " + actions[slot].getDisplayName());
        }
    }
}

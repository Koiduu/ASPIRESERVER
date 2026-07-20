package com.aspireserver.duelbot.blocks;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Places a block on the bot's behalf by firing a real {@link BlockPlaceEvent}, so
 * duel-arena region protection (WorldGuard etc.) still vetoes illegal placements.
 */
public final class BlockPlacer {

    private BlockPlacer() {
    }

    /**
     * Attempts to place {@code material} at {@code target}, drawing from the bot's simulated hotbar.
     *
     * @return true if the block was placed (event not cancelled).
     */
    public static boolean place(Player bot, Block target, Material material) {
        if (!target.getType().isAir() && !target.isLiquid()) return false;

        Block against = firstSolidNeighbour(target);
        if (against == null) against = target.getRelative(0, -1, 0);

        BlockState replaced = target.getState();
        target.setType(material, false);

        ItemStack hand = new ItemStack(material);
        BlockPlaceEvent event = new BlockPlaceEvent(
                target, replaced, against, hand, bot, true, EquipmentSlot.HAND);
        bot.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled() || !event.canBuild()) {
            replaced.update(true, false);
            return false;
        }
        return true;
    }

    private static Block firstSolidNeighbour(Block b) {
        int[][] faces = {{0, -1, 0}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] f : faces) {
            Block rel = b.getRelative(f[0], f[1], f[2]);
            if (rel.getType().isSolid()) return rel;
        }
        return null;
    }
}

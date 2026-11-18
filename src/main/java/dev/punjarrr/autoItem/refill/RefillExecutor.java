package dev.punjarrr.autoItem.refill;

import dev.punjarrr.autoItem.bukkit.BukkitItemView;
import dev.punjarrr.autoItem.refill.Decision.Refill;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

/**
 * The only code that writes player inventories (SPEC §3 "The refill"). One synchronous call on the
 * player's owning thread; it only moves items, never creates or deletes them.
 */
public final class RefillExecutor {

    /**
     * Performs a planned refill.
     *
     * @param plannedSource the source slot's item as the planner saw it
     * @param plannedTarget the target slot's item as the planner saw it (null = empty)
     * @return a clone of the stack moved into the target, or null if the inventory no longer matches the
     *         plan (then nothing was changed)
     */
    public @Nullable ItemStack apply(Player player, Refill refill, BukkitItemView plannedSource,
                                     @Nullable BukkitItemView plannedTarget) {
        PlayerInventory inventory = player.getInventory();

        // Verify both slots before touching either, so a mismatch never leaves a half-done move.
        ItemStack sourceNow = inventory.getItem(refill.source());
        if (sourceNow == null || sourceNow.isEmpty() || sourceNow.getAmount() != plannedSource.amount()
                || !sourceNow.isSimilar(plannedSource.stack())) {
            return null;
        }
        ItemStack targetNow = inventory.getItem(refill.target());
        boolean targetEmpty = targetNow == null || targetNow.isEmpty();
        if (plannedTarget == null ? !targetEmpty
                : targetEmpty || targetNow.getAmount() != plannedTarget.amount()
                || !targetNow.isSimilar(plannedTarget.stack())) {
            return null;
        }

        // Clone everything before its slot is written: getItem() may return a live mirror.
        ItemStack moved = sourceNow.clone();
        ItemStack remainder = targetEmpty ? null : targetNow.clone();
        // A swap: the remainder (bowl, bottle, bucket) goes into the slot the stack came from. That slot was
        // just verified and is emptied by this move, so it always fits: nothing is ever dropped, and the
        // remainder never lands in a hand or in another pending target.
        inventory.setItem(refill.source(), remainder);
        inventory.setItem(refill.target(), moved.clone());
        return moved;
    }
}

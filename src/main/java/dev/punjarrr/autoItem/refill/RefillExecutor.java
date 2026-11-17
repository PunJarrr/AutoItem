package dev.punjarrr.autoItem.refill;

import dev.punjarrr.autoItem.bukkit.BukkitItemView;
import dev.punjarrr.autoItem.refill.Decision.Refill;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

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
        inventory.setItem(refill.source(), null);
        inventory.setItem(refill.target(), moved.clone());
        if (remainder != null) {
            giveBack(player, inventory, remainder);
        }
        return moved;
    }

    /** Into the inventory if it fits; whatever doesn't fit drops at the player's feet. */
    private static void giveBack(Player player, PlayerInventory inventory, ItemStack remainder) {
        Map<Integer, ItemStack> leftover = inventory.addItem(remainder);
        if (leftover.isEmpty()) {
            return;
        }
        Location feet = player.getLocation();
        World world = feet.getWorld();
        for (ItemStack stack : leftover.values()) {
            world.dropItem(feet, stack);
        }
    }
}

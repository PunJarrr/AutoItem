package dev.punjarrr.autoItem.listener;

import dev.punjarrr.autoItem.refill.CheckRecorder;
import dev.punjarrr.autoItem.refill.RefillPlanner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Drops pending checks when something other than a trigger empties or swaps a hand in the same tick
 * (SPEC §3 "Discards"): a drop (Q), a hand swap (F), an inventory click or drag (also from hacked clients
 * with no inventory open) and a totem pop. Otherwise a check from a use that consumed nothing would refill
 * the slot: an auto-totem, or a refill after Q. Changes only the recorder's own state, never an inventory.
 */
public final class DiscardListener implements Listener {

    private final CheckRecorder recorder;

    public DiscardListener(CheckRecorder recorder) {
        this.recorder = recorder;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        recorder.discard(player, player.getInventory().getHeldItemSlot());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        recorder.discard(player, player.getInventory().getHeldItemSlot());
        recorder.discard(player, RefillPlanner.OFFHAND_SLOT);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            recorder.discardAll(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            recorder.discardAll(player);
        }
    }

    /** Cancelled when there's no totem, so ignoreCancelled leaves only real pops. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotem(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getHand() != null) {
            recorder.discard(player, CheckRecorder.slotFor(player, event.getHand()));
        } else {
            recorder.discard(player, player.getInventory().getHeldItemSlot());
            recorder.discard(player, RefillPlanner.OFFHAND_SLOT);
        }
    }
}

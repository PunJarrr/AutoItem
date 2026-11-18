package dev.punjarrr.autoItem.listener;

import dev.punjarrr.autoItem.refill.CheckRecorder;
import dev.punjarrr.autoItem.refill.Trigger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Trigger {@code buckets}. Emptying leaves a bucket (remainders table). Filling the last empty bucket, or
 * catching a mob in the last water bucket, leaves the filled bucket: it is allowed as the remainder, moved
 * out, and the slot gets another empty (or water) bucket. Observer only.
 */
public final class BucketListener implements Listener {

    private final CheckRecorder recorder;

    public BucketListener(CheckRecorder recorder) {
        this.recorder = recorder;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent event) {
        record(event.getPlayer(), event.getHand(), event.getBucket(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent event) {
        record(event.getPlayer(), event.getHand(), event.getBucket(), event.getItemStack());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCatch(PlayerBucketEntityEvent event) {
        int slot = CheckRecorder.slotFor(event.getPlayer(), event.getHand());
        if (slot >= 0) {
            recorder.recordSlot(event.getPlayer(), slot, CheckRecorder.similarTo(event.getOriginalBucket()),
                    Trigger.BUCKETS, filled(event.getEntityBucket()));
        }
    }

    private void record(Player player, EquipmentSlot hand, Material bucket,
                        @Nullable ItemStack result) {
        int slot = CheckRecorder.slotFor(player, hand);
        if (slot >= 0) {
            recorder.recordSlot(player, slot, item -> item.getType() == bucket, Trigger.BUCKETS, filled(result));
        }
    }

    private static Set<String> filled(@Nullable ItemStack result) {
        return result == null || result.isEmpty() ? Set.of() : Set.of(result.getType().getKey().toString());
    }
}

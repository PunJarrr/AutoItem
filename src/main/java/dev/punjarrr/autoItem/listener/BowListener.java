package dev.punjarrr.autoItem.listener;

import com.destroystokyo.paper.event.player.PlayerReadyArrowEvent;
import dev.punjarrr.autoItem.refill.CheckRecorder;
import dev.punjarrr.autoItem.refill.Trigger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Trigger {@code bows}: the last arrow (or rocket) in the hand opposite the bow or crossbow. Ammo taken from
 * storage slots isn't in a hand, so there's nothing to refill. Tipped arrows match exactly (isSimilar).
 * Observer only.
 *
 * <p>A bow takes its arrow out of the inventory before {@code EntityShootBowEvent} fires, so that event
 * can't see the last arrow. {@code PlayerReadyArrowEvent} fires earlier, while the weapon picks its ammo:
 * when a bow starts drawing and again on release, and when a crossbow loads, always before anything is
 * removed. It names the exact ammo, so only a hand holding that ammo is recorded.
 */
public final class BowListener implements Listener {

    private final CheckRecorder recorder;

    public BowListener(CheckRecorder recorder) {
        this.recorder = recorder;
    }

    /** The event doesn't say which hand holds the arrow; the bow's hand never matches an arrow. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onReadyArrow(PlayerReadyArrowEvent event) {
        recorder.recordBothHands(event.getPlayer(), event.getArrow(), Trigger.BOWS);
    }

}

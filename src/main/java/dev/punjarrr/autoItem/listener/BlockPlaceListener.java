package dev.punjarrr.autoItem.listener;

import dev.punjarrr.autoItem.refill.CheckRecorder;
import dev.punjarrr.autoItem.refill.Trigger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Trigger {@code block-place}. Also receives {@code BlockMultiPlaceEvent} (a subclass). Matches the item in
 * the hand, not the placed block, so redstone dust refills dust and seeds refill seeds. Observer only.
 */
public final class BlockPlaceListener implements Listener {

    private final CheckRecorder recorder;

    public BlockPlaceListener(CheckRecorder recorder) {
        this.recorder = recorder;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        recorder.record(event.getPlayer(), event.getHand(), event.getItemInHand(), Trigger.BLOCK_PLACE);
    }
}

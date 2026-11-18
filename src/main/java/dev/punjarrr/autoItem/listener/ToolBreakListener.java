package dev.punjarrr.autoItem.listener;

import dev.punjarrr.autoItem.refill.CheckRecorder;
import dev.punjarrr.autoItem.refill.Trigger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemBreakEvent;

/**
 * Trigger {@code tool-break} (off by default). The event fires while the broken tool is still in its slot
 * and doesn't say which hand, so each hand holding exactly that item is recorded as a TOOL_BREAK check; the
 * planner then picks a replacement by material, name, plugin data and enchantments (SPEC §4.2). Broken
 * armor never matches a hand slot, so it is never re-equipped (not in v1). Observer only.
 */
public final class ToolBreakListener implements Listener {

    private final CheckRecorder recorder;

    public ToolBreakListener(CheckRecorder recorder) {
        this.recorder = recorder;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(PlayerItemBreakEvent event) {
        recorder.recordBothHands(event.getPlayer(), event.getBrokenItem(), Trigger.TOOL_BREAK);
    }
}

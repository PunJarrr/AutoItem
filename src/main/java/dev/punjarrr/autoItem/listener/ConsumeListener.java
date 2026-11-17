package dev.punjarrr.autoItem.listener;

import dev.punjarrr.autoItem.refill.CheckRecorder;
import dev.punjarrr.autoItem.refill.Trigger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

/** Trigger {@code consume}: eating and drinking. Remainders (bowl, bottle, bucket) come from the table. */
public final class ConsumeListener implements Listener {

    private final CheckRecorder recorder;

    public ConsumeListener(CheckRecorder recorder) {
        this.recorder = recorder;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        recorder.record(event.getPlayer(), event.getHand(), event.getItem(), Trigger.CONSUME);
    }
}

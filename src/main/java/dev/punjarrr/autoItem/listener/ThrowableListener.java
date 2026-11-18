package dev.punjarrr.autoItem.listener;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import dev.punjarrr.autoItem.refill.CheckRecorder;
import dev.punjarrr.autoItem.refill.Trigger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Trigger {@code throwables}: snowballs, eggs, ender pearls, splash/lingering potions, experience bottles,
 * wind charges. The event doesn't say which hand threw, so both hands are recorded; the task only refills
 * the one that actually ran out. Tridents are damageable, so the planner never refills them. Observer only.
 */
public final class ThrowableListener implements Listener {

    private final CheckRecorder recorder;

    public ThrowableListener(CheckRecorder recorder) {
        this.recorder = recorder;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(PlayerLaunchProjectileEvent event) {
        if (event.shouldConsume()) {
            recorder.recordBothHands(event.getPlayer(), event.getItemStack(), Trigger.THROWABLES);
        }
    }
}

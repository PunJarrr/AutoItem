package dev.punjarrr.autoItem.refill;

import dev.punjarrr.autoItem.bukkit.BukkitItemView;
import dev.punjarrr.autoItem.bukkit.PlayerStates;
import dev.punjarrr.autoItem.config.ConfigService;
import dev.punjarrr.autoItem.feedback.FeedbackService;
import dev.punjarrr.autoItem.refill.Decision.Refill;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Answers one player's batch of checks, one tick after they were recorded. Each check re-reads the live
 * inventory (after the previous check's refill), asks the planner, and hands a refill to the executor.
 */
public final class RefillTask {

    private final ConfigService config;
    private final PlayerStates states;
    private final RefillPlanner planner;
    private final RefillExecutor executor;
    private final FeedbackService feedback;
    private final Logger logger;

    public RefillTask(ConfigService config, PlayerStates states, RefillPlanner planner, RefillExecutor executor,
                      FeedbackService feedback, Logger logger) {
        this.config = config;
        this.states = states;
        this.planner = planner;
        this.executor = executor;
        this.feedback = feedback;
        this.logger = logger;
    }

    public void run(Player player, Batch batch) {
        // Every slot this batch may refill is off limits as a source, for every check in it: one check never
        // takes another's slot (before or after it was refilled), so a remainder never lands in another target.
        List<Check> checks = batch.checks();
        Set<Integer> targets = new HashSet<>();
        for (Check check : checks) {
            targets.add(check.slot());
        }
        for (Check check : checks) {
            runOne(player, check, targets);
        }
    }

    private void runOne(Player player, Check check, Set<Integer> targets) {
        BukkitItemView[] slots = read(player.getInventory());
        Decision decision = planner.plan(check, slots, config.settings(), states.of(player), targets);
        if (decision instanceof Refill refill) {
            ItemStack moved = executor.apply(player, refill, slots[refill.source()], slots[refill.target()]);
            if (moved != null) {
                try {
                    feedback.refilled(player, moved);
                } catch (RuntimeException e) {
                    // The move is done; a failing sound or action bar must not stop the next check.
                    logger.log(Level.WARNING, "Refill feedback failed for " + player.getName(), e);
                }
            }
        }
    }

    /** Clones of all 41 slots as they are right now. */
    static BukkitItemView[] read(PlayerInventory inventory) {
        BukkitItemView[] slots = new BukkitItemView[RefillPlanner.INVENTORY_SIZE];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = BukkitItemView.of(inventory.getItem(i));
        }
        return slots;
    }
}

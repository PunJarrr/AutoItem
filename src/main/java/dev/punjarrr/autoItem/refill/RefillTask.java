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
        Set<Integer> refilled = new HashSet<>();
        for (Check check : batch.checks()) {
            runOne(player, check, refilled);
        }
    }

    private void runOne(Player player, Check check, Set<Integer> refilled) {
        BukkitItemView[] slots = read(player.getInventory());
        Decision decision = planner.plan(check, slots, config.settings(), states.of(player), refilled);
        if (decision instanceof Refill refill) {
            ItemStack moved = executor.apply(player, refill, slots[refill.source()], slots[refill.target()]);
            if (moved != null) {
                refilled.add(refill.target());
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

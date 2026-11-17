package dev.punjarrr.autoItem.refill;

import dev.punjarrr.autoItem.bukkit.BukkitItemView;
import dev.punjarrr.autoItem.bukkit.PlayerStates;
import dev.punjarrr.autoItem.config.ConfigService;
import dev.punjarrr.autoItem.refill.Decision.Refill;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

/**
 * Answers one player's batch of checks, one tick after they were recorded. Each check re-reads the live
 * inventory (after the previous check's refill), asks the planner, and hands a refill to the executor.
 */
public final class RefillTask {

    private final ConfigService config;
    private final PlayerStates states;
    private final RefillPlanner planner;
    private final RefillExecutor executor;

    public RefillTask(ConfigService config, PlayerStates states, RefillPlanner planner, RefillExecutor executor) {
        this.config = config;
        this.states = states;
        this.planner = planner;
        this.executor = executor;
    }

    public void run(Player player, Batch batch) {
        for (Check check : batch.checks()) {
            runOne(player, check);
        }
    }

    private void runOne(Player player, Check check) {
        BukkitItemView[] slots = read(player.getInventory());
        Decision decision = planner.plan(check, slots, config.settings(), states.of(player));
        if (decision instanceof Refill refill) {
            executor.apply(player, refill, slots[refill.source()], slots[refill.target()]);
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

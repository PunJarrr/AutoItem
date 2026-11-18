package dev.punjarrr.autoItem.refill;

import dev.punjarrr.autoItem.bukkit.BukkitItemView;
import dev.punjarrr.autoItem.bukkit.PlayerStates;
import dev.punjarrr.autoItem.config.ConfigService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Where listeners report "this slot may have just run out". Records a {@link Check}, at most one per
 * player per slot per tick, and schedules one task per player on the player's entity scheduler (the main
 * thread on Paper, the player's region thread on Folia). Never touches inventories.
 */
public final class CheckRecorder {

    private final Plugin plugin;
    private final ConfigService config;
    private final PlayerStates states;
    private final RefillTask task;
    /** One batch per player, only touched from that player's owning thread. Concurrent for Folia hand-offs. */
    private final Map<UUID, Batch> batches = new ConcurrentHashMap<>();

    public CheckRecorder(Plugin plugin, ConfigService config, PlayerStates states, RefillTask task) {
        this.plugin = plugin;
        this.config = config;
        this.states = states;
        this.task = task;
    }

    /** The raw inventory slot for a hand at this moment, or -1 if {@code hand} isn't a hand. */
    public static int slotFor(Player player, @Nullable EquipmentSlot hand) {
        if (hand == EquipmentSlot.HAND) {
            return player.getInventory().getHeldItemSlot();
        }
        if (hand == EquipmentSlot.OFF_HAND) {
            return RefillPlanner.OFFHAND_SLOT;
        }
        return -1;
    }

    public void record(Player player, @Nullable EquipmentSlot hand, @Nullable ItemStack eventItem, Trigger trigger) {
        int slot = slotFor(player, hand);
        if (slot >= 0) {
            recordSlot(player, slot, similarTo(eventItem), trigger, Set.of());
        }
    }

    /** For events that don't say which hand was used: records each hand holding the last such item. */
    public void recordBothHands(Player player, @Nullable ItemStack eventItem, Trigger trigger) {
        Predicate<ItemStack> matches = similarTo(eventItem);
        recordSlot(player, player.getInventory().getHeldItemSlot(), matches, trigger, Set.of());
        recordSlot(player, RefillPlanner.OFFHAND_SLOT, matches, trigger, Set.of());
    }

    /** Matches items similar to {@code eventItem}; nothing if it is null or empty. */
    public static Predicate<ItemStack> similarTo(@Nullable ItemStack eventItem) {
        return eventItem == null || eventItem.isEmpty() ? item -> false : item -> item.isSimilar(eventItem);
    }

    /**
     * Forgets this tick's pending check for a slot because something other than a trigger changed it (a drop,
     * a hand swap, a totem pop). Without this, a check recorded by a use that didn't consume anything (the
     * right-click catch-all, a bow starting to draw) would refill a slot the player emptied some other way.
     * Touches only the recorder's own state, never the inventory.
     */
    public void discard(Player player, int slot) {
        Batch batch = batches.get(player.getUniqueId());
        if (batch != null) {
            batch.remove(slot);
        }
    }

    /** Forgets all of this tick's pending checks for a player (an inventory click or drag). */
    public void discardAll(Player player) {
        Batch batch = batches.get(player.getUniqueId());
        if (batch != null) {
            batch.clear();
        }
    }

    /**
     * Records a check if the slot, read now (before vanilla decrements it), holds exactly one item that
     * {@code matches}. {@code extraRemainders} (material keys) are allowed in the slot on top of the
     * remainders table, e.g. the filled bucket a bucket event reports.
     *
     * <p>Records even when {@code trigger} is turned off: the switch is applied at task time to the merged
     * check, so a disabled specific trigger also suppresses the right-click catch-all for the same use.
     */
    public void recordSlot(Player player, int slot, Predicate<ItemStack> matches, Trigger trigger,
                           Set<String> extraRemainders) {
        RefillSettings settings = config.settings();
        PlayerState state = states.of(player);
        if (!Gates.allows(state, settings)) {
            return;
        }
        ItemStack live = player.getInventory().getItem(slot);
        if (live == null || live.isEmpty() || live.getAmount() != 1) {
            return;
        }
        if (!matches.test(live)) {
            return;
        }
        BukkitItemView snapshot = BukkitItemView.of(live);
        if (snapshot == null) {
            return;
        }
        Set<String> remainders = new HashSet<>(settings.remainders().allowedFor(snapshot.material()));
        remainders.addAll(extraRemainders);
        Check check = new Check(slot, snapshot, remainders, EnumSet.of(trigger), state.worldId(), state.gameMode());
        enqueue(player, check);
    }

    private void enqueue(Player player, Check check) {
        UUID id = player.getUniqueId();
        Batch batch = batches.get(id);
        if (batch == null) {
            Batch fresh = new Batch();
            batches.put(id, fresh);
            boolean scheduled = player.getScheduler().execute(plugin,
                    () -> drain(player, fresh),
                    () -> batches.remove(id, fresh),
                    1L);
            if (!scheduled) {
                batches.remove(id, fresh);
                return;
            }
            batch = fresh;
        }
        batch.add(check);
    }

    private void drain(Player player, Batch batch) {
        batches.remove(player.getUniqueId(), batch);
        task.run(player, batch);
    }

    /** Players with checks waiting for their task. For tests. */
    public int pendingPlayers() {
        return batches.size();
    }
}

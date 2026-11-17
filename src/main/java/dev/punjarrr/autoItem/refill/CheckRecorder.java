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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
            recordSlot(player, slot, eventItem, trigger);
        }
    }

    /**
     * Records a check if the slot, read now (before vanilla decrements it), holds exactly one item similar
     * to {@code eventItem} (any item if {@code eventItem} is null).
     */
    public void recordSlot(Player player, int slot, @Nullable ItemStack eventItem, Trigger trigger) {
        RefillSettings settings = config.settings();
        if (!settings.triggers().contains(trigger)) {
            return;
        }
        PlayerState state = states.of(player);
        if (!Gates.allows(state, settings)) {
            return;
        }
        ItemStack live = player.getInventory().getItem(slot);
        if (live == null || live.isEmpty() || live.getAmount() != 1) {
            return;
        }
        if (eventItem != null && !live.isSimilar(eventItem)) {
            return;
        }
        BukkitItemView snapshot = BukkitItemView.of(live);
        if (snapshot == null) {
            return;
        }
        Check check = new Check(slot, snapshot, settings.remainders().allowedFor(snapshot.material()),
                EnumSet.of(trigger), state.worldId(), state.gameMode());
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

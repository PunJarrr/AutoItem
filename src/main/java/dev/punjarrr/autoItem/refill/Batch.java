package dev.punjarrr.autoItem.refill;

import java.util.List;
import java.util.TreeMap;

/**
 * The checks recorded for one player during one tick, at most one per slot. Iteration order is by slot,
 * so the main hand (0–8) comes before the offhand (40). Only touched by the player's owning thread.
 */
public final class Batch {

    private final TreeMap<Integer, Check> bySlot = new TreeMap<>();

    /**
     * Same item as the check already pending for that slot: merge (SPEC §3 step 4). A different item: the slot
     * changed in between (an armor-stand swap, another plugin), so the later check replaces the earlier one;
     * the item used last is the one to refill.
     */
    public void add(Check check) {
        bySlot.merge(check.slot(), check,
                (earlier, later) -> earlier.snapshot().isSimilar(later.snapshot()) ? earlier.merge(later) : later);
    }

    /** Forgets the check for one slot, if any. */
    public void remove(int slot) {
        bySlot.remove(slot);
    }

    /** Forgets every check. */
    public void clear() {
        bySlot.clear();
    }

    public List<Check> checks() {
        return List.copyOf(bySlot.values());
    }

    public boolean isEmpty() {
        return bySlot.isEmpty();
    }
}

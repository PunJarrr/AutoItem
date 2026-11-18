package dev.punjarrr.autoItem.refill;

import java.util.List;
import java.util.TreeMap;

/**
 * The checks recorded for one player during one tick, at most one per slot. Iteration order is by slot,
 * so the main hand (0–8) comes before the offhand (40). Only touched by the player's owning thread.
 */
public final class Batch {

    private final TreeMap<Integer, Check> bySlot = new TreeMap<>();

    public void add(Check check) {
        bySlot.merge(check.slot(), check, Check::merge);
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

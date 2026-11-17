package dev.punjarrr.autoItem.refill;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One pending "was the last item in this slot used up?" question, recorded by a listener and answered
 * by the planner one tick later (SPEC §3).
 *
 * @param slot               raw PlayerInventory index: 0–8 for the main hand, 40 for the offhand
 * @param snapshot           the slot's item at event time, before vanilla decremented it
 * @param allowedRemainders  material keys that may be left in the slot (exactly one item of one of them)
 * @param triggers           the trigger groups that recorded this check
 * @param world              the player's world at event time
 * @param gameMode           the player's game mode at event time
 */
public record Check(int slot, ItemView snapshot, Set<String> allowedRemainders, Set<Trigger> triggers,
                    UUID world, String gameMode) {

    public Check {
        allowedRemainders = Set.copyOf(allowedRemainders);
        triggers = Set.copyOf(triggers);
        if (triggers.isEmpty()) {
            throw new IllegalArgumentException("a check needs at least one trigger");
        }
    }

    /** A tool-break check uses the tool-break matching rules (SPEC §4.2). */
    public boolean isToolBreak() {
        return triggers.contains(Trigger.TOOL_BREAK);
    }

    /**
     * Dedup (SPEC §3 step 4): keep this check's snapshot and context, and union the allowed remainders and
     * triggers. Accepting more remainders is safe because a remainder is always moved, never deleted.
     */
    public Check merge(Check later) {
        if (later.slot != slot) {
            throw new IllegalArgumentException("can only merge checks for the same slot");
        }
        Set<String> remainders = new HashSet<>(allowedRemainders);
        remainders.addAll(later.allowedRemainders);
        Set<Trigger> allTriggers = EnumSet.copyOf(triggers);
        allTriggers.addAll(later.triggers);
        return new Check(slot, snapshot, remainders, allTriggers, world, gameMode);
    }
}

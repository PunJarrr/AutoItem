package dev.punjarrr.autoItem.refill;

/** The planner's answer for one check. */
public sealed interface Decision {

    /** Move the stack in {@code source} into {@code target}, returning whatever {@code target} holds. */
    record Refill(int source, int target) implements Decision {
    }

    record Skip(Reason reason) implements Decision {
    }

    /** Why nothing happens, in the order the planner checks (SPEC §3 decision table). */
    enum Reason {
        /** Offline, dead, creative/spectator, disabled world, toggle off, no permission, or plugin off. */
        GATE,
        /** World or game mode changed between the event and the task. */
        CONTEXT_CHANGED,
        /** The trigger that recorded the check is turned off. */
        DISABLED,
        /** The used item is in {@code ignored-items}. */
        IGNORED,
        /** The slot still holds the same item: the last one wasn't used up. */
        NOT_USED_UP,
        /** The slot holds something that is neither empty nor an allowed remainder. */
        SLOT_CHANGED,
        /** A damageable item only refills through a tool break. */
        DAMAGEABLE,
        /** Nothing in the inventory matches. */
        NO_SOURCE
    }
}

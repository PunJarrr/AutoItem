package dev.punjarrr.autoItem.refill;

import dev.punjarrr.autoItem.refill.Decision.Reason;
import dev.punjarrr.autoItem.refill.Decision.Refill;
import dev.punjarrr.autoItem.refill.Decision.Skip;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * The refill decision (SPEC §3 decision table, §4.2 matching). Pure: it reads only its arguments, so the
 * same inputs always give the same answer, and it never changes anything.
 */
public final class RefillPlanner {

    /** Raw PlayerInventory size: storage 0–35, armor 36–39, offhand 40. */
    public static final int INVENTORY_SIZE = 41;
    public static final int OFFHAND_SLOT = 40;

    private static final int HOTBAR_END = 9;
    private static final int STORAGE_END = 36;

    /**
     * @param check    the recorded check
     * @param slots    the inventory read at task time, {@value #INVENTORY_SIZE} entries, null = empty
     * @param settings the config at task time
     * @param player   the player at task time
     */
    public Decision plan(Check check, @Nullable ItemView[] slots, RefillSettings settings, PlayerState player) {
        return plan(check, slots, settings, player, Set.of());
    }

    /**
     * @param reserved slots already refilled earlier in this batch: never sources, so a second check (the
     *                 offhand) can't undo the first one's refill
     */
    public Decision plan(Check check, @Nullable ItemView[] slots, RefillSettings settings, PlayerState player,
                         Set<Integer> reserved) {
        if (slots.length != INVENTORY_SIZE) {
            throw new IllegalArgumentException("expected " + INVENTORY_SIZE + " slots, got " + slots.length);
        }
        if (!Gates.allows(player, settings)) {
            return new Skip(Reason.GATE);
        }
        if (!check.world().equals(player.worldId()) || !check.gameMode().equals(player.gameMode())) {
            return new Skip(Reason.CONTEXT_CHANGED);
        }
        ItemView snapshot = check.snapshot();
        // Tool-break matching only for an item that can break: a tool-break record merged into a check for
        // something else (another item broke in the same slot in the same tick) doesn't loosen its matching.
        boolean toolBreak = check.isToolBreak() && snapshot.maxDurability() > 0;
        boolean triggerEnabled = toolBreak
                ? settings.triggers().contains(Trigger.TOOL_BREAK)
                : check.triggers().stream().filter(t -> t != Trigger.TOOL_BREAK).anyMatch(settings.triggers()::contains);
        if (!triggerEnabled) {
            return new Skip(Reason.DISABLED);
        }
        if (settings.ignoredMaterials().contains(snapshot.material())) {
            return new Skip(Reason.IGNORED);
        }
        ItemView target = slots[check.slot()];
        if (target != null && target.isSimilar(snapshot)) {
            return new Skip(Reason.NOT_USED_UP);
        }
        if (target != null && !isAllowedRemainder(check, target)) {
            return new Skip(Reason.SLOT_CHANGED);
        }
        if (!toolBreak && snapshot.maxDurability() > 0) {
            return new Skip(Reason.DAMAGEABLE);
        }
        int source = toolBreak
                ? findToolSource(check, slots, settings, player.heldSlot(), reserved)
                : findSimilarSource(check, slots, settings, player.heldSlot(), reserved);
        if (source < 0) {
            return new Skip(Reason.NO_SOURCE);
        }
        return new Refill(source, check.slot());
    }

    /** Exactly one item of an allowed material. */
    public static boolean isAllowedRemainder(Check check, ItemView item) {
        return item.amount() == 1 && check.allowedRemainders().contains(item.material());
    }

    /** Main inventory before hotbar; smallest stack first; then lowest slot. */
    private static int findSimilarSource(Check check, @Nullable ItemView[] slots, RefillSettings settings,
                                         int heldSlot, Set<Integer> reserved) {
        ItemView snapshot = check.snapshot();
        return candidates(check, slots, settings, heldSlot, reserved)
                .filter(i -> slots[i].isSimilar(snapshot))
                .boxed()
                .min(Comparator.<Integer>comparingInt(i -> isHotbar(i) ? 1 : 0)
                        .thenComparingInt(i -> slots[i].amount())
                        .thenComparingInt(i -> i))
                .orElse(-1);
    }

    /**
     * The same tool once wear is ignored, with a compatible custom name. Prefer identical enchantments,
     * then main inventory, then least damaged, then lowest slot (SPEC §4.2).
     */
    private static int findToolSource(Check check, @Nullable ItemView[] slots, RefillSettings settings,
                                      int heldSlot, Set<Integer> reserved) {
        ItemView broken = check.snapshot();
        return candidates(check, slots, settings, heldSlot, reserved)
                .filter(i -> slots[i].material().equals(broken.material()))
                .filter(i -> !slots[i].hasCustomName() || slots[i].sameCustomName(broken))
                .filter(i -> slots[i].sameIgnoringWear(broken))
                .boxed()
                .min(Comparator.<Integer>comparingInt(i -> slots[i].sameEnchantments(broken) ? 0 : 1)
                        .thenComparingInt(i -> isHotbar(i) ? 1 : 0)
                        .thenComparingInt(i -> slots[i].damage())
                        .thenComparingInt(i -> i))
                .orElse(-1);
    }

    /**
     * Non-empty storage slots 0–35 other than the target, the held slot (taking the held stack would just
     * empty the main hand) and slots refilled earlier in this batch; the hotbar only if allowed.
     */
    private static IntStream candidates(Check check, @Nullable ItemView[] slots, RefillSettings settings,
                                        int heldSlot, Set<Integer> reserved) {
        return IntStream.range(settings.hotbarSource() ? 0 : HOTBAR_END, STORAGE_END)
                .filter(i -> i != check.slot() && i != heldSlot && !reserved.contains(i))
                .filter(i -> slots[i] != null && slots[i].amount() > 0);
    }

    private static boolean isHotbar(int slot) {
        return slot < HOTBAR_END;
    }
}

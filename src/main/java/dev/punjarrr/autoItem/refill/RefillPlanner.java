package dev.punjarrr.autoItem.refill;

import dev.punjarrr.autoItem.refill.Decision.Reason;
import dev.punjarrr.autoItem.refill.Decision.Refill;
import dev.punjarrr.autoItem.refill.Decision.Skip;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
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
        if (slots.length != INVENTORY_SIZE) {
            throw new IllegalArgumentException("expected " + INVENTORY_SIZE + " slots, got " + slots.length);
        }
        if (!Gates.allows(player, settings)) {
            return new Skip(Reason.GATE);
        }
        if (!check.world().equals(player.worldId()) || !check.gameMode().equals(player.gameMode())) {
            return new Skip(Reason.CONTEXT_CHANGED);
        }
        boolean toolBreak = check.isToolBreak();
        boolean triggerEnabled = toolBreak
                ? settings.triggers().contains(Trigger.TOOL_BREAK)
                : check.triggers().stream().anyMatch(settings.triggers()::contains);
        if (!triggerEnabled) {
            return new Skip(Reason.DISABLED);
        }
        ItemView snapshot = check.snapshot();
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
                ? findToolSource(check, slots, settings, player.heldSlot())
                : findSimilarSource(check, slots, settings, player.heldSlot());
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
                                         int heldSlot) {
        ItemView snapshot = check.snapshot();
        return candidates(check, slots, settings, heldSlot)
                .filter(i -> slots[i].isSimilar(snapshot))
                .boxed()
                .min(Comparator.<Integer>comparingInt(i -> isHotbar(i) ? 1 : 0)
                        .thenComparingInt(i -> slots[i].amount())
                        .thenComparingInt(i -> i))
                .orElse(-1);
    }

    /**
     * Same material, a compatible custom name and the same plugin data. Prefer identical enchantments,
     * then main inventory, then least damaged, then lowest slot (SPEC §4.2).
     */
    private static int findToolSource(Check check, @Nullable ItemView[] slots, RefillSettings settings,
                                      int heldSlot) {
        ItemView broken = check.snapshot();
        return candidates(check, slots, settings, heldSlot)
                .filter(i -> slots[i].material().equals(broken.material()))
                .filter(i -> !slots[i].hasCustomName() || slots[i].sameCustomName(broken))
                .filter(i -> slots[i].samePluginData(broken))
                .boxed()
                .min(Comparator.<Integer>comparingInt(i -> slots[i].sameEnchantments(broken) ? 0 : 1)
                        .thenComparingInt(i -> isHotbar(i) ? 1 : 0)
                        .thenComparingInt(i -> slots[i].damage())
                        .thenComparingInt(i -> i))
                .orElse(-1);
    }

    /**
     * Non-empty storage slots 0–35 other than the target and the held slot (taking the held stack would just
     * empty the main hand); the hotbar only if allowed.
     */
    private static IntStream candidates(Check check, @Nullable ItemView[] slots, RefillSettings settings,
                                        int heldSlot) {
        return IntStream.range(settings.hotbarSource() ? 0 : HOTBAR_END, STORAGE_END)
                .filter(i -> i != check.slot() && i != heldSlot)
                .filter(i -> slots[i] != null && slots[i].amount() > 0);
    }

    private static boolean isHotbar(int slot) {
        return slot < HOTBAR_END;
    }
}

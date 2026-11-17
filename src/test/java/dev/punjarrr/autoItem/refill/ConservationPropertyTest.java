package dev.punjarrr.autoItem.refill;

import dev.punjarrr.autoItem.refill.Decision.Refill;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static dev.punjarrr.autoItem.refill.RefillPlannerTest.REMAINDERS;
import static dev.punjarrr.autoItem.refill.RefillPlannerTest.WORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * U31: random inventories × random checks. Whatever the planner decides, applying it moves items only:
 * per-material totals (inventory + dropped) never change, the target is only overwritten when it was empty
 * or held exactly one allowed remainder, and sources are always storage slots other than target and held slot.
 */
class ConservationPropertyTest {

    private static final String[] MATERIALS = {"cobblestone", "torch", "mushroom_stew", "bowl", "potion",
            "glass_bottle", "water_bucket", "bucket", "milk_bucket", "ominous_bottle"};
    private static final String[] METAS = {"", "", "", "swiftness", "named"};

    private final RefillPlanner planner = new RefillPlanner();

    @Test
    void refillsOnlyMoveItems() {
        Random random = new Random(20261118L);
        int refills = 0;
        for (int round = 0; round < 20_000; round++) {
            ItemView[] slots = new ItemView[RefillPlanner.INVENTORY_SIZE];
            int filled = random.nextInt(42);
            for (int i = 0; i < filled; i++) {
                slots[random.nextInt(slots.length)] = randomItem(random, 1 + random.nextInt(64));
            }
            int heldSlot = random.nextInt(9);
            int target = random.nextInt(4) == 0 ? RefillPlanner.OFFHAND_SLOT : heldSlot;
            FakeItem snapshot = randomItem(random, 1);
            Check check = new Check(target, snapshot, REMAINDERS.allowedFor(snapshot.material()),
                    EnumSet.of(Trigger.CONSUME), WORLD, "SURVIVAL");
            RefillSettings settings = new RefillSettings(true, EnumSet.allOf(Trigger.class), random.nextBoolean(),
                    Set.of(), Set.of(), REMAINDERS);
            PlayerState player = new PlayerState(true, false, "SURVIVAL", WORLD, "world", true, true, heldSlot);

            ItemView targetBefore = slots[target];
            RefillPlannerTest.ModelInventory inventory = new RefillPlannerTest.ModelInventory(slots.clone());
            Map<String, Integer> before = inventory.totals();
            Decision decision = planner.plan(check, slots.clone(), settings, player);
            if (!(decision instanceof Refill refill)) {
                continue;
            }
            refills++;
            assertEquals(target, refill.target());
            assertTrue(refill.source() >= 0 && refill.source() < 36, "source in storage: " + refill);
            assertNotEquals(target, refill.source());
            assertNotEquals(heldSlot, refill.source());
            assertTrue(settings.hotbarSource() || refill.source() >= 9, "hotbar only when allowed");
            assertTrue(slots[refill.source()].isSimilar(snapshot), "source matches the used item");
            assertTrue(targetBefore == null || RefillPlanner.isAllowedRemainder(check, targetBefore),
                    "target overwritten only when empty or an allowed remainder: " + targetBefore);

            inventory.apply(refill);
            assertEquals(before, inventory.totals(), "per-material totals unchanged");
        }
        assertTrue(refills > 1_000, "the property should exercise many refills, got " + refills);
    }

    private static FakeItem randomItem(Random random, int amount) {
        return FakeItem.of(MATERIALS[random.nextInt(MATERIALS.length)], amount)
                .meta(METAS[random.nextInt(METAS.length)]);
    }
}

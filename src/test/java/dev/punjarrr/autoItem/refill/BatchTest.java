package dev.punjarrr.autoItem.refill;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static dev.punjarrr.autoItem.refill.FakeItem.key;
import static dev.punjarrr.autoItem.refill.RefillPlannerTest.OTHER_WORLD;
import static dev.punjarrr.autoItem.refill.RefillPlannerTest.WORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** U28: dedup of checks recorded for the same slot in one tick. */
class BatchTest {

    @Test
    void sameItemSameSlotMergesKeepingTheFirstSnapshot() {
        FakeItem first = FakeItem.of("bucket", 1);
        FakeItem second = FakeItem.of("bucket", 1);
        Batch batch = new Batch();
        batch.add(new Check(3, first, Set.of(key("water_bucket")), EnumSet.of(Trigger.RIGHT_CLICK), WORLD,
                "SURVIVAL"));
        batch.add(new Check(3, second, Set.of(key("milk_bucket")), EnumSet.of(Trigger.BUCKETS), OTHER_WORLD,
                "ADVENTURE"));

        List<Check> checks = batch.checks();
        assertEquals(1, checks.size());
        Check merged = checks.getFirst();
        assertSame(first, merged.snapshot());
        assertEquals(WORLD, merged.world());
        assertEquals("SURVIVAL", merged.gameMode());
        assertEquals(Set.of(key("water_bucket"), key("milk_bucket")), merged.allowedRemainders());
        // The right-click catch-all gives way to the specific trigger.
        assertEquals(Set.of(Trigger.BUCKETS), merged.triggers());
    }

    @Test
    void aDifferentItemInTheSameSlotReplacesTheEarlierCheck() {
        // The slot changed between the two records (an armor-stand swap): the item used last is refilled.
        Batch batch = new Batch();
        batch.add(new Check(0, FakeItem.of("diamond", 1), Set.of(), EnumSet.of(Trigger.RIGHT_CLICK), WORLD,
                "SURVIVAL"));
        FakeItem cobble = FakeItem.of("cobblestone", 1);
        batch.add(new Check(0, cobble, Set.of(), EnumSet.of(Trigger.BLOCK_PLACE), WORLD, "SURVIVAL"));

        Check only = batch.checks().getFirst();
        assertSame(cobble, only.snapshot());
        assertEquals(Set.of(Trigger.BLOCK_PLACE), only.triggers());
    }

    @Test
    void aToolRecordedAtDifferentDamageInOneUseStaysOneToolBreakCheck() {
        // Flint and steel, as Paper reports it: right-click (damage 63), break (64), then BlockPlaceEvent for
        // the fire with the pre-use copy restored (63 again).
        Batch batch = new Batch();
        batch.add(new Check(0, FakeItem.tool("flint_and_steel", 64, 63), Set.of(), EnumSet.of(Trigger.RIGHT_CLICK),
                WORLD, "SURVIVAL"));
        batch.add(new Check(0, FakeItem.tool("flint_and_steel", 64, 64), Set.of(), EnumSet.of(Trigger.TOOL_BREAK),
                WORLD, "SURVIVAL"));
        batch.add(new Check(0, FakeItem.tool("flint_and_steel", 64, 63), Set.of(), EnumSet.of(Trigger.BLOCK_PLACE),
                WORLD, "SURVIVAL"));

        assertEquals(1, batch.checks().size());
        assertTrue(batch.checks().getFirst().isToolBreak());
    }

    @Test
    void anotherToolOfTheSameTypeSwappedInIsADifferentItem() {
        Batch batch = new Batch();
        batch.add(new Check(0, FakeItem.tool("diamond_pickaxe", 1561, 10).named("X"), Set.of(),
                EnumSet.of(Trigger.RIGHT_CLICK), WORLD, "SURVIVAL"));
        FakeItem broken = FakeItem.tool("diamond_pickaxe", 1561, 1561);
        batch.add(new Check(0, broken, Set.of(), EnumSet.of(Trigger.TOOL_BREAK), WORLD, "SURVIVAL"));

        assertSame(broken, batch.checks().getFirst().snapshot());
        assertTrue(batch.checks().getFirst().isToolBreak());
    }

    @Test
    void toolBreakWinsWhenMerged() {
        FakeItem shears = FakeItem.tool("shears", 238, 237);
        Batch batch = new Batch();
        batch.add(new Check(0, shears, Set.of(), EnumSet.of(Trigger.RIGHT_CLICK), WORLD, "SURVIVAL"));
        batch.add(new Check(0, shears, Set.of(), EnumSet.of(Trigger.TOOL_BREAK), WORLD, "SURVIVAL"));
        assertTrue(batch.checks().getFirst().isToolBreak());
    }

    @Test
    void removeAndClearForgetChecks() {
        Batch batch = new Batch();
        batch.add(new Check(0, FakeItem.of("torch", 1), Set.of(), EnumSet.of(Trigger.BLOCK_PLACE), WORLD, "SURVIVAL"));
        batch.add(new Check(40, FakeItem.of("torch", 1), Set.of(), EnumSet.of(Trigger.BLOCK_PLACE), WORLD,
                "SURVIVAL"));
        batch.remove(0);
        assertEquals(List.of(40), batch.checks().stream().map(Check::slot).toList());
        batch.clear();
        assertTrue(batch.isEmpty());
    }

    @Test
    void mainHandRunsBeforeOffhand() {
        Batch batch = new Batch();
        batch.add(new Check(40, FakeItem.of("torch", 1), Set.of(), EnumSet.of(Trigger.BLOCK_PLACE), WORLD,
                "SURVIVAL"));
        batch.add(new Check(7, FakeItem.of("bread", 1), Set.of(), EnumSet.of(Trigger.CONSUME), WORLD, "SURVIVAL"));
        assertEquals(List.of(7, 40), batch.checks().stream().map(Check::slot).toList());
    }
}

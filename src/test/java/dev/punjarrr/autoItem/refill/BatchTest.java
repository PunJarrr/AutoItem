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
    void sameSlotMergesKeepingTheFirstSnapshot() {
        FakeItem first = FakeItem.of("bucket", 1);
        FakeItem second = FakeItem.of("bucket", 1).meta("other");
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
        assertEquals(Set.of(Trigger.RIGHT_CLICK, Trigger.BUCKETS), merged.triggers());
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
    void mainHandRunsBeforeOffhand() {
        Batch batch = new Batch();
        batch.add(new Check(40, FakeItem.of("torch", 1), Set.of(), EnumSet.of(Trigger.BLOCK_PLACE), WORLD,
                "SURVIVAL"));
        batch.add(new Check(7, FakeItem.of("bread", 1), Set.of(), EnumSet.of(Trigger.CONSUME), WORLD, "SURVIVAL"));
        assertEquals(List.of(7, 40), batch.checks().stream().map(Check::slot).toList());
    }
}

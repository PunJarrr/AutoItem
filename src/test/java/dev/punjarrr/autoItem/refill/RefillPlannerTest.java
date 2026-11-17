package dev.punjarrr.autoItem.refill;

import dev.punjarrr.autoItem.refill.Decision.Reason;
import dev.punjarrr.autoItem.refill.Decision.Refill;
import dev.punjarrr.autoItem.refill.Decision.Skip;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.punjarrr.autoItem.refill.FakeItem.key;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** SPEC §8 unit tests U1–U26 and U29 for the pure planner. U31 lives in {@link ConservationPropertyTest}. */
class RefillPlannerTest {

    static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID OTHER_WORLD = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final Remainders REMAINDERS = new Remainders(Map.of(
            key("mushroom_stew"), key("bowl"),
            key("potion"), key("glass_bottle"),
            key("honey_bottle"), key("glass_bottle"),
            key("water_bucket"), key("bucket"),
            key("milk_bucket"), key("bucket"),
            key("ominous_bottle"), Remainders.NONE));

    private final RefillPlanner planner = new RefillPlanner();

    // ---- fixtures ----

    static RefillSettings settings() {
        return settings(true, true, Set.of());
    }

    static RefillSettings settings(boolean hotbar, boolean toolBreak, Set<String> ignored) {
        Set<Trigger> triggers = EnumSet.allOf(Trigger.class);
        if (!toolBreak) {
            triggers.remove(Trigger.TOOL_BREAK);
        }
        return new RefillSettings(true, triggers, hotbar, ignored, Set.of("disabled_world"), REMAINDERS);
    }

    static PlayerState survival() {
        return new PlayerState(true, false, "SURVIVAL", WORLD, "world", true, true, 0);
    }

    static Check check(int slot, ItemView snapshot, Trigger trigger) {
        return new Check(slot, snapshot, REMAINDERS.allowedFor(snapshot.material()), EnumSet.of(trigger), WORLD,
                "SURVIVAL");
    }

    static ItemView[] empty() {
        return new ItemView[RefillPlanner.INVENTORY_SIZE];
    }

    private Decision plan(Check check, ItemView[] slots) {
        return planner.plan(check, slots, settings(), survival());
    }

    private static void assertRefill(int source, int target, Decision decision) {
        assertEquals(new Refill(source, target), decision);
    }

    private static void assertSkip(Reason reason, Decision decision) {
        assertEquals(new Skip(reason), decision);
    }

    // ---- U1–U6: the target slot ----

    @Test
    void u1_emptyTargetRefillsFromMainInventory() {
        ItemView[] slots = empty();
        slots[20] = FakeItem.of("cobblestone", 64);
        assertRefill(20, 3, plan(check(3, FakeItem.of("cobblestone", 1), Trigger.BLOCK_PLACE), slots));
    }

    @Test
    void u2_targetStillHoldsTheItemMeansNotUsedUp() {
        ItemView[] slots = empty();
        slots[3] = FakeItem.of("cobblestone", 1);
        slots[20] = FakeItem.of("cobblestone", 64);
        assertSkip(Reason.NOT_USED_UP, plan(check(3, FakeItem.of("cobblestone", 1), Trigger.BLOCK_PLACE), slots));
    }

    @Test
    void u3_targetHoldsSomethingElse() {
        ItemView[] slots = empty();
        slots[3] = FakeItem.of("dirt", 1);
        slots[20] = FakeItem.of("cobblestone", 64);
        assertSkip(Reason.SLOT_CHANGED, plan(check(3, FakeItem.of("cobblestone", 1), Trigger.BLOCK_PLACE), slots));
    }

    @Test
    void u4_targetHoldsTheAllowedRemainder() {
        ItemView[] slots = empty();
        slots[0] = FakeItem.of("bowl", 1);
        slots[10] = FakeItem.of("mushroom_stew", 1);
        assertRefill(10, 0, plan(check(0, FakeItem.of("mushroom_stew", 1), Trigger.CONSUME), slots));
    }

    @Test
    void u5_twoBowlsAreNotARemainderButOneRenamedBowlIs() {
        ItemView[] slots = empty();
        slots[0] = FakeItem.of("bowl", 2);
        slots[10] = FakeItem.of("mushroom_stew", 1);
        Check check = check(0, FakeItem.of("mushroom_stew", 1), Trigger.CONSUME);
        assertSkip(Reason.SLOT_CHANGED, plan(check, slots));

        slots[0] = FakeItem.of("bowl", 1).named("Lucky bowl");
        assertRefill(10, 0, plan(check, slots));
    }

    @Test
    void u6_ominousBottleLeavesNothing() {
        ItemView[] slots = empty();
        slots[0] = FakeItem.of("glass_bottle", 1);
        slots[10] = FakeItem.of("ominous_bottle", 1);
        Check check = check(0, FakeItem.of("ominous_bottle", 1), Trigger.CONSUME);
        assertSkip(Reason.SLOT_CHANGED, plan(check, slots));

        slots[0] = null;
        assertRefill(10, 0, plan(check, slots));
    }

    // ---- U7–U11: source slots ----

    @Test
    void u7_mainInventoryBeatsHotbar() {
        ItemView[] slots = empty();
        slots[1] = FakeItem.of("cobblestone", 2);
        slots[30] = FakeItem.of("cobblestone", 64);
        assertRefill(30, 0, plan(check(0, FakeItem.of("cobblestone", 1), Trigger.BLOCK_PLACE), slots));
    }

    @Test
    void u8_smallestStackFirstThenLowestSlot() {
        ItemView[] slots = empty();
        slots[9] = FakeItem.of("cobblestone", 64);
        slots[25] = FakeItem.of("cobblestone", 5);
        slots[15] = FakeItem.of("cobblestone", 5);
        slots[12] = FakeItem.of("cobblestone", 30);
        assertRefill(15, 0, plan(check(0, FakeItem.of("cobblestone", 1), Trigger.BLOCK_PLACE), slots));
    }

    @Test
    void u9_hotbarSourceOff() {
        ItemView[] slots = empty();
        slots[5] = FakeItem.of("cobblestone", 64);
        Decision decision = planner.plan(check(0, FakeItem.of("cobblestone", 1), Trigger.BLOCK_PLACE), slots,
                settings(false, false, Set.of()), survival());
        assertSkip(Reason.NO_SOURCE, decision);
    }

    @Test
    void u10_theHeldSlotIsNeverASource() {
        ItemView[] slots = slotsWith(0, FakeItem.of("cobblestone", 64));
        assertSkip(Reason.NO_SOURCE, plan(check(40, FakeItem.of("cobblestone", 1), Trigger.BLOCK_PLACE), slots));
    }

    @Test
    void u10_hotbarSourceOnNeverUsesTheTargetItself() {
        ItemView[] slots = empty();
        slots[5] = FakeItem.of("cobblestone", 64);
        assertRefill(5, 0, plan(check(0, FakeItem.of("cobblestone", 1), Trigger.BLOCK_PLACE), slots));

        // Offhand target: the hotbar is a source, the offhand (40) itself never is.
        assertRefill(5, 40, plan(check(40, FakeItem.of("cobblestone", 1), Trigger.BLOCK_PLACE), slots));
    }

    @ParameterizedTest
    @ValueSource(ints = {36, 37, 38, 39, 40})
    void u11_armorAndOffhandAreNeverSources(int slot) {
        ItemView[] slots = empty();
        slots[slot] = FakeItem.of("cobblestone", 64);
        assertSkip(Reason.NO_SOURCE, plan(check(0, FakeItem.of("cobblestone", 1), Trigger.BLOCK_PLACE), slots));
    }

    // ---- U12–U16: matching ----

    @Test
    void u12_onlyTheIdenticalPotion() {
        ItemView speed = FakeItem.of("potion", 1).meta("swiftness");
        ItemView[] slots = empty();
        slots[10] = FakeItem.of("potion", 1).meta("strong_swiftness");
        slots[11] = FakeItem.of("potion", 1).meta("slowness");
        assertSkip(Reason.NO_SOURCE, plan(check(0, speed, Trigger.CONSUME), slots));

        slots[12] = FakeItem.of("potion", 1).meta("swiftness");
        assertRefill(12, 0, plan(check(0, speed, Trigger.CONSUME), slots));
    }

    @Test
    void u13_splashLingeringAndDrinkableNeverCrossMatch() {
        ItemView[] slots = empty();
        slots[10] = FakeItem.of("splash_potion", 1).meta("swiftness");
        slots[11] = FakeItem.of("lingering_potion", 1).meta("swiftness");
        assertSkip(Reason.NO_SOURCE, plan(check(0, FakeItem.of("potion", 1).meta("swiftness"), Trigger.CONSUME),
                slots));
    }

    @Test
    void u14_tippedArrowsMatchExactly() {
        ItemView poison = FakeItem.of("tipped_arrow", 1).meta("poison");
        ItemView[] slots = empty();
        slots[10] = FakeItem.of("tipped_arrow", 64).meta("harming");
        slots[11] = FakeItem.of("arrow", 64);
        assertSkip(Reason.NO_SOURCE, plan(check(40, poison, Trigger.BOWS), slots));

        slots[12] = FakeItem.of("tipped_arrow", 64).meta("poison");
        assertRefill(12, 40, plan(check(40, poison, Trigger.BOWS), slots));
    }

    @Test
    void u15_enchantedOrNamedOnlyFromIdentical() {
        ItemView named = FakeItem.of("golden_apple", 1).named("Snack");
        ItemView[] slots = empty();
        slots[10] = FakeItem.of("golden_apple", 64);
        slots[11] = FakeItem.of("golden_apple", 64).named("Other");
        assertSkip(Reason.NO_SOURCE, plan(check(0, named, Trigger.CONSUME), slots));
        slots[12] = FakeItem.of("golden_apple", 3).named("Snack");
        assertRefill(12, 0, plan(check(0, named, Trigger.CONSUME), slots));

        ItemView enchanted = FakeItem.of("stone", 1).enchanted(Map.of("minecraft:unbreaking", 1));
        assertSkip(Reason.NO_SOURCE, plan(check(1, enchanted, Trigger.BLOCK_PLACE), slotsWith(20,
                FakeItem.of("stone", 64))));
        assertRefill(20, 1, plan(check(1, enchanted, Trigger.BLOCK_PLACE), slotsWith(20,
                FakeItem.of("stone", 64).enchanted(Map.of("minecraft:unbreaking", 1)))));
    }

    @Test
    void u16_ignoredItemsAreNeverRefilled() {
        ItemView[] slots = slotsWith(20, FakeItem.of("totem_of_undying", 1));
        Decision decision = planner.plan(check(40, FakeItem.of("totem_of_undying", 1), Trigger.RIGHT_CLICK), slots,
                settings(true, false, Set.of(key("totem_of_undying"))), survival());
        assertSkip(Reason.IGNORED, decision);
    }

    // ---- U17–U20: gates and switches ----

    @ParameterizedTest
    @EnumSource(value = Trigger.class, names = "TOOL_BREAK", mode = EnumSource.Mode.EXCLUDE)
    void u17_triggerTurnedOffAtTaskTime(Trigger trigger) {
        Set<Trigger> enabled = EnumSet.allOf(Trigger.class);
        enabled.remove(trigger);
        RefillSettings settings = new RefillSettings(true, enabled, true, Set.of(), Set.of(), REMAINDERS);
        Decision decision = planner.plan(check(0, FakeItem.of("cobblestone", 1), trigger),
                slotsWith(20, FakeItem.of("cobblestone", 64)), settings, survival());
        assertSkip(Reason.DISABLED, decision);
    }

    @Test
    void u17_aMergedCheckRunsIfAnyOfItsTriggersIsOn() {
        Check merged = check(0, FakeItem.of("cobblestone", 1), Trigger.BLOCK_PLACE)
                .merge(check(0, FakeItem.of("cobblestone", 1), Trigger.RIGHT_CLICK));
        RefillSettings settings = new RefillSettings(true, EnumSet.of(Trigger.RIGHT_CLICK), true, Set.of(),
                Set.of(), REMAINDERS);
        assertRefill(20, 0, planner.plan(merged, slotsWith(20, FakeItem.of("cobblestone", 64)), settings,
                survival()));
    }

    @Nested
    class U18Gates {
        private final Check check = check(0, FakeItem.of("cobblestone", 1), Trigger.BLOCK_PLACE);
        private final ItemView[] slots = slotsWith(20, FakeItem.of("cobblestone", 64));

        private void assertGate(PlayerState player) {
            assertSkip(Reason.GATE, planner.plan(check, slots, settings(), player));
        }

        @Test
        void offline() {
            assertGate(new PlayerState(false, false, "SURVIVAL", WORLD, "world", true, true, 0));
        }

        @Test
        void dead() {
            assertGate(new PlayerState(true, true, "SURVIVAL", WORLD, "world", true, true, 0));
        }

        @ParameterizedTest
        @ValueSource(strings = {"CREATIVE", "SPECTATOR"})
        void creativeOrSpectator(String gameMode) {
            Check sameMode = new Check(0, FakeItem.of("cobblestone", 1), Set.of(), EnumSet.of(Trigger.BLOCK_PLACE),
                    WORLD, gameMode);
            assertSkip(Reason.GATE, planner.plan(sameMode, slots, settings(),
                    new PlayerState(true, false, gameMode, WORLD, "world", true, true, 0)));
        }

        @Test
        void adventureIsAllowed() {
            Check adventure = new Check(0, FakeItem.of("cobblestone", 1), Set.of(),
                    EnumSet.of(Trigger.BLOCK_PLACE), WORLD, "ADVENTURE");
            assertRefill(20, 0, planner.plan(adventure, slots, settings(),
                    new PlayerState(true, false, "ADVENTURE", WORLD, "world", true, true, 0)));
        }

        @Test
        void disabledWorld() {
            assertGate(new PlayerState(true, false, "SURVIVAL", WORLD, "disabled_world", true, true, 0));
        }

        @Test
        void toggledOff() {
            assertGate(new PlayerState(true, false, "SURVIVAL", WORLD, "world", false, true, 0));
        }

        @Test
        void noPermission() {
            assertGate(new PlayerState(true, false, "SURVIVAL", WORLD, "world", true, false, 0));
        }

        @Test
        void masterSwitchOff() {
            RefillSettings off = new RefillSettings(false, EnumSet.allOf(Trigger.class), true, Set.of(), Set.of(),
                    REMAINDERS);
            assertSkip(Reason.GATE, planner.plan(check, slots, off, survival()));
        }
    }

    @Test
    void u19_worldOrGameModeChangedSinceTheEvent() {
        Check check = check(0, FakeItem.of("cobblestone", 1), Trigger.BLOCK_PLACE);
        ItemView[] slots = slotsWith(20, FakeItem.of("cobblestone", 64));
        assertSkip(Reason.CONTEXT_CHANGED, planner.plan(check, slots, settings(),
                new PlayerState(true, false, "SURVIVAL", OTHER_WORLD, "world_nether", true, true, 0)));
        assertSkip(Reason.CONTEXT_CHANGED, planner.plan(check, slots, settings(),
                new PlayerState(true, false, "ADVENTURE", WORLD, "world", true, true, 0)));
    }

    @Test
    void u20_damageableItemsOnlyRefillThroughToolBreak() {
        ItemView trident = FakeItem.tool("trident", 250, 10);
        ItemView[] slots = slotsWith(20, FakeItem.tool("trident", 250, 10));
        assertSkip(Reason.DAMAGEABLE, plan(check(0, trident, Trigger.THROWABLES), slots));

        ItemView shears = FakeItem.tool("shears", 238, 237);
        assertSkip(Reason.DAMAGEABLE, plan(check(0, shears, Trigger.RIGHT_CLICK),
                slotsWith(20, FakeItem.tool("shears", 238, 237))));
    }

    // ---- U21–U26: tool break ----

    @Nested
    class ToolBreak {
        private final Map<String, Integer> efficiency = Map.of("minecraft:efficiency", 5);
        private final ItemView broken = FakeItem.tool("diamond_pickaxe", 1561, 1560).enchanted(efficiency);

        private Decision planBreak(ItemView[] slots) {
            return planner.plan(check(2, broken, Trigger.TOOL_BREAK), slots, settings(true, true, Set.of()),
                    survival());
        }

        @Test
        void u21_identicalEnchantmentsWinOverMainInventory() {
            ItemView[] slots = empty();
            slots[20] = FakeItem.tool("diamond_pickaxe", 1561, 0);
            slots[6] = FakeItem.tool("diamond_pickaxe", 1561, 500).enchanted(efficiency);
            assertRefill(6, 2, planBreak(slots));
        }

        @Test
        void u22_customNamedCandidateNeverReplacesUnnamedTool() {
            ItemView[] slots = slotsWith(20, FakeItem.tool("diamond_pickaxe", 1561, 0).named("Excavator")
                    .enchanted(efficiency));
            assertSkip(Reason.NO_SOURCE, planBreak(slots));
        }

        @Test
        void u23_identicallyNamedToolIsPicked() {
            ItemView namedBroken = ((FakeItem) broken).named("Excavator");
            ItemView[] slots = slotsWith(20, FakeItem.tool("diamond_pickaxe", 1561, 3).named("Excavator"));
            assertRefill(20, 2, planner.plan(check(2, namedBroken, Trigger.TOOL_BREAK), slots,
                    settings(true, true, Set.of()), survival()));
        }

        @Test
        void u24_differentPluginDataIsNeverPicked() {
            ItemView[] slots = slotsWith(20, FakeItem.tool("diamond_pickaxe", 1561, 0).pluginData("custom:drill")
                    .enchanted(efficiency));
            assertSkip(Reason.NO_SOURCE, planBreak(slots));
        }

        @Test
        void u25_mainBeforeHotbarThenLeastDamaged() {
            ItemView[] slots = empty();
            slots[4] = FakeItem.tool("diamond_pickaxe", 1561, 0).enchanted(efficiency);
            slots[30] = FakeItem.tool("diamond_pickaxe", 1561, 900).enchanted(efficiency);
            slots[25] = FakeItem.tool("diamond_pickaxe", 1561, 100).enchanted(efficiency);
            assertRefill(25, 2, planBreak(slots));
        }

        @Test
        void u25_otherMaterialsNeverMatch() {
            assertSkip(Reason.NO_SOURCE, planBreak(slotsWith(20, FakeItem.tool("iron_pickaxe", 250, 0))));
        }

        @Test
        void u26_toolBreakTurnedOff() {
            Decision decision = planner.plan(check(2, broken, Trigger.TOOL_BREAK),
                    slotsWith(20, FakeItem.tool("diamond_pickaxe", 1561, 0)), settings(true, false, Set.of()),
                    survival());
            assertSkip(Reason.DISABLED, decision);
        }
    }

    // ---- U29: two checks, one source ----

    @Test
    void u29_bothHandsCompeteForOneSource() {
        ModelInventory inventory = new ModelInventory(empty());
        inventory.slots[20] = FakeItem.of("torch", 10);
        Check main = check(0, FakeItem.of("torch", 1), Trigger.BLOCK_PLACE);
        Check off = check(40, FakeItem.of("torch", 1), Trigger.BLOCK_PLACE);

        Decision first = plan(main, inventory.slots.clone());
        assertRefill(20, 0, first);
        inventory.apply((Refill) first);

        // Re-read after the first refill: the only torches left are in the held slot, which is never a
        // source, so the offhand doesn't empty the main hand again.
        assertSkip(Reason.NO_SOURCE, plan(off, inventory.slots.clone()));
        // A second stack elsewhere goes to the offhand.
        inventory.slots[5] = FakeItem.of("torch", 3);
        assertRefill(5, 40, plan(off, inventory.slots.clone()));
    }

    static ItemView[] slotsWith(int slot, ItemView item) {
        ItemView[] slots = empty();
        slots[slot] = item;
        return slots;
    }

    /** Applies refills to a model inventory the way {@code RefillExecutor} does, counting drops. */
    static final class ModelInventory {
        final ItemView[] slots;
        final Map<String, Integer> ground = new HashMap<>();

        ModelInventory(ItemView[] slots) {
            this.slots = slots;
        }

        void apply(Refill refill) {
            ItemView moved = slots[refill.source()];
            ItemView remainder = slots[refill.target()];
            slots[refill.source()] = null;
            slots[refill.target()] = moved;
            if (remainder != null) {
                addItem((FakeItem) remainder);
            }
        }

        /** Like Bukkit's addItem over storage slots 0–35: top up similar stacks, then empty slots, then drop. */
        private void addItem(FakeItem item) {
            int left = item.amount();
            int max = item.maxDurability() > 0 ? 1 : 64;
            for (int i = 0; i < 36 && left > 0; i++) {
                if (slots[i] != null && slots[i].isSimilar(item) && slots[i].amount() < max) {
                    int add = Math.min(left, max - slots[i].amount());
                    slots[i] = ((FakeItem) slots[i]).amount(slots[i].amount() + add);
                    left -= add;
                }
            }
            for (int i = 0; i < 36 && left > 0; i++) {
                if (slots[i] == null) {
                    int add = Math.min(left, max);
                    slots[i] = item.amount(add);
                    left -= add;
                }
            }
            if (left > 0) {
                ground.merge(item.material(), left, Integer::sum);
            }
        }

        Map<String, Integer> totals() {
            Map<String, Integer> totals = new HashMap<>(ground);
            for (ItemView item : slots) {
                if (item != null) {
                    totals.merge(item.material(), item.amount(), Integer::sum);
                }
            }
            return totals;
        }
    }
}

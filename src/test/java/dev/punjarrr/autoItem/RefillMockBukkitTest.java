package dev.punjarrr.autoItem;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC §8 K tests (and the API-level parts of some X tests): the real listeners, recorder, entity-scheduler
 * task and executor on a MockBukkit server. Each test fires the event as the server would, then applies the
 * vanilla effect (the decrement, the bowl) by hand, then ticks once.
 */
class RefillMockBukkitTest {

    private ServerMock server;
    private AutoItem plugin;
    private PlayerMock player;
    private PlayerInventory inventory;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = AutoItemForTests.load();
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        inventory = player.getInventory();
        inventory.clear();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ---- helpers ----

    private void place(EquipmentSlot hand) {
        int slot = hand == EquipmentSlot.HAND ? inventory.getHeldItemSlot() : 40;
        Block block = player.getWorld().getBlockAt(0, 70, 0);
        BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), block.getRelative(BlockFace.DOWN),
                inventory.getItem(slot), player, true, hand);
        server.getPluginManager().callEvent(event);
    }

    private void consume(EquipmentSlot hand) {
        int slot = hand == EquipmentSlot.HAND ? inventory.getHeldItemSlot() : 40;
        server.getPluginManager().callEvent(new PlayerItemConsumeEvent(player, inventory.getItem(slot).clone(), hand));
    }

    /** Per-material count over all 41 slots, the cursor and dropped items. */
    private Map<Material, Integer> totals() {
        Map<Material, Integer> totals = new HashMap<>();
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && !stack.isEmpty()) {
                totals.merge(stack.getType(), stack.getAmount(), Integer::sum);
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (!cursor.isEmpty()) {
            totals.merge(cursor.getType(), cursor.getAmount(), Integer::sum);
        }
        for (World world : server.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                totals.merge(item.getItemStack().getType(), item.getItemStack().getAmount(), Integer::sum);
            }
        }
        return totals;
    }

    private static void assertStack(Material material, int amount, ItemStack actual) {
        assertTrue(actual != null && !actual.isEmpty(), () -> "expected " + amount + " " + material + ", was empty");
        assertEquals(material, actual.getType());
        assertEquals(amount, actual.getAmount());
    }

    private static void assertEmpty(ItemStack actual) {
        assertTrue(actual == null || actual.isEmpty(), () -> "expected empty, was " + actual);
    }

    private void tick() {
        server.getScheduler().performOneTick();
    }

    // ---- K1–K5 ----

    @Test
    void k1_lastBlockRefillsFromTheSmallestMainInventoryStack() {
        inventory.setHeldItemSlot(3);
        inventory.setItem(3, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));
        inventory.setItem(25, new ItemStack(Material.COBBLESTONE, 10));
        inventory.setItem(5, new ItemStack(Material.COBBLESTONE, 2));

        place(EquipmentSlot.HAND);
        inventory.setItem(3, null); // vanilla uses the block
        Map<Material, Integer> before = totals();
        tick();

        assertStack(Material.COBBLESTONE, 10, inventory.getItem(3));
        assertEmpty(inventory.getItem(25));
        assertStack(Material.COBBLESTONE, 64, inventory.getItem(20));
        assertStack(Material.COBBLESTONE, 2, inventory.getItem(5));
        assertEquals(before, totals());
    }

    @Test
    void k1_offhandIsRefilledToo() {
        inventory.setItemInOffHand(new ItemStack(Material.TORCH, 1));
        inventory.setItem(30, new ItemStack(Material.TORCH, 16));

        place(EquipmentSlot.OFF_HAND);
        inventory.setItemInOffHand(null);
        tick();

        assertStack(Material.TORCH, 16, inventory.getItemInOffHand());
        assertEmpty(inventory.getItem(30));
    }

    @Test
    void k2_lastStewRefillsAndTheBowlMovesOut() {
        inventory.setItem(0, new ItemStack(Material.MUSHROOM_STEW, 1));
        inventory.setItem(10, new ItemStack(Material.MUSHROOM_STEW, 1));

        consume(EquipmentSlot.HAND);
        inventory.setItem(0, new ItemStack(Material.BOWL, 1)); // vanilla leaves the bowl
        Map<Material, Integer> before = totals();
        tick();

        assertStack(Material.MUSHROOM_STEW, 1, inventory.getItem(0));
        assertStack(Material.BOWL, 1, inventory.getItem(10)); // the remainder takes the source's slot
        assertEquals(before, totals());
    }

    @Test
    void k3_fullInventoryStillConserves() {
        for (int i = 0; i < 36; i++) {
            inventory.setItem(i, new ItemStack(Material.STONE, 64));
        }
        inventory.setItem(0, new ItemStack(Material.MUSHROOM_STEW, 1));
        inventory.setItem(10, new ItemStack(Material.MUSHROOM_STEW, 1));

        consume(EquipmentSlot.HAND);
        inventory.setItem(0, new ItemStack(Material.BOWL, 1));
        Map<Material, Integer> before = totals();
        tick();

        assertStack(Material.MUSHROOM_STEW, 1, inventory.getItem(0));
        // The source slot was just emptied, so the bowl always has room there.
        assertStack(Material.BOWL, 1, inventory.getItem(10));
        assertEquals(before, totals());
    }

    @Test
    void k3_offhandStewWithAnEmptyHeldSlotNeverPutsTheBowlInTheHand() {
        inventory.setHeldItemSlot(0); // empty
        inventory.setItemInOffHand(new ItemStack(Material.MUSHROOM_STEW, 1));
        inventory.setItem(25, new ItemStack(Material.MUSHROOM_STEW, 1));

        consume(EquipmentSlot.OFF_HAND);
        inventory.setItemInOffHand(new ItemStack(Material.BOWL, 1));
        tick();

        assertStack(Material.MUSHROOM_STEW, 1, inventory.getItemInOffHand());
        assertEmpty(inventory.getItem(0));
        assertStack(Material.BOWL, 1, inventory.getItem(25));
    }

    @Test
    void k4_pendingChecksAreDroppedWhenThePluginIsDisabled() {
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));
        place(EquipmentSlot.HAND);
        inventory.setItem(0, null);
        server.getPluginManager().disablePlugin(plugin);
        tick();

        assertEmpty(inventory.getItem(0));
        assertStack(Material.COBBLESTONE, 64, inventory.getItem(20));
    }

    @Test
    void k4_aTaskQueuedBeforeADisableAndReEnableNeverRuns() {
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));
        place(EquipmentSlot.HAND);
        inventory.setItem(0, null);
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin);
        tick();

        assertEmpty(inventory.getItem(0));
        assertStack(Material.COBBLESTONE, 64, inventory.getItem(20));
    }

    @Test
    void x3_twoHotbarChecksInOneTickNeverTakeEachOthersSlot() {
        // Hacked client: right-click with a single diamond in slot 3 (nothing used up), scroll to slot 0, place
        // the last cobblestone there, all in one tick. Slot 3 is a pending target, so slot 0's check must not
        // pull from it even though it's a hotbar slot that is no longer held.
        inventory.setHeldItemSlot(3);
        inventory.setItem(3, new ItemStack(Material.COBBLESTONE, 1));
        place(EquipmentSlot.HAND); // records slot 3; the block is "not consumed" (slot 3 keeps it)
        inventory.setHeldItemSlot(0);
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        place(EquipmentSlot.HAND);
        inventory.setItem(0, null); // slot 0's block is used up
        tick();

        assertEmpty(inventory.getItem(0));
        assertStack(Material.COBBLESTONE, 1, inventory.getItem(3));
    }

    @Test
    void k4_cancelledEventRecordsNothing() {
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));
        Block block = player.getWorld().getBlockAt(0, 70, 0);
        BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), block.getRelative(BlockFace.DOWN),
                inventory.getItem(0), player, true, EquipmentSlot.HAND);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);

        assertEquals(0, plugin.recorder().pendingPlayers());
    }

    @Test
    void k5_onlyTheLastItemIsRecorded() {
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 2));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));

        place(EquipmentSlot.HAND);
        assertEquals(0, plugin.recorder().pendingPlayers());

        // X2: the second-to-last was placed, then Q dropped the last one in the same tick: no refill.
        inventory.setItem(0, null);
        tick();
        assertEmpty(inventory.getItem(0));
        assertStack(Material.COBBLESTONE, 64, inventory.getItem(20));
    }

    @Test
    void k7_toggleOffMeansNoRefill() {
        player.getPersistentDataContainer().set(new NamespacedKey(plugin, "enabled"), PersistentDataType.BYTE,
                (byte) 0);
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));

        place(EquipmentSlot.HAND);
        assertEquals(0, plugin.recorder().pendingPlayers());
    }

    // ---- exploit cases that can be reproduced at the API level ----

    @Test
    void x1_swapHandsInTheSameTickAborts() {
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItemInOffHand(new ItemStack(Material.SHIELD, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));

        place(EquipmentSlot.HAND);
        inventory.setItem(0, null);
        // F: the shield moves to the main hand
        inventory.setItem(0, inventory.getItemInOffHand());
        inventory.setItemInOffHand(null);
        Map<Material, Integer> before = totals();
        tick();

        assertStack(Material.SHIELD, 1, inventory.getItem(0));
        assertStack(Material.COBBLESTONE, 64, inventory.getItem(20));
        assertEquals(before, totals());
    }

    @Test
    void x3_scrollingAwayStillRefillsTheSlotThatWasUsed() {
        inventory.setItem(2, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setHeldItemSlot(2);
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));

        place(EquipmentSlot.HAND);
        inventory.setItem(2, null);
        inventory.setHeldItemSlot(6);
        tick();

        assertStack(Material.COBBLESTONE, 64, inventory.getItem(2));
        assertEmpty(inventory.getItem(6));
    }

    @Test
    void x6_gameModeChangeBeforeTheTaskAborts() {
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));

        place(EquipmentSlot.HAND);
        inventory.setItem(0, null);
        player.setGameMode(GameMode.ADVENTURE);
        tick();

        assertEmpty(inventory.getItem(0));
        assertStack(Material.COBBLESTONE, 64, inventory.getItem(20));
    }

    @Test
    void x6_worldChangeBeforeTheTaskAborts() {
        World other = server.addSimpleWorld("other");
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));

        place(EquipmentSlot.HAND);
        inventory.setItem(0, null);
        player.teleport(other.getSpawnLocation());
        tick();

        assertEmpty(inventory.getItem(0));
        assertStack(Material.COBBLESTONE, 64, inventory.getItem(20));
    }

    @Test
    void x6_deathBeforeTheTaskAborts() {
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));

        place(EquipmentSlot.HAND);
        inventory.setItem(0, null);
        player.setHealth(0);
        tick();

        assertEmpty(inventory.getItem(0));
    }

    @Test
    void x6_logoutBeforeTheTaskAborts() {
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));

        place(EquipmentSlot.HAND);
        inventory.setItem(0, null);
        player.disconnect();
        tick();

        assertEmpty(inventory.getItem(0));
        assertStack(Material.COBBLESTONE, 64, inventory.getItem(20));
    }

    @Test
    void x8_anotherPluginWritingTheSlotInTheSameTickIsNeverOverwritten() {
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));

        place(EquipmentSlot.HAND);
        inventory.setItem(0, new ItemStack(Material.DIRT, 1)); // another plugin, same tick
        Map<Material, Integer> before = totals();
        tick();

        assertStack(Material.DIRT, 1, inventory.getItem(0));
        assertStack(Material.COBBLESTONE, 64, inventory.getItem(20));
        assertEquals(before, totals());
    }

    @Test
    void x8_anotherPluginRestoringTheItemMeansNotUsedUp() {
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));

        place(EquipmentSlot.HAND); // an "infinite blocks" plugin puts the block straight back
        tick();

        assertStack(Material.COBBLESTONE, 1, inventory.getItem(0));
        assertStack(Material.COBBLESTONE, 64, inventory.getItem(20));
    }

    @Test
    void x10_bothHandsRunOutInTheSameTick() {
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItemInOffHand(new ItemStack(Material.BREAD, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));
        inventory.setItem(21, new ItemStack(Material.BREAD, 5));

        place(EquipmentSlot.HAND);
        consume(EquipmentSlot.OFF_HAND);
        inventory.setItem(0, null);
        inventory.setItemInOffHand(null);
        Map<Material, Integer> before = totals();
        tick();

        assertStack(Material.COBBLESTONE, 64, inventory.getItem(0));
        assertStack(Material.BREAD, 5, inventory.getItemInOffHand());
        assertEquals(before, totals());
    }

    @Test
    void x10_offhandNeverEmptiesTheMainHand() {
        inventory.setItem(0, new ItemStack(Material.TORCH, 1));
        inventory.setItemInOffHand(new ItemStack(Material.TORCH, 1));
        inventory.setItem(20, new ItemStack(Material.TORCH, 10));

        place(EquipmentSlot.HAND);
        place(EquipmentSlot.OFF_HAND);
        inventory.setItem(0, null);
        inventory.setItemInOffHand(null);
        tick();

        assertStack(Material.TORCH, 10, inventory.getItem(0));
        assertEmpty(inventory.getItemInOffHand());
    }
}

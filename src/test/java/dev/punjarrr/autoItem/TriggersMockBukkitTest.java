package dev.punjarrr.autoItem;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.destroystokyo.paper.event.player.PlayerReadyArrowEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Snowball;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
 * SPEC §8 K6 for the milestone 2 triggers: each listener records the right slot, and the task refills it
 * (or not). As in {@link RefillMockBukkitTest}, the vanilla effect of each action is applied by hand.
 */
class TriggersMockBukkitTest {

    private ServerMock server;
    private AutoItem plugin;
    private PlayerMock player;
    private PlayerInventory inventory;
    private Block block;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = AutoItemForTests.load();
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        inventory = player.getInventory();
        inventory.clear();
        block = player.getWorld().getBlockAt(0, 70, 0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void call(Event event) {
        server.getPluginManager().callEvent(event);
    }

    private void tick() {
        server.getScheduler().performOneTick();
    }

    private Map<Material, Integer> totals() {
        Map<Material, Integer> totals = new HashMap<>();
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && !stack.isEmpty()) {
                totals.merge(stack.getType(), stack.getAmount(), Integer::sum);
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

    // ---- buckets ----

    @Test
    void lastEmptyBucketFilledGetsAnotherEmptyBucketAndTheFilledOneMovesOut() {
        inventory.setItem(0, new ItemStack(Material.BUCKET, 1));
        inventory.setItem(10, new ItemStack(Material.BUCKET, 5));

        call(new PlayerBucketFillEvent(player, block, block, BlockFace.UP, Material.BUCKET,
                new ItemStack(Material.WATER_BUCKET), EquipmentSlot.HAND));
        inventory.setItem(0, new ItemStack(Material.WATER_BUCKET)); // vanilla
        Map<Material, Integer> before = totals();
        tick();

        assertStack(Material.BUCKET, 5, inventory.getItem(0));
        assertEmpty(inventory.getItem(10));
        assertStack(Material.WATER_BUCKET, 1, inventory.getItem(1)); // first free slot
        assertEquals(before, totals());
    }

    @Test
    void lastWaterBucketEmptiedRefillsAndTheBucketMovesOut() {
        inventory.setItem(0, new ItemStack(Material.WATER_BUCKET, 1));
        inventory.setItem(12, new ItemStack(Material.WATER_BUCKET, 1));

        call(new PlayerBucketEmptyEvent(player, block, block, BlockFace.UP, Material.WATER_BUCKET,
                new ItemStack(Material.BUCKET), EquipmentSlot.HAND));
        inventory.setItem(0, new ItemStack(Material.BUCKET));
        Map<Material, Integer> before = totals();
        tick();

        assertStack(Material.WATER_BUCKET, 1, inventory.getItem(0));
        assertEmpty(inventory.getItem(12));
        assertStack(Material.BUCKET, 1, inventory.getItem(1)); // first free slot
        assertEquals(before, totals());
    }

    // ---- throwables ----

    @Test
    void lastSnowballInTheOffhandRefillsTheOffhandOnly() {
        inventory.setItem(0, new ItemStack(Material.SNOWBALL, 1));
        inventory.setItemInOffHand(new ItemStack(Material.SNOWBALL, 1));
        inventory.setItem(20, new ItemStack(Material.SNOWBALL, 16));
        Snowball snowball = player.getWorld().spawn(player.getLocation(), Snowball.class);

        // The event doesn't say which hand threw: both hands are recorded, only the empty one refills.
        call(new PlayerLaunchProjectileEvent(player, new ItemStack(Material.SNOWBALL), snowball));
        inventory.setItemInOffHand(null);
        tick();

        assertStack(Material.SNOWBALL, 1, inventory.getItem(0));
        assertStack(Material.SNOWBALL, 16, inventory.getItemInOffHand());
        assertEmpty(inventory.getItem(20));
    }

    @Test
    void throwThatDoesNotConsumeRecordsNothing() {
        inventory.setItem(0, new ItemStack(Material.ENDER_PEARL, 1));
        Snowball snowball = player.getWorld().spawn(player.getLocation(), Snowball.class);
        PlayerLaunchProjectileEvent event = new PlayerLaunchProjectileEvent(player,
                new ItemStack(Material.ENDER_PEARL), snowball);
        event.setShouldConsume(false);
        call(event);
        assertEquals(0, plugin.recorder().pendingPlayers());
    }

    // ---- right-click ----

    @Test
    void rightClickAirIsRecordedAlthoughPaperReportsItCancelled() {
        inventory.setItem(0, new ItemStack(Material.FIREWORK_ROCKET, 1));
        inventory.setItem(30, new ItemStack(Material.FIREWORK_ROCKET, 7));
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                inventory.getItem(0), null, BlockFace.SELF, EquipmentSlot.HAND);
        assertTrue(event.isCancelled(), "precondition: right-click-air starts cancelled");

        call(event);
        inventory.setItem(0, null);
        tick();

        assertStack(Material.FIREWORK_ROCKET, 7, inventory.getItem(0));
    }

    @Test
    void lastSpawnEggUsedOnABlockRefills() {
        inventory.setItem(0, new ItemStack(Material.COW_SPAWN_EGG, 1));
        inventory.setItem(30, new ItemStack(Material.COW_SPAWN_EGG, 2));

        call(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, inventory.getItem(0), block, BlockFace.UP,
                EquipmentSlot.HAND));
        inventory.setItem(0, null);
        tick();

        assertStack(Material.COW_SPAWN_EGG, 2, inventory.getItem(0));
    }

    @Test
    void deniedItemUseRecordsNothing() {
        inventory.setItem(0, new ItemStack(Material.BONE_MEAL, 1));
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, inventory.getItem(0),
                block, BlockFace.UP, EquipmentSlot.HAND);
        event.setUseItemInHand(Event.Result.DENY);
        call(event);
        assertEquals(0, plugin.recorder().pendingPlayers());
    }

    @Test
    void leftClickRecordsNothing() {
        inventory.setItem(0, new ItemStack(Material.BONE_MEAL, 1));
        call(new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, inventory.getItem(0), block, BlockFace.UP,
                EquipmentSlot.HAND));
        assertEquals(0, plugin.recorder().pendingPlayers());
    }

    @Test
    void armorEquippedByRightClickIsNeverRefilled() {
        inventory.setItem(0, new ItemStack(Material.IRON_HELMET, 1));
        inventory.setItem(30, new ItemStack(Material.IRON_HELMET, 1));
        call(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, inventory.getItem(0), null, BlockFace.SELF,
                EquipmentSlot.HAND));
        assertEquals(0, plugin.recorder().pendingPlayers());
    }

    // ---- bows ----

    @Test
    void lastArrowInTheOffhandRefillsAfterABowShot() {
        ItemStack bow = new ItemStack(Material.BOW);
        inventory.setItem(0, bow);
        inventory.setItemInOffHand(new ItemStack(Material.ARROW, 1));
        inventory.setItem(20, new ItemStack(Material.ARROW, 64));

        call(new PlayerReadyArrowEvent(player, bow, new ItemStack(Material.ARROW)));
        inventory.setItemInOffHand(null);
        tick();

        assertStack(Material.ARROW, 64, inventory.getItemInOffHand());
        assertStack(Material.BOW, 1, inventory.getItem(0));
    }

    @Test
    void arrowsThatAreNotTheLastOneAreNotRecorded() {
        ItemStack bow = new ItemStack(Material.BOW);
        inventory.setItem(0, bow);
        inventory.setItemInOffHand(new ItemStack(Material.ARROW, 2));
        call(new PlayerReadyArrowEvent(player, bow, new ItemStack(Material.ARROW)));
        assertEquals(0, plugin.recorder().pendingPlayers());
    }

    @Test
    void infinityBowLeavesTheArrowSoNothingMoves() {
        ItemStack bow = new ItemStack(Material.BOW);
        inventory.setItem(0, bow);
        inventory.setItemInOffHand(new ItemStack(Material.ARROW, 1));
        inventory.setItem(20, new ItemStack(Material.ARROW, 64));
        call(new PlayerReadyArrowEvent(player, bow, new ItemStack(Material.ARROW)));
        tick(); // infinity: the arrow stays in the offhand

        assertStack(Material.ARROW, 1, inventory.getItemInOffHand());
        assertStack(Material.ARROW, 64, inventory.getItem(20));
    }

    @Test
    void lastRocketInTheOffhandRefillsWhenACrossbowLoads() {
        ItemStack crossbow = new ItemStack(Material.CROSSBOW);
        inventory.setItem(0, crossbow);
        inventory.setItemInOffHand(new ItemStack(Material.FIREWORK_ROCKET, 1));
        inventory.setItem(20, new ItemStack(Material.FIREWORK_ROCKET, 3));

        // PlayerReadyArrowEvent also fires when a crossbow loads, naming the exact ammo.
        call(new PlayerReadyArrowEvent(player, crossbow, new ItemStack(Material.FIREWORK_ROCKET)));
        inventory.setItemInOffHand(null);
        tick();

        assertStack(Material.FIREWORK_ROCKET, 3, inventory.getItemInOffHand());
    }

    @Test
    void crossbowLoadingFromStorageNeverRecordsAnUnrelatedOffhandItem() {
        ItemStack crossbow = new ItemStack(Material.CROSSBOW);
        inventory.setItem(0, crossbow);
        inventory.setItemInOffHand(new ItemStack(Material.WATER_BUCKET, 1));
        inventory.setItem(20, new ItemStack(Material.ARROW, 10));
        call(new PlayerReadyArrowEvent(player, crossbow, new ItemStack(Material.ARROW)));
        assertEquals(0, plugin.recorder().pendingPlayers());
    }

    // ---- discards: something other than a trigger emptied the slot in the same tick ----
    // (Inventory clicks and drags can't be built in MockBukkit; the bot covers a real click, X4.)

    /** Right-click the air with the last item in a hand: recorded, but nothing is used up. */
    private void rightClickAir(EquipmentSlot hand) {
        ItemStack item = hand == EquipmentSlot.HAND ? inventory.getItemInMainHand() : inventory.getItemInOffHand();
        call(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, item, null, BlockFace.SELF, hand));
    }

    @Test
    void totemPopAfterARightClickNeverRefillsTheTotem() {
        inventory.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
        inventory.setItem(20, new ItemStack(Material.TOTEM_OF_UNDYING, 3));
        rightClickAir(EquipmentSlot.OFF_HAND);

        call(new EntityResurrectEvent(player, EquipmentSlot.OFF_HAND));
        inventory.setItemInOffHand(null); // vanilla uses the totem
        tick();

        assertEmpty(inventory.getItemInOffHand());
        assertStack(Material.TOTEM_OF_UNDYING, 3, inventory.getItem(20));
    }

    @Test
    void cancelledResurrectDiscardsNothing() {
        // No totem: vanilla fires the event already cancelled. The pending check must stay.
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));
        call(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, inventory.getItem(0), block, BlockFace.UP,
                EquipmentSlot.HAND));
        EntityResurrectEvent resurrect = new EntityResurrectEvent(player, EquipmentSlot.HAND);
        resurrect.setCancelled(true);
        call(resurrect);
        inventory.setItem(0, null);
        tick();

        assertStack(Material.COBBLESTONE, 64, inventory.getItem(0));
    }

    @Test
    void dropAfterARightClickNeverRefills() {
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 1));
        inventory.setItem(20, new ItemStack(Material.DIAMOND, 5));
        rightClickAir(EquipmentSlot.HAND);

        Item dropped = player.getWorld().dropItem(player.getLocation(), inventory.getItem(0).clone());
        inventory.setItem(0, null);
        call(new PlayerDropItemEvent(player, dropped));
        tick();

        assertEmpty(inventory.getItem(0));
        assertStack(Material.DIAMOND, 5, inventory.getItem(20));
    }

    @Test
    void handSwapAfterARightClickNeverRefills() {
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 1));
        inventory.setItem(20, new ItemStack(Material.DIAMOND, 5));
        rightClickAir(EquipmentSlot.HAND);

        call(new PlayerSwapHandItemsEvent(player, new ItemStack(Material.AIR), inventory.getItem(0).clone()));
        inventory.setItemInOffHand(inventory.getItem(0));
        inventory.setItem(0, null);
        tick();

        assertEmpty(inventory.getItem(0));
        assertStack(Material.DIAMOND, 1, inventory.getItemInOffHand());
        assertStack(Material.DIAMOND, 5, inventory.getItem(20));
    }

    @Test
    void aRealUseAfterADiscardStillRefills() {
        // Q on another item, then placing the last block in the same tick: the place is recorded after the
        // discard, so it still refills.
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));
        Item dropped = player.getWorld().dropItem(player.getLocation(), new ItemStack(Material.DIRT));
        call(new PlayerDropItemEvent(player, dropped));
        call(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, inventory.getItem(0), block, BlockFace.UP,
                EquipmentSlot.HAND));
        inventory.setItem(0, null);
        tick();

        assertStack(Material.COBBLESTONE, 64, inventory.getItem(0));
    }

    @Test
    void bowInTheOffhandRefillsTheMainHandArrow() {
        ItemStack bow = new ItemStack(Material.BOW);
        inventory.setItemInOffHand(bow);
        inventory.setHeldItemSlot(4);
        inventory.setItem(4, new ItemStack(Material.ARROW, 1));
        inventory.setItem(20, new ItemStack(Material.ARROW, 10));

        call(new PlayerReadyArrowEvent(player, bow, new ItemStack(Material.ARROW)));
        inventory.setItem(4, null);
        tick();

        assertStack(Material.ARROW, 10, inventory.getItem(4));
    }
}

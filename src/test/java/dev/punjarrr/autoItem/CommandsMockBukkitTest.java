package dev.punjarrr.autoItem;

import dev.punjarrr.autoItem.refill.Trigger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC §8 K7 and the milestone 3 parts of the matrix: /autoitem toggle and reload, permissions, disabled
 * worlds, tool breaking and feedback, on a MockBukkit server.
 */
class CommandsMockBukkitTest {

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

    private static String plain(Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private String nextPlayerMessage() {
        return plain(player.nextComponentMessage());
    }

    private String nextConsoleMessage() {
        return plain(server.getConsoleSender().nextComponentMessage());
    }

    /**
     * Writes the bundled config.yml with text replaced ({@code from, to, from, to...}), then reloads it from
     * the console and returns the reply.
     */
    private String reloadWith(String... replacements) throws IOException {
        String yaml;
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (int i = 0; i < replacements.length; i += 2) {
            String from = replacements[i];
            assertTrue(yaml.contains(from), "config.yml contains " + from);
            yaml = yaml.replace(from, replacements[i + 1]);
        }
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), yaml);
        server.dispatchCommand(server.getConsoleSender(), "autoitem reload");
        return nextConsoleMessage();
    }

    /** Places the last cobblestone in slot 0 with 64 more in storage; returns slot 0 after the next tick. */
    private ItemStack placeLastBlock() {
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 1));
        inventory.setItem(20, new ItemStack(Material.COBBLESTONE, 64));
        Block block = player.getWorld().getBlockAt(0, 70, 0);
        server.getPluginManager().callEvent(new BlockPlaceEvent(block, block.getState(),
                block.getRelative(BlockFace.DOWN), inventory.getItem(0), player, true, EquipmentSlot.HAND));
        inventory.setItem(0, null);
        server.getScheduler().performOneTick();
        return inventory.getItem(0);
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    // ---- /autoitem toggle ----

    @Test
    void toggleTurnsRefillsOffAndOnAndPersistsInThePdc() {
        player.performCommand("autoitem toggle");
        assertEquals("[AutoItem] Auto refill disabled.", nextPlayerMessage());
        assertEquals((byte) 0, player.getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "enabled"), PersistentDataType.BYTE));
        assertTrue(isEmpty(placeLastBlock()));

        player.performCommand("autoitem toggle");
        assertEquals("[AutoItem] Auto refill enabled.", nextPlayerMessage());
        assertEquals(64, placeLastBlock().getAmount());
    }

    @Test
    void toggleNeedsItsPermission() {
        player.addAttachment(plugin, "autoitem.toggle", false);
        player.performCommand("autoitem toggle");
        assertEquals("[AutoItem] You don't have permission.", nextPlayerMessage());
        assertNull(player.getPersistentDataContainer().get(new NamespacedKey(plugin, "enabled"),
                PersistentDataType.BYTE));
    }

    @Test
    void toggleFromTheConsoleIsRefused() {
        server.dispatchCommand(server.getConsoleSender(), "autoitem toggle");
        assertEquals("[AutoItem] Only players can use this.", nextConsoleMessage());
    }

    @Test
    void noOrUnknownArgumentShowsUsage() {
        player.performCommand("autoitem");
        assertEquals("[AutoItem] /autoitem <toggle|reload>", nextPlayerMessage());
        player.performCommand("autoitem fly");
        assertEquals("[AutoItem] /autoitem <toggle|reload>", nextPlayerMessage());
    }

    @Test
    void withoutUsePermissionNothingRefills() {
        player.addAttachment(plugin, "autoitem.use", false);
        assertTrue(isEmpty(placeLastBlock()));
    }

    // ---- /autoitem reload ----

    @Test
    void reloadAppliesTheNewConfig() throws IOException {
        assertEquals("[AutoItem] Config reloaded.", reloadWith("block-place: true", "block-place: false"));
        assertFalse(plugin.configService().settings().triggers().contains(Trigger.BLOCK_PLACE));
        assertTrue(isEmpty(placeLastBlock()));
    }

    @Test
    void reloadReportsWarnings() throws IOException {
        assertEquals("[AutoItem] Config reloaded with 1 warning(s). See console.",
                reloadWith("block-place: true", "block-place: maybe"));
        assertTrue(plugin.configService().settings().triggers().contains(Trigger.BLOCK_PLACE));
    }

    @Test
    void brokenYamlKeepsThePreviousConfig() throws IOException {
        reloadWith("block-place: true", "block-place: false");
        assertEquals("[AutoItem] Config has errors, kept the previous one. See console.",
                reloadWith("enabled: true", "enabled: [true"));
        assertFalse(plugin.configService().settings().triggers().contains(Trigger.BLOCK_PLACE));
    }

    @Test
    void reloadNeedsItsPermission() {
        player.performCommand("autoitem reload");
        assertEquals("[AutoItem] You don't have permission.", nextPlayerMessage());
    }

    @Test
    void tabCompletionOnlyListsAllowedSubcommands() {
        assertEquals(List.of("toggle"), plugin.getCommand("autoitem").tabComplete(player, "autoitem",
                new String[]{""}));
        player.setOp(true);
        assertEquals(List.of("toggle", "reload"), plugin.getCommand("autoitem").tabComplete(player, "autoitem",
                new String[]{""}));
        assertEquals(List.of("reload"), plugin.getCommand("autoitem").tabComplete(player, "autoitem",
                new String[]{"re"}));
    }

    // ---- worlds ----

    @Test
    void disabledWorldNeverRefills() throws IOException {
        reloadWith("disabled-worlds: []", "disabled-worlds: [" + player.getWorld().getName() + "]");
        assertTrue(isEmpty(placeLastBlock()));
    }

    // ---- tool breaking ----

    private static ItemStack pickaxe(int damage, boolean efficiency, String name) {
        ItemStack stack = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = stack.getItemMeta();
        ((Damageable) meta).setDamage(damage);
        if (efficiency) {
            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        }
        if (name != null) {
            meta.displayName(Component.text(name));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** As vanilla does: damage reaches the maximum (1561), the event fires, then the tool is removed. */
    private void breakToolInSlot(int slot) {
        ItemStack tool = inventory.getItem(slot);
        ItemMeta meta = tool.getItemMeta();
        ((Damageable) meta).setDamage(1561);
        tool.setItemMeta(meta);
        inventory.setItem(slot, tool);
        server.getPluginManager().callEvent(new PlayerItemBreakEvent(player, inventory.getItem(slot)));
        inventory.setItem(slot, null);
        server.getScheduler().performOneTick();
    }

    private void breakToolInSlot0() {
        breakToolInSlot(0);
    }

    @Test
    void toolBreakIsOffByDefault() {
        inventory.setItem(0, pickaxe(1560, false, null));
        inventory.setItem(20, pickaxe(0, false, null));
        breakToolInSlot0();
        assertTrue(isEmpty(inventory.getItem(0)));
    }

    @Test
    void brokenToolIsReplacedPreferringIdenticalEnchantments() throws IOException {
        reloadWith("tool-break: false", "tool-break: true");
        inventory.setItem(0, pickaxe(1560, true, null));
        inventory.setItem(20, pickaxe(0, false, null));
        inventory.setItem(5, pickaxe(800, true, null));
        breakToolInSlot0();

        ItemStack replacement = inventory.getItem(0);
        assertEquals(Material.DIAMOND_PICKAXE, replacement.getType());
        assertEquals(5, replacement.getEnchantmentLevel(Enchantment.EFFICIENCY));
        assertTrue(isEmpty(inventory.getItem(5)));
        assertEquals(Material.DIAMOND_PICKAXE, inventory.getItem(20).getType());
    }

    @Test
    void anIdenticalWorkingToolInTheOtherHandIsUntouched() throws IOException {
        reloadWith("tool-break: false", "tool-break: true");
        inventory.setItem(0, pickaxe(1500, false, null));
        inventory.setItemInOffHand(pickaxe(1500, false, null));
        inventory.setItem(20, pickaxe(0, false, null));
        breakToolInSlot0();

        assertEquals(0, ((Damageable) inventory.getItem(0).getItemMeta()).getDamage(), "refilled from slot 20");
        assertEquals(1500, ((Damageable) inventory.getItemInOffHand().getItemMeta()).getDamage());
    }

    @Test
    void aToolBreakingInTheOffhandIsReplacedThere() throws IOException {
        reloadWith("tool-break: false", "tool-break: true");
        inventory.setItemInOffHand(pickaxe(1500, false, null));
        inventory.setItem(20, pickaxe(0, false, null));
        breakToolInSlot(40);

        assertEquals(Material.DIAMOND_PICKAXE, inventory.getItemInOffHand().getType());
        assertTrue(isEmpty(inventory.getItem(20)));
    }

    @Test
    void aToolWithAnotherModelOrLoreNeverStandsIn() throws IOException {
        // Custom items from other plugins often differ only in model or lore.
        reloadWith("tool-break: false", "tool-break: true");
        inventory.setItem(0, pickaxe(1500, false, null));
        ItemStack custom = pickaxe(0, false, null);
        ItemMeta meta = custom.getItemMeta();
        meta.setCustomModelData(7);
        meta.lore(List.of(Component.text("Ruby Pickaxe")));
        custom.setItemMeta(meta);
        inventory.setItem(20, custom);
        breakToolInSlot0();

        assertTrue(isEmpty(inventory.getItem(0)));
        assertEquals(custom, inventory.getItem(20));
    }

    @Test
    void namedToolNeverStandsInForAnUnnamedOne() throws IOException {
        reloadWith("tool-break: false", "tool-break: true");
        inventory.setItem(0, pickaxe(1560, false, null));
        inventory.setItem(20, pickaxe(0, false, "Excavator"));
        breakToolInSlot0();
        assertTrue(isEmpty(inventory.getItem(0)));
        assertEquals(Material.DIAMOND_PICKAXE, inventory.getItem(20).getType());
    }

    // ---- feedback ----

    @Test
    void refillPlaysTheQuietSoundAndNoActionBarByDefault() {
        placeLastBlock();
        assertEquals(1, player.getHeardSounds().size());
        assertEquals("minecraft:entity.item.pickup", player.getHeardSounds().getFirst().getSound());
        assertEquals(0.3f, player.getHeardSounds().getFirst().getVolume());
        assertNull(player.nextActionBar());
    }

    @Test
    void actionBarAndNoSoundWhenConfigured() throws IOException {
        assertEquals("[AutoItem] Config reloaded.", reloadWith(
                "enabled: true\n    # Any sound key", "enabled: false\n    # Any sound key",
                "action-bar: false", "action-bar: true"));
        placeLastBlock();
        assertTrue(player.getHeardSounds().isEmpty());
        String bar = plain(player.nextActionBar());
        assertTrue(bar != null && bar.startsWith("Refilled "), () -> "action bar: " + bar);
    }

    @Test
    void noFeedbackWhenNothingWasRefilled() {
        player.performCommand("autoitem toggle");
        nextPlayerMessage();
        placeLastBlock();
        assertTrue(player.getHeardSounds().isEmpty());
    }
}

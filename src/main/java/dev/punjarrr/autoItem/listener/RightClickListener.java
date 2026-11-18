package dev.punjarrr.autoItem.listener;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import dev.punjarrr.autoItem.refill.CheckRecorder;
import dev.punjarrr.autoItem.refill.Trigger;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Trigger {@code right-click}, the catch-all for items used by right-clicking: spawn eggs, boats, minecarts,
 * bone meal, fireworks, end crystals, ender eyes, item frames, paintings, armor stands, feeding and breeding,
 * name tags, leads, cauldrons, filling bottles and bowls. When a more specific trigger records the same use
 * (placing a block, a bucket, a throw) that one wins (see {@code Check}). Items that right-click equips
 * (armor, elytra, heads) are never recorded. Observer only.
 */
public final class RightClickListener implements Listener {

    private final CheckRecorder recorder;
    private final Predicate<ItemStack> equipsOnRightClick;

    /** @param equipsOnRightClick true for items that right-clicking equips instead of using */
    public RightClickListener(CheckRecorder recorder, Predicate<ItemStack> equipsOnRightClick) {
        this.recorder = recorder;
        this.equipsOnRightClick = equipsOnRightClick;
    }

    /** Armor, elytra, heads, carved pumpkins: anything whose equipment slot isn't a hand. */
    public static boolean equipsOnRightClick(ItemStack item) {
        return !item.getType().getEquipmentSlot().isHand();
    }

    /** Paper reports right-click-air as cancelled from the start, so use useItemInHand() instead. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.useItemInHand() == Event.Result.DENY || !event.getAction().isRightClick()) {
            return;
        }
        record(event.getPlayer(), event.getHand(), event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        record(player, event.getHand(), player.getInventory().getItem(event.getHand()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            record(player, event.getHand(), player.getInventory().getItem(event.getHand()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && event.getHand() != null) {
            record(player, event.getHand(), event.getItemStack());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        record(event.getPlayer(), event.getHand(), event.getPlayerItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onElytraBoost(PlayerElytraBoostEvent event) {
        if (event.shouldConsume()) {
            record(event.getPlayer(), event.getHand(), event.getItemStack());
        }
    }

    private void record(Player player, @Nullable EquipmentSlot hand, @Nullable ItemStack item) {
        if (item == null || item.isEmpty() || equipsOnRightClick.test(item)) {
            return;
        }
        recorder.record(player, hand, item, Trigger.RIGHT_CLICK);
    }
}

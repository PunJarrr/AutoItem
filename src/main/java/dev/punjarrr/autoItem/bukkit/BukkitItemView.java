package dev.punjarrr.autoItem.bukkit;

import dev.punjarrr.autoItem.refill.ItemView;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * {@link ItemView} over a private clone of an {@link ItemStack}. The clone is taken in {@link #of}, so a
 * view never changes when the slot it was read from is written (live mirrors turn to air when cleared).
 */
public final class BukkitItemView implements ItemView {

    private final ItemStack stack;
    private @Nullable ItemMeta meta;

    private BukkitItemView(ItemStack stack) {
        this.stack = stack;
    }

    /** A view over a clone of {@code live}, or null if it's empty. */
    public static @Nullable BukkitItemView of(@Nullable ItemStack live) {
        if (live == null || live.isEmpty()) {
            return null;
        }
        return new BukkitItemView(live.clone());
    }

    /** The view's own clone. Never put it into an inventory; clone it again first. */
    public ItemStack stack() {
        return stack;
    }

    private ItemMeta meta() {
        if (meta == null) {
            meta = stack.getItemMeta();
        }
        return meta;
    }

    @Override
    public String material() {
        return stack.getType().getKey().toString();
    }

    @Override
    public int amount() {
        return stack.getAmount();
    }

    @Override
    public int maxDurability() {
        if (meta() instanceof Damageable damageable && damageable.hasMaxDamage()) {
            return damageable.getMaxDamage();
        }
        return stack.getType().getMaxDurability();
    }

    @Override
    public int damage() {
        return meta() instanceof Damageable damageable ? damageable.getDamage() : 0;
    }

    @Override
    public boolean isSimilar(ItemView other) {
        return other instanceof BukkitItemView view && stack.isSimilar(view.stack);
    }

    @Override
    public boolean hasCustomName() {
        return meta() != null && meta().hasDisplayName();
    }

    @Override
    public boolean sameCustomName(ItemView other) {
        return other instanceof BukkitItemView view && Objects.equals(customName(), view.customName());
    }

    private @Nullable Component customName() {
        return hasCustomName() ? meta().displayName() : null;
    }

    @Override
    public boolean sameEnchantments(ItemView other) {
        return other instanceof BukkitItemView view && stack.getEnchantments().equals(view.stack.getEnchantments());
    }

    @Override
    public boolean samePluginData(ItemView other) {
        if (!(other instanceof BukkitItemView view)) {
            return false;
        }
        if (meta() == null || view.meta() == null) {
            return meta() == null && view.meta() == null;
        }
        return meta().getPersistentDataContainer().equals(view.meta().getPersistentDataContainer());
    }

    @Override
    public String toString() {
        return "BukkitItemView[" + stack + "]";
    }
}

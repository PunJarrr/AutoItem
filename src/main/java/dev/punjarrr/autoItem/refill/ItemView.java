package dev.punjarrr.autoItem.refill;

/**
 * What the planner needs to know about one item stack. At runtime this wraps a <em>clone</em> of an
 * {@code ItemStack}; tests use a fake, so the planner never needs a server.
 */
public interface ItemView {

    /** The material's namespaced key, e.g. {@code minecraft:bowl}. */
    String material();

    int amount();

    /** Maximum durability; 0 if the item can't be damaged. */
    int maxDurability();

    /** Damage taken so far; 0 for undamageable items. */
    int damage();

    /** Same item apart from the amount ({@code ItemStack#isSimilar}). */
    boolean isSimilar(ItemView other);

    boolean hasCustomName();

    /** Both have no custom name, or both have the same one. */
    boolean sameCustomName(ItemView other);

    boolean sameEnchantments(ItemView other);

    /**
     * The same item once wear is ignored: identical apart from amount, damage, enchantments, repair cost and
     * custom name. Everything else counts (item name, lore, model, attributes, unbreakable, plugin data and
     * any other custom data), so a custom tool from another plugin never stands in for a plain one, or the
     * other way round. Used for tool breaks, where isSimilar can't match because durability differs.
     */
    boolean sameIgnoringWear(ItemView other);
}

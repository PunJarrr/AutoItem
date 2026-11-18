package dev.punjarrr.autoItem.refill;

import java.util.Map;
import java.util.Objects;

/**
 * Test double for {@link ItemView}. {@code meta} stands for everything isSimilar compares besides material,
 * damage, name, enchantments and plugin data (potion type, tipped arrow effect, lore...).
 */
record FakeItem(String material, int amount, String meta, int maxDurability, int damage, String customName,
                Map<String, Integer> enchantments, String pluginData) implements ItemView {

    static FakeItem of(String material, int amount) {
        return new FakeItem(key(material), amount, "", 0, 0, null, Map.of(), "");
    }

    static FakeItem tool(String material, int maxDurability, int damage) {
        return new FakeItem(key(material), 1, "", maxDurability, damage, null, Map.of(), "");
    }

    static String key(String material) {
        return material.contains(":") ? material : "minecraft:" + material;
    }

    FakeItem amount(int newAmount) {
        return new FakeItem(material, newAmount, meta, maxDurability, damage, customName, enchantments, pluginData);
    }

    FakeItem meta(String newMeta) {
        return new FakeItem(material, amount, newMeta, maxDurability, damage, customName, enchantments, pluginData);
    }

    FakeItem named(String name) {
        return new FakeItem(material, amount, meta, maxDurability, damage, name, enchantments, pluginData);
    }

    FakeItem enchanted(Map<String, Integer> enchants) {
        return new FakeItem(material, amount, meta, maxDurability, damage, customName, enchants, pluginData);
    }

    FakeItem pluginData(String data) {
        return new FakeItem(material, amount, meta, maxDurability, damage, customName, enchantments, data);
    }

    FakeItem damaged(int newDamage) {
        return new FakeItem(material, amount, meta, maxDurability, newDamage, customName, enchantments, pluginData);
    }

    @Override
    public boolean isSimilar(ItemView other) {
        return other instanceof FakeItem o && material.equals(o.material) && meta.equals(o.meta)
                && damage == o.damage && sameCustomName(o) && sameEnchantments(o) && pluginData.equals(o.pluginData);
    }

    @Override
    public boolean hasCustomName() {
        return customName != null;
    }

    @Override
    public boolean sameCustomName(ItemView other) {
        return other instanceof FakeItem o && Objects.equals(customName, o.customName);
    }

    @Override
    public boolean sameEnchantments(ItemView other) {
        return other instanceof FakeItem o && enchantments.equals(o.enchantments);
    }

    /** Ignores amount, damage, enchantments and custom name, like the Bukkit view. */
    @Override
    public boolean sameIgnoringWear(ItemView other) {
        return other instanceof FakeItem o && material.equals(o.material) && meta.equals(o.meta)
                && pluginData.equals(o.pluginData);
    }
}

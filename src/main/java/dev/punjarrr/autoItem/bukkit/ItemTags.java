package dev.punjarrr.autoItem.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** Looks up item tags on the running server. Unknown tags (e.g. from newer versions) return null. */
public final class ItemTags {

    private ItemTags() {
    }

    public static @Nullable Set<String> materialKeys(String tagKey) {
        NamespacedKey key = NamespacedKey.fromString(tagKey);
        if (key == null) {
            return null;
        }
        Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
        if (tag == null) {
            return null;
        }
        Set<String> keys = new HashSet<>();
        for (Material material : tag.getValues()) {
            keys.add(material.getKey().toString());
        }
        return keys;
    }
}

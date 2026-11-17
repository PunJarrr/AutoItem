package dev.punjarrr.autoItem.toggle;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The per-player switch, stored as {@code autoitem:enabled} (BYTE) on the player's PersistentDataContainer.
 * Missing means on. Call only from the player's owning thread.
 */
public final class ToggleService {

    private final NamespacedKey key;

    public ToggleService(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "enabled");
    }

    public boolean isEnabled(Player player) {
        Byte stored = player.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return stored == null || stored != 0;
    }

    /** Flips the switch and returns the new state. */
    public boolean toggle(Player player) {
        boolean enabled = !isEnabled(player);
        player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
        return enabled;
    }
}

package dev.punjarrr.autoItem;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Predicate;

/**
 * The plugin with the one check MockBukkit can't run ({@code Material#getEquipmentSlot()}) replaced by a
 * name-based approximation. The real check is covered by the bot tests (tools/bot).
 */
public class AutoItemForTests extends AutoItem {

    /**
     * Loads this class with the real plugin.yml's permissions. MockBukkit only registers them when it loads
     * the plugin.yml main class itself; without them {@code autoitem.use} would fall back to op-only and every
     * refill would be gated off.
     */
    static AutoItemForTests load() {
        try (InputStream in = AutoItemForTests.class.getResourceAsStream("/plugin.yml")) {
            PluginDescriptionFile description = new PluginDescriptionFile(in);
            PluginManager pluginManager = Bukkit.getPluginManager();
            for (Permission permission : description.getPermissions()) {
                pluginManager.addPermission(permission);
            }
            AutoItemForTests plugin = MockBukkit.load(AutoItemForTests.class);
            if (pluginManager.getPermission("autoitem.use") == null) {
                throw new AssertionError("plugin.yml permissions were not registered");
            }
            return plugin;
        } catch (IOException | InvalidDescriptionException e) {
            throw new AssertionError("can't read plugin.yml", e);
        }
    }

    @Override
    protected Predicate<ItemStack> equipsOnRightClick() {
        return item -> {
            String name = item.getType().name();
            return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
                    || name.endsWith("_BOOTS") || name.equals("ELYTRA");
        };
    }
}

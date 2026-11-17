package dev.punjarrr.autoItem.config;

import dev.punjarrr.autoItem.refill.Remainders;
import dev.punjarrr.autoItem.refill.Trigger;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of config.yml. Material values are namespaced keys ({@code minecraft:bowl}), so the
 * record is plain data. Item tags stay as tag keys until {@link SettingsFactory} resolves them.
 *
 * @param ignoredMaterials material keys listed in {@code ignored-items}
 * @param ignoredTags      tag keys listed in {@code ignored-items} (without the leading '#')
 * @param remainders       material key → material key or {@link Remainders#NONE}
 */
public record PluginConfig(int configVersion, boolean enabled, Set<Trigger> triggers, boolean hotbarSource,
                           Set<String> ignoredMaterials, Set<String> ignoredTags, Map<String, String> remainders,
                           Set<String> disabledWorlds, Feedback feedback, Messages messages) {

    public static final int CURRENT_CONFIG_VERSION = 1;

    public PluginConfig {
        triggers = Set.copyOf(triggers);
        ignoredMaterials = Set.copyOf(ignoredMaterials);
        ignoredTags = Set.copyOf(ignoredTags);
        remainders = Map.copyOf(remainders);
        disabledWorlds = Set.copyOf(disabledWorlds);
    }

    public record Feedback(boolean soundEnabled, String soundKey, float soundVolume, float soundPitch,
                           boolean actionBar) {
    }

    public record Messages(String prefix, String refilled, String toggledOn, String toggledOff, String reloaded,
                           String reloadedWithWarnings, String reloadFailed, String noPermission,
                           String playerOnly, String usage) {
    }

    public static PluginConfig defaults() {
        Set<Trigger> triggers = EnumSet.allOf(Trigger.class);
        triggers.remove(Trigger.TOOL_BREAK);
        return new PluginConfig(CURRENT_CONFIG_VERSION, true, triggers, true, Set.of(), Set.of(),
                defaultRemainders(), Set.of(),
                new Feedback(true, "minecraft:entity.item.pickup", 0.3f, 1.4f, false),
                new Messages(
                        "<gray>[<gold>AutoItem</gold>]</gray> ",
                        "<gray>Refilled <white><item></white></gray>",
                        "<prefix><green>Auto refill enabled.</green>",
                        "<prefix><yellow>Auto refill disabled.</yellow>",
                        "<prefix><green>Config reloaded.</green>",
                        "<prefix><yellow>Config reloaded with <count> warning(s). See console.</yellow>",
                        "<prefix><red>Config has errors, kept the previous one. See console.</red>",
                        "<prefix><red>You don't have permission.</red>",
                        "<prefix><red>Only players can use this.</red>",
                        "<prefix><gray>/autoitem <toggle|reload></gray>"));
    }

    private static Map<String, String> defaultRemainders() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String stew : new String[]{"mushroom_stew", "rabbit_stew", "beetroot_soup", "suspicious_stew"}) {
            map.put("minecraft:" + stew, "minecraft:bowl");
        }
        map.put("minecraft:potion", "minecraft:glass_bottle");
        map.put("minecraft:honey_bottle", "minecraft:glass_bottle");
        for (String bucket : new String[]{"water", "lava", "milk", "powder_snow", "cod", "salmon", "pufferfish",
                "tropical_fish", "axolotl", "tadpole"}) {
            map.put("minecraft:" + bucket + "_bucket", "minecraft:bucket");
        }
        map.put("minecraft:ominous_bottle", Remainders.NONE);
        return map;
    }
}

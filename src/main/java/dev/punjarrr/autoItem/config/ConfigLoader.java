package dev.punjarrr.autoItem.config;

import dev.punjarrr.autoItem.refill.Remainders;
import dev.punjarrr.autoItem.refill.Trigger;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.DoublePredicate;
import java.util.function.IntPredicate;

/**
 * Turns a parsed config.yml into a {@link PluginConfig} and collects warnings. It never logs and never
 * needs a running server, so it's unit tested directly.
 *
 * <p>A missing key uses its default silently. An invalid value adds a warning and uses the default.
 * Bad entries in lists and maps are skipped with a warning.
 */
public final class ConfigLoader {

    public record LoadResult(PluginConfig config, List<String> warnings) {
        public LoadResult {
            warnings = List.copyOf(warnings);
        }
    }

    /** Placeholders the plugin resolves itself, across all messages. */
    private static final Set<String> PLACEHOLDERS = Set.of("prefix", "item", "count");

    /** Sections whose child keys are user-chosen (material names), so they're not checked. */
    private static final Set<String> FREE_FORM_SECTIONS = Set.of("remainders");

    private static final Set<String> KNOWN_PATHS;

    static {
        Set<String> paths = new LinkedHashSet<>(List.of(
                "config-version", "enabled",
                "triggers",
                "sources", "sources.hotbar",
                "ignored-items", "remainders", "disabled-worlds",
                "feedback", "feedback.sound", "feedback.sound.enabled", "feedback.sound.key",
                "feedback.sound.volume", "feedback.sound.pitch", "feedback.action-bar",
                "messages", "messages.prefix", "messages.refilled", "messages.toggled-on", "messages.toggled-off",
                "messages.reloaded", "messages.reloaded-with-warnings", "messages.reload-failed",
                "messages.no-permission", "messages.player-only", "messages.usage"));
        for (Trigger trigger : Trigger.values()) {
            paths.add("triggers." + trigger.configKey());
        }
        KNOWN_PATHS = Set.copyOf(paths);
    }

    static final float MAX_VOLUME = 10.0f;
    static final float MIN_PITCH = 0.5f;
    static final float MAX_PITCH = 2.0f;

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final TagResolver STANDARD_TAGS = StandardTags.defaults();

    public LoadResult load(ConfigurationSection root) {
        Reader reader = new Reader(root);
        return new LoadResult(reader.read(), reader.warnings);
    }

    /** One load: the source section and the warnings collected so far. */
    private static final class Reader {
        private final ConfigurationSection root;
        private final List<String> warnings = new ArrayList<>();
        private final PluginConfig defaults = PluginConfig.defaults();

        Reader(ConfigurationSection root) {
            this.root = root;
        }

        PluginConfig read() {
            checkUnknownKeys();
            int version = readInt("config-version", PluginConfig.CURRENT_CONFIG_VERSION, v -> v >= 1);
            if (version > PluginConfig.CURRENT_CONFIG_VERSION) {
                warnings.add("'config-version' is " + version + " but this AutoItem version only knows "
                        + PluginConfig.CURRENT_CONFIG_VERSION + ". The file is from a newer plugin version; "
                        + "unknown options are ignored.");
            }
            boolean enabled = readBool("enabled", defaults.enabled());
            Set<Trigger> triggers = EnumSet.noneOf(Trigger.class);
            for (Trigger trigger : Trigger.values()) {
                if (readBool("triggers." + trigger.configKey(), defaults.triggers().contains(trigger))) {
                    triggers.add(trigger);
                }
            }
            boolean hotbar = readBool("sources.hotbar", defaults.hotbarSource());
            Set<String> ignoredMaterials = new LinkedHashSet<>();
            Set<String> ignoredTags = new LinkedHashSet<>();
            readIgnored("ignored-items", ignoredMaterials, ignoredTags);
            Map<String, String> remainders = readRemainders("remainders", defaults.remainders());
            Set<String> worlds = readStringSet("disabled-worlds");
            return new PluginConfig(version, enabled, triggers, hotbar, ignoredMaterials, ignoredTags, remainders,
                    worlds, readFeedback(), readMessages());
        }

        private PluginConfig.Feedback readFeedback() {
            PluginConfig.Feedback d = defaults.feedback();
            return new PluginConfig.Feedback(
                    readBool("feedback.sound.enabled", d.soundEnabled()),
                    readSoundKey("feedback.sound.key", d.soundKey()),
                    (float) readDouble("feedback.sound.volume", d.soundVolume(), v -> v >= 0 && v <= MAX_VOLUME),
                    (float) readDouble("feedback.sound.pitch", d.soundPitch(), v -> v >= MIN_PITCH && v <= MAX_PITCH),
                    readBool("feedback.action-bar", d.actionBar()));
        }

        private PluginConfig.Messages readMessages() {
            PluginConfig.Messages d = defaults.messages();
            return new PluginConfig.Messages(
                    readMiniMessage("messages.prefix", d.prefix(), true),
                    readMiniMessage("messages.refilled", d.refilled(), false),
                    readMiniMessage("messages.toggled-on", d.toggledOn(), false),
                    readMiniMessage("messages.toggled-off", d.toggledOff(), false),
                    readMiniMessage("messages.reloaded", d.reloaded(), false),
                    readMiniMessage("messages.reloaded-with-warnings", d.reloadedWithWarnings(), false),
                    readMiniMessage("messages.reload-failed", d.reloadFailed(), false),
                    readMiniMessage("messages.no-permission", d.noPermission(), false),
                    readMiniMessage("messages.player-only", d.playerOnly(), false),
                    readMiniMessage("messages.usage", d.usage(), false));
        }

        // ---- checks ----

        private void checkUnknownKeys() {
            for (String path : root.getKeys(true)) {
                if (KNOWN_PATHS.contains(path) || isInFreeFormSection(path)) {
                    continue;
                }
                warnings.add("Unknown config key '" + path + "' (ignored). Check the spelling.");
            }
        }

        private static boolean isInFreeFormSection(String path) {
            for (String section : FREE_FORM_SECTIONS) {
                if (path.startsWith(section + ".")) {
                    return true;
                }
            }
            return false;
        }

        // ---- scalar readers ----

        private boolean readBool(String path, boolean def) {
            Object value = root.get(path);
            if (value == null) {
                return def;
            }
            if (value instanceof Boolean b) {
                return b;
            }
            return invalid(path, value, def);
        }

        /** Only YAML integers that fit in an int are accepted; 5.0, "5" and 99999999999 are invalid. */
        private int readInt(String path, int def, IntPredicate valid) {
            Object value = root.get(path);
            if (value == null) {
                return def;
            }
            if (value instanceof Integer i && valid.test(i)) {
                return i;
            }
            return invalid(path, value, def);
        }

        private double readDouble(String path, double def, DoublePredicate valid) {
            Object value = root.get(path);
            if (value == null) {
                return def;
            }
            if ((value instanceof Integer || value instanceof Double) && valid.test(((Number) value).doubleValue())) {
                return ((Number) value).doubleValue();
            }
            return invalid(path, value, (float) def, def);
        }

        private String readSoundKey(String path, String def) {
            Object value = root.get(path);
            if (value == null) {
                return def;
            }
            if (value instanceof String s && !s.isBlank()) {
                try {
                    return Key.key(s.trim().toLowerCase(Locale.ROOT)).asString();
                } catch (InvalidKeyException ignored) {
                    // falls through to the warning
                }
            }
            return invalid(path, value, "\"" + def + "\"", def);
        }

        private String readMiniMessage(String path, String def, boolean allowBlank) {
            Object value = root.get(path);
            if (value == null) {
                return def;
            }
            if (!(value instanceof String s) || (!allowBlank && s.isBlank()) || !checkMiniMessage(path, s)) {
                return invalid(path, value, "\"" + def + "\"", def);
            }
            return s;
        }

        /**
         * MiniMessage is lenient: a typo like {@code <yelow>} renders as literal text instead of failing.
         * Warn about tags nobody resolves, but keep the user's text. Returns false only if parsing throws.
         */
        private boolean checkMiniMessage(String path, String text) {
            Set<String> unknownTags = new TreeSet<>();
            TagResolver recorder = new TagResolver() {
                @Override
                public @Nullable Tag resolve(String name, ArgumentQueue arguments, Context ctx) {
                    return null;
                }

                @Override
                public boolean has(String name) {
                    if (!STANDARD_TAGS.has(name) && !PLACEHOLDERS.contains(name)) {
                        unknownTags.add(name);
                    }
                    return false;
                }
            };
            try {
                MINI_MESSAGE.deserialize(text, recorder);
            } catch (RuntimeException e) {
                return false;
            }
            if (!unknownTags.isEmpty()) {
                warnings.add("Unknown MiniMessage tag(s) in '" + path + "': <" + String.join(">, <", unknownTags)
                        + ">. They'll show as plain text.");
            }
            return true;
        }

        // ---- list and map readers ----

        private Set<String> readStringSet(String path) {
            Set<String> result = new LinkedHashSet<>();
            for (Object entry : readList(path)) {
                if (entry instanceof String s && !s.isBlank()) {
                    result.add(s);
                } else {
                    warnings.add("Invalid entry in '" + path + "': " + entry + " (skipped)");
                }
            }
            return result;
        }

        /** Material names go to {@code materials}; {@code #namespace:tag} entries go to {@code tags}. */
        private void readIgnored(String path, Set<String> materials, Set<String> tags) {
            for (Object entry : readList(path)) {
                if (!(entry instanceof String s) || s.isBlank()) {
                    warnings.add("Invalid entry in '" + path + "': " + entry + " (skipped)");
                    continue;
                }
                String trimmed = s.trim();
                if (trimmed.startsWith("#")) {
                    NamespacedKey key = NamespacedKey.fromString(trimmed.substring(1).toLowerCase(Locale.ROOT));
                    if (key == null) {
                        warnings.add("Invalid tag in '" + path + "': " + s + " (skipped)");
                    } else {
                        tags.add(key.toString());
                    }
                    continue;
                }
                String material = parseMaterial(path, trimmed);
                if (material != null) {
                    materials.add(material);
                }
            }
        }

        /** A present-but-empty section means "no remainders"; a missing one means the defaults. */
        private Map<String, String> readRemainders(String path, Map<String, String> def) {
            Object value = root.get(path);
            if (value == null) {
                return def;
            }
            if (!(value instanceof ConfigurationSection section)) {
                return invalid(path, value, "(the built-in table)", def);
            }
            Map<String, String> result = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                String item = parseMaterial(path, key);
                if (item == null) {
                    continue;
                }
                Object target = section.get(key);
                if (target instanceof String s && s.trim().equalsIgnoreCase("NONE")) {
                    result.put(item, Remainders.NONE);
                } else if (target instanceof String s) {
                    String remainder = parseMaterial(path + "." + key, s.trim());
                    if (remainder != null) {
                        result.put(item, remainder);
                    }
                } else {
                    warnings.add("Invalid value for '" + path + "." + key + "': " + target
                            + " (skipped; use an item name or NONE)");
                }
            }
            return result;
        }

        private List<?> readList(String path) {
            Object value = root.get(path);
            if (value == null) {
                return List.of();
            }
            if (value instanceof List<?> list) {
                return list;
            }
            warnings.add("Invalid value for '" + path + "': " + value + " (using default [])");
            return List.of();
        }

        /**
         * Accepts DIAMOND, diamond and minecraft:diamond and returns the material key. Unknown and legacy
         * names are skipped with a warning, so items from newer versions only work where they exist.
         */
        private @Nullable String parseMaterial(String path, String name) {
            Material material = Material.matchMaterial(name.trim());
            if (material == null || material.isLegacy()) {
                warnings.add("Unknown item in '" + path + "': " + name + " (skipped)");
                return null;
            }
            return material.getKey().toString();
        }

        // ---- fallbacks ----

        private <T> T invalid(String path, Object value, T def) {
            return invalid(path, value, def, def);
        }

        private <T> T invalid(String path, Object value, Object shownDefault, T def) {
            warnings.add("Invalid value for '" + path + "': " + value + " (using default " + shownDefault + ")");
            return def;
        }
    }
}

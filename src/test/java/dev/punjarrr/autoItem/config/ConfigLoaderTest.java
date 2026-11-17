package dev.punjarrr.autoItem.config;

import dev.punjarrr.autoItem.refill.Remainders;
import dev.punjarrr.autoItem.refill.Trigger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** U30: config parsing, validation and fallbacks. */
class ConfigLoaderTest {

    private static final PluginConfig DEFAULTS = PluginConfig.defaults();

    private static ConfigLoader.LoadResult load(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            throw new AssertionError("test YAML is invalid", e);
        }
        return new ConfigLoader().load(config);
    }

    private static void assertOneWarning(ConfigLoader.LoadResult result, String expected) {
        assertEquals(1, result.warnings().size(), () -> "warnings: " + result.warnings());
        assertTrue(result.warnings().getFirst().contains(expected),
                () -> "expected '" + expected + "' in: " + result.warnings().getFirst());
    }

    @Test
    void bundledConfigMatchesDefaultsWithoutWarnings() throws IOException {
        String yaml;
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            assertTrue(in != null, "config.yml on the classpath");
            yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        ConfigLoader.LoadResult result = load(yaml);
        assertEquals(List.of(), result.warnings());
        assertEquals(DEFAULTS, result.config());
    }

    @Test
    void emptyConfigUsesDefaultsSilently() {
        ConfigLoader.LoadResult result = load("");
        assertEquals(List.of(), result.warnings());
        assertEquals(DEFAULTS, result.config());
    }

    @Test
    void toolBreakIsOffByDefault() {
        assertFalse(DEFAULTS.triggers().contains(Trigger.TOOL_BREAK));
        assertTrue(DEFAULTS.triggers().contains(Trigger.BLOCK_PLACE));
    }

    @Test
    void triggersCanBeSwitched() {
        ConfigLoader.LoadResult result = load("triggers:\n  block-place: false\n  tool-break: true\n");
        assertEquals(List.of(), result.warnings());
        assertFalse(result.config().triggers().contains(Trigger.BLOCK_PLACE));
        assertTrue(result.config().triggers().contains(Trigger.TOOL_BREAK));
        assertTrue(result.config().triggers().contains(Trigger.CONSUME));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "enabled               | maybe",
            "sources.hotbar        | 1",
            "triggers.consume      | yes please",
            "feedback.sound.volume | 11",
            "feedback.sound.pitch  | 0.1",
            "feedback.sound.key    | 'Not A Key!'",
            "config-version        | 0",
    })
    void invalidValuesWarnAndUseTheDefault(String path, String value) {
        ConfigLoader.LoadResult result = load(nested(path, value));
        assertOneWarning(result, "Invalid value for '" + path + "'");
        assertEquals(DEFAULTS, result.config());
    }

    /** {@code a.b.c} and {@code v} as nested YAML. */
    private static String nested(String path, String value) {
        String[] parts = path.split("\\.");
        StringBuilder yaml = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            yaml.append("  ".repeat(i)).append(parts[i]).append(':');
            yaml.append(i == parts.length - 1 ? " " + value + "\n" : "\n");
        }
        return yaml.toString();
    }

    @Test
    void unknownKeysWarn() {
        assertOneWarning(load("triggers:\n  block-plase: false\n"), "Unknown config key 'triggers.block-plase'");
    }

    @Test
    void ignoredItemsAcceptMaterialsAndTags() {
        ConfigLoader.LoadResult result = load("ignored-items: [TOTEM_OF_UNDYING, 'minecraft:diamond', '#minecraft:beds',"
                + " NOT_AN_ITEM, '#Bad Tag!']\n");
        assertEquals(2, result.warnings().size(), () -> "warnings: " + result.warnings());
        assertTrue(result.warnings().get(0).contains("Unknown item in 'ignored-items': NOT_AN_ITEM"));
        assertTrue(result.warnings().get(1).contains("Invalid tag in 'ignored-items'"));
        assertEquals(Set.of("minecraft:totem_of_undying", "minecraft:diamond"), result.config().ignoredMaterials());
        assertEquals(Set.of("minecraft:beds"), result.config().ignoredTags());
    }

    @Test
    void remaindersReplaceTheDefaultsAndAcceptNone() {
        ConfigLoader.LoadResult result = load("remainders:\n  MUSHROOM_STEW: bowl\n  ominous_bottle: none\n"
                + "  FUTURE_ITEM_FROM_27X: BOWL\n  RABBIT_STEW: NOT_AN_ITEM\n  BEETROOT_SOUP: 5\n");
        assertEquals(3, result.warnings().size(), () -> "warnings: " + result.warnings());
        assertEquals(Map.of("minecraft:mushroom_stew", "minecraft:bowl", "minecraft:ominous_bottle", Remainders.NONE),
                result.config().remainders());
    }

    @Test
    void emptyRemaindersSectionMeansNoRemainders() {
        ConfigLoader.LoadResult result = load("remainders: {}\n");
        assertEquals(List.of(), result.warnings());
        assertEquals(Map.of(), result.config().remainders());
    }

    @Test
    void remaindersThatIsNotASectionFallsBack() {
        ConfigLoader.LoadResult result = load("remainders: [BOWL]\n");
        assertOneWarning(result, "Invalid value for 'remainders'");
        assertEquals(DEFAULTS.remainders(), result.config().remainders());
    }

    @Test
    void disabledWorlds() {
        ConfigLoader.LoadResult result = load("disabled-worlds: [world_nether, 5]\n");
        assertOneWarning(result, "Invalid entry in 'disabled-worlds': 5");
        assertEquals(Set.of("world_nether"), result.config().disabledWorlds());
    }

    @Test
    void unknownMiniMessageTagsWarnButKeepTheText() {
        ConfigLoader.LoadResult result = load("messages:\n  refilled: '<yelow>Refilled <item>'\n");
        assertOneWarning(result, "Unknown MiniMessage tag(s) in 'messages.refilled': <yelow>");
        assertEquals("<yelow>Refilled <item>", result.config().messages().refilled());
    }

    @Test
    void blankMessageFallsBack() {
        ConfigLoader.LoadResult result = load("messages:\n  usage: ''\n");
        assertOneWarning(result, "Invalid value for 'messages.usage'");
        assertEquals(DEFAULTS.messages().usage(), result.config().messages().usage());
    }

    @Test
    void newerConfigVersionWarns() {
        assertOneWarning(load("config-version: 2\n"), "newer plugin version");
    }
}

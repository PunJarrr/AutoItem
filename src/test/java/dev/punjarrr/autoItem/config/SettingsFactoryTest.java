package dev.punjarrr.autoItem.config;

import dev.punjarrr.autoItem.refill.RefillSettings;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** U16/U30: ignored item tags are expanded at load time; unknown tags warn and are skipped. */
class SettingsFactoryTest {

    @Test
    void tagsExpandToMaterialsAndUnknownTagsWarn() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("ignored-items: [DIAMOND, '#minecraft:beds', '#minecraft:from_the_future']\n");
        PluginConfig config = new ConfigLoader().load(yaml).config();

        Map<String, Set<String>> tags = Map.of("minecraft:beds", Set.of("minecraft:red_bed", "minecraft:blue_bed"));
        List<String> warnings = new ArrayList<>();
        RefillSettings settings = new SettingsFactory(tags::get).create(config, warnings::add);

        assertEquals(Set.of("minecraft:diamond", "minecraft:red_bed", "minecraft:blue_bed"),
                settings.ignoredMaterials());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("#minecraft:from_the_future"));
    }
}

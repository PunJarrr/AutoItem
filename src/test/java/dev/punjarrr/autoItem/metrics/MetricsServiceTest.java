package dev.punjarrr.autoItem.metrics;

import dev.punjarrr.autoItem.config.ConfigLoader;
import dev.punjarrr.autoItem.config.PluginConfig;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsServiceTest {

    private static PluginConfig load(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return new ConfigLoader().load(config).config();
    }

    @Test
    void defaultTriggersAreAllButToolBreak() {
        assertEquals(Map.of("block-place", 1, "consume", 1, "buckets", 1, "throwables", 1, "right-click", 1,
                "bows", 1), MetricsService.enabledTriggers(PluginConfig.defaults()));
    }

    @Test
    void onlyEnabledTriggersAreCounted() throws InvalidConfigurationException {
        PluginConfig config = load("""
                triggers:
                  block-place: false
                  consume: false
                  buckets: false
                  throwables: false
                  right-click: false
                  bows: false
                  tool-break: true
                """);
        assertEquals(Map.of("tool-break", 1), MetricsService.enabledTriggers(config));
    }

    @Test
    void noTriggersIsReportedAsNone() throws InvalidConfigurationException {
        PluginConfig config = load("""
                triggers:
                  block-place: false
                  consume: false
                  buckets: false
                  throwables: false
                  right-click: false
                  bows: false
                """);
        assertEquals(Map.of("none", 1), MetricsService.enabledTriggers(config));
    }
}

package dev.punjarrr.autoItem.metrics;

import dev.punjarrr.autoItem.config.ConfigService;
import dev.punjarrr.autoItem.config.PluginConfig;
import dev.punjarrr.autoItem.refill.Trigger;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anonymous bStats metrics. Started once at startup when {@code metrics: true}; server owners can also
 * turn bStats off for every plugin in {@code plugins/bStats/config.yml}.
 *
 * <p>Chart callbacks run on the main thread on Paper and on bStats' own thread on Folia, so they only
 * read the immutable config snapshot.</p>
 */
public final class MetricsService {

    /** The AutoItem plugin ID on bstats.org. */
    public static final int BSTATS_PLUGIN_ID = 35427;

    private final Metrics metrics;

    private MetricsService(Metrics metrics) {
        this.metrics = metrics;
    }

    /** Starts bStats, or returns null when {@code metrics} is off. */
    public static MetricsService startIfEnabled(JavaPlugin plugin, ConfigService config) {
        if (!config.config().metrics()) {
            return null;
        }
        Metrics metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new AdvancedPie("enabled_triggers", () -> enabledTriggers(config.config())));
        metrics.addCustomChart(new SimplePie("tool_break",
                () -> config.config().triggers().contains(Trigger.TOOL_BREAK) ? "on" : "off"));
        return new MetricsService(metrics);
    }

    /** One entry per enabled trigger, so the pie shows how many servers keep each one on. */
    static Map<String, Integer> enabledTriggers(PluginConfig config) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (Trigger trigger : Trigger.values()) {
            if (config.triggers().contains(trigger)) {
                values.put(trigger.configKey(), 1);
            }
        }
        if (values.isEmpty()) {
            values.put("none", 1);
        }
        return values;
    }

    public void shutdown() {
        metrics.shutdown();
    }
}

package dev.punjarrr.autoItem.config;

import dev.punjarrr.autoItem.refill.RefillSettings;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Owns the current config snapshot. Everything that needs config gets this service injected and calls
 * {@link #config()} or {@link #settings()} when it needs a value, so a reload is seen everywhere at once.
 * The snapshot is one immutable object behind a volatile field, so Folia region threads always see a
 * consistent pair.
 */
public final class ConfigService {

    public sealed interface ReloadResult {
        /** The new config is active. Invalid values fell back to their defaults, one warning each. */
        record Ok(int warnings) implements ReloadResult {
        }

        /** The file couldn't be read or isn't valid YAML. The previous config stays active. */
        record Failed(String reason) implements ReloadResult {
        }
    }

    /** A config and the settings derived from it, swapped together. */
    public record Snapshot(PluginConfig config, RefillSettings settings) {
    }

    private final File file;
    private final Logger logger;
    private final ConfigLoader loader;
    private final SettingsFactory settingsFactory;
    private volatile Snapshot current;
    private volatile boolean loadedOnce;

    public ConfigService(File file, Logger logger, ConfigLoader loader, SettingsFactory settingsFactory) {
        this.file = file;
        this.logger = logger;
        this.loader = loader;
        this.settingsFactory = settingsFactory;
        PluginConfig defaults = PluginConfig.defaults();
        this.current = new Snapshot(defaults, settingsFactory.create(defaults, logger::warning));
    }

    public Snapshot snapshot() {
        return current;
    }

    public PluginConfig config() {
        return current.config();
    }

    public RefillSettings settings() {
        return current.settings();
    }

    /** Reads the file again. Logs every problem; never throws. */
    public synchronized ReloadResult reload() {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            logger.severe("Could not load " + file.getName() + ", "
                    + (loadedOnce ? "keeping the previous settings" : "using the defaults") + ": " + reason);
            return new ReloadResult.Failed(reason);
        }
        ConfigLoader.LoadResult result = loader.load(yaml);
        List<String> warnings = new ArrayList<>(result.warnings());
        RefillSettings settings = settingsFactory.create(result.config(), warnings::add);
        warnings.forEach(logger::warning);
        current = new Snapshot(result.config(), settings);
        loadedOnce = true;
        return new ReloadResult.Ok(warnings.size());
    }
}

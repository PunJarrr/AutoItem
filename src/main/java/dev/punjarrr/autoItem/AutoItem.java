package dev.punjarrr.autoItem;

import dev.punjarrr.autoItem.bukkit.ItemTags;
import dev.punjarrr.autoItem.bukkit.PlayerStates;
import dev.punjarrr.autoItem.config.ConfigLoader;
import dev.punjarrr.autoItem.config.ConfigService;
import dev.punjarrr.autoItem.config.SettingsFactory;
import dev.punjarrr.autoItem.listener.BlockPlaceListener;
import dev.punjarrr.autoItem.listener.ConsumeListener;
import dev.punjarrr.autoItem.refill.CheckRecorder;
import dev.punjarrr.autoItem.refill.RefillExecutor;
import dev.punjarrr.autoItem.refill.RefillPlanner;
import dev.punjarrr.autoItem.refill.RefillTask;
import dev.punjarrr.autoItem.toggle.ToggleService;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/** Wiring only. Not final: MockBukkit loads the plugin through a generated subclass. */
public class AutoItem extends JavaPlugin {

    private CheckRecorder recorder;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigService configService = new ConfigService(new File(getDataFolder(), "config.yml"), getLogger(),
                new ConfigLoader(), new SettingsFactory(ItemTags::materialKeys));
        configService.reload();

        ToggleService toggles = new ToggleService(this);
        PlayerStates states = new PlayerStates(toggles);
        RefillTask task = new RefillTask(configService, states, new RefillPlanner(), new RefillExecutor());
        recorder = new CheckRecorder(this, configService, states, task);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new BlockPlaceListener(recorder), this);
        pluginManager.registerEvents(new ConsumeListener(recorder), this);
    }

    /** For tests. */
    CheckRecorder recorder() {
        return recorder;
    }
}

package dev.punjarrr.autoItem;

import dev.punjarrr.autoItem.bukkit.ItemTags;
import dev.punjarrr.autoItem.command.AutoItemCommand;
import dev.punjarrr.autoItem.bukkit.PlayerStates;
import dev.punjarrr.autoItem.config.ConfigLoader;
import dev.punjarrr.autoItem.config.ConfigService;
import dev.punjarrr.autoItem.config.SettingsFactory;
import dev.punjarrr.autoItem.feedback.FeedbackService;
import dev.punjarrr.autoItem.feedback.Messages;
import dev.punjarrr.autoItem.listener.BlockPlaceListener;
import dev.punjarrr.autoItem.listener.BowListener;
import dev.punjarrr.autoItem.listener.BucketListener;
import dev.punjarrr.autoItem.listener.ConsumeListener;
import dev.punjarrr.autoItem.listener.DiscardListener;
import dev.punjarrr.autoItem.listener.RightClickListener;
import dev.punjarrr.autoItem.listener.ThrowableListener;
import dev.punjarrr.autoItem.listener.ToolBreakListener;
import dev.punjarrr.autoItem.refill.CheckRecorder;
import dev.punjarrr.autoItem.refill.RefillExecutor;
import dev.punjarrr.autoItem.refill.RefillPlanner;
import dev.punjarrr.autoItem.refill.RefillTask;
import dev.punjarrr.autoItem.toggle.ToggleService;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.function.Predicate;

/** Wiring only. Not final: MockBukkit loads the plugin through a generated subclass. */
public class AutoItem extends JavaPlugin {

    private CheckRecorder recorder;
    private ConfigService configService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configService = new ConfigService(new File(getDataFolder(), "config.yml"), getLogger(),
                new ConfigLoader(), new SettingsFactory(ItemTags::materialKeys));
        configService.reload();

        ToggleService toggles = new ToggleService(this);
        PlayerStates states = new PlayerStates(toggles);
        Messages messages = new Messages(configService);
        RefillTask task = new RefillTask(configService, states, new RefillPlanner(), new RefillExecutor(),
                new FeedbackService(configService, messages), getLogger());
        recorder = new CheckRecorder(this, configService, states, task);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new BlockPlaceListener(recorder), this);
        pluginManager.registerEvents(new ConsumeListener(recorder), this);
        pluginManager.registerEvents(new BucketListener(recorder), this);
        pluginManager.registerEvents(new ThrowableListener(recorder), this);
        pluginManager.registerEvents(new RightClickListener(recorder, equipsOnRightClick()), this);
        pluginManager.registerEvents(new BowListener(recorder), this);
        pluginManager.registerEvents(new ToolBreakListener(recorder), this);
        pluginManager.registerEvents(new DiscardListener(recorder), this);

        AutoItemCommand command = new AutoItemCommand(configService, toggles, messages);
        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("autoitem"),
                "command 'autoitem' missing from plugin.yml");
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
    }

    @Override
    public void onDisable() {
        if (recorder != null) {
            recorder.clear();
        }
    }

    /**
     * Which items right-clicking equips. A hook only because MockBukkit can't answer
     * {@code Material#getEquipmentSlot()}; tests override it.
     */
    protected Predicate<ItemStack> equipsOnRightClick() {
        return RightClickListener::equipsOnRightClick;
    }

    /** For tests. */
    CheckRecorder recorder() {
        return recorder;
    }

    /** For tests. */
    ConfigService configService() {
        return configService;
    }
}

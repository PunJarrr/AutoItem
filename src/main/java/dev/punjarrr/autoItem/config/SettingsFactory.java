package dev.punjarrr.autoItem.config;

import dev.punjarrr.autoItem.refill.RefillSettings;
import dev.punjarrr.autoItem.refill.Remainders;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Builds the planner's {@link RefillSettings} from a {@link PluginConfig}, expanding item tags. */
public final class SettingsFactory {

    private final Function<String, @Nullable Set<String>> itemTags;

    /**
     * @param itemTags looks up an item tag key ({@code minecraft:beds}) and returns its material keys,
     *                 or null if the server doesn't know the tag
     */
    public SettingsFactory(Function<String, @Nullable Set<String>> itemTags) {
        this.itemTags = itemTags;
    }

    public RefillSettings create(PluginConfig config, Consumer<String> warn) {
        Set<String> ignored = new HashSet<>(config.ignoredMaterials());
        for (String tag : config.ignoredTags()) {
            Set<String> members = itemTags.apply(tag);
            if (members == null) {
                warn.accept("Unknown item tag in 'ignored-items': #" + tag + " (skipped)");
            } else {
                ignored.addAll(members);
            }
        }
        return new RefillSettings(config.enabled(), config.triggers(), config.hotbarSource(), ignored,
                config.disabledWorlds(), new Remainders(config.remainders()));
    }

    /** For tests and the initial state: no tags known. */
    public static RefillSettings withoutTags(PluginConfig config) {
        return new SettingsFactory(tag -> null).create(config, w -> { });
    }
}

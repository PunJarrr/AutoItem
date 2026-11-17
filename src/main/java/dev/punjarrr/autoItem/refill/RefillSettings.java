package dev.punjarrr.autoItem.refill;

import java.util.Set;

/**
 * The config values the recorder and planner use, with item tags already expanded to material keys.
 *
 * @param ignoredMaterials material keys that are never refilled
 * @param disabledWorlds   exact world names
 */
public record RefillSettings(boolean enabled, Set<Trigger> triggers, boolean hotbarSource,
                             Set<String> ignoredMaterials, Set<String> disabledWorlds, Remainders remainders) {

    public RefillSettings {
        triggers = Set.copyOf(triggers);
        ignoredMaterials = Set.copyOf(ignoredMaterials);
        disabledWorlds = Set.copyOf(disabledWorlds);
    }
}

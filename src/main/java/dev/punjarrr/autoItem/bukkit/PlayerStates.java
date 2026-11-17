package dev.punjarrr.autoItem.bukkit;

import dev.punjarrr.autoItem.refill.PlayerState;
import dev.punjarrr.autoItem.toggle.ToggleService;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Reads a {@link PlayerState} from a live player. Call only from the player's owning thread. */
public final class PlayerStates {

    public static final String USE_PERMISSION = "autoitem.use";

    private final ToggleService toggles;

    public PlayerStates(ToggleService toggles) {
        this.toggles = toggles;
    }

    public PlayerState of(Player player) {
        World world = player.getWorld();
        return new PlayerState(player.isOnline(), player.isDead(), player.getGameMode().name(), world.getUID(),
                world.getName(), toggles.isEnabled(player), player.hasPermission(USE_PERMISSION),
                player.getInventory().getHeldItemSlot());
    }
}

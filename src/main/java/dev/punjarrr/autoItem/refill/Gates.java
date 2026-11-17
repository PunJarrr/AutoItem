package dev.punjarrr.autoItem.refill;

/** The player gates of SPEC §4.4, checked at event time and again at task time. */
public final class Gates {

    private Gates() {
    }

    public static boolean allows(PlayerState player, RefillSettings settings) {
        return settings.enabled()
                && player.online()
                && !player.dead()
                && (player.gameMode().equals("SURVIVAL") || player.gameMode().equals("ADVENTURE"))
                && !settings.disabledWorlds().contains(player.worldName())
                && player.toggledOn()
                && player.hasUsePermission();
    }
}

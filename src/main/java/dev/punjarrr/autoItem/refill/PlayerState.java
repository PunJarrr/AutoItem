package dev.punjarrr.autoItem.refill;

import java.util.UUID;

/**
 * The player facts the gates need, read at event time (to skip recording) and again at task time.
 *
 * @param gameMode the Bukkit game mode name, e.g. {@code SURVIVAL}
 * @param heldSlot the selected hotbar slot (0–8); never used as a source, so one hand never empties the other
 */
public record PlayerState(boolean online, boolean dead, String gameMode, UUID worldId, String worldName,
                          boolean toggledOn, boolean hasUsePermission, int heldSlot) {
}

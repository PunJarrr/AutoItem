package dev.punjarrr.autoItem.feedback;

import dev.punjarrr.autoItem.config.ConfigService;
import dev.punjarrr.autoItem.config.PluginConfig;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * What the player notices after a refill: a quiet sound (on by default) and an action-bar message (off by
 * default). The sound is an Adventure sound built from a key, so a key a version doesn't know plays nothing.
 * Runs on the player's owning thread, inside the refill task.
 */
public final class FeedbackService {

    private final ConfigService config;
    private final Messages messages;

    public FeedbackService(ConfigService config, Messages messages) {
        this.config = config;
        this.messages = messages;
    }

    public void refilled(Player player, ItemStack moved) {
        PluginConfig current = config.config();
        PluginConfig.Feedback feedback = current.feedback();
        if (feedback.soundEnabled()) {
            player.playSound(Sound.sound(Key.key(feedback.soundKey()), Sound.Source.PLAYER, feedback.soundVolume(),
                    feedback.soundPitch()));
        }
        if (feedback.actionBar()) {
            player.sendActionBar(messages.render(current.messages().refilled(),
                    Placeholder.component("item", Messages.itemName(moved))));
        }
    }
}

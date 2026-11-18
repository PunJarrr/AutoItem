package dev.punjarrr.autoItem.feedback;

import dev.punjarrr.autoItem.config.ConfigService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Renders the MiniMessage strings under {@code messages} in config.yml, with {@code <prefix>} filled in. */
public final class Messages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final ConfigService config;

    public Messages(ConfigService config) {
        this.config = config;
    }

    public Component render(String template, TagResolver... placeholders) {
        TagResolver prefix = Placeholder.parsed("prefix", config.config().messages().prefix());
        return MINI_MESSAGE.deserialize(template, TagResolver.resolver(prefix, TagResolver.resolver(placeholders)));
    }

    /** The item's custom name if it has one, else its name translated on the player's client. */
    public static Component itemName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component name = meta.displayName();
            if (name != null) {
                return name;
            }
        }
        return Component.translatable(stack);
    }
}

package dev.punjarrr.autoItem.command;

import dev.punjarrr.autoItem.config.ConfigService;
import dev.punjarrr.autoItem.config.PluginConfig;
import dev.punjarrr.autoItem.feedback.Messages;
import dev.punjarrr.autoItem.toggle.ToggleService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * {@code /autoitem toggle} (per player, {@code autoitem.toggle}) and {@code /autoitem reload}
 * ({@code autoitem.reload}). On Folia a player's command runs on that player's region thread, so the PDC
 * write in toggle is on the owning thread; reload only swaps an immutable snapshot.
 */
public final class AutoItemCommand implements TabExecutor {

    public static final String TOGGLE_PERMISSION = "autoitem.toggle";
    public static final String RELOAD_PERMISSION = "autoitem.reload";

    private final ConfigService config;
    private final ToggleService toggles;
    private final Messages messages;

    public AutoItemCommand(ConfigService config, ToggleService toggles, Messages messages) {
        this.config = config;
        this.toggles = toggles;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        PluginConfig.Messages text = config.config().messages();
        String sub = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "toggle" -> toggle(sender, text);
            case "reload" -> reload(sender, text);
            default -> sender.sendMessage(messages.render(text.usage()));
        }
        return true;
    }

    private void toggle(CommandSender sender, PluginConfig.Messages text) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render(text.playerOnly()));
            return;
        }
        if (!player.hasPermission(TOGGLE_PERMISSION)) {
            sender.sendMessage(messages.render(text.noPermission()));
            return;
        }
        boolean enabled = toggles.toggle(player);
        sender.sendMessage(messages.render(enabled ? text.toggledOn() : text.toggledOff()));
    }

    private void reload(CommandSender sender, PluginConfig.Messages text) {
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            sender.sendMessage(messages.render(text.noPermission()));
            return;
        }
        ConfigService.ReloadResult result = config.reload();
        // Messages come from the config that is active now (the new one, unless the reload failed).
        PluginConfig.Messages now = config.config().messages();
        if (result instanceof ConfigService.ReloadResult.Ok ok) {
            sender.sendMessage(ok.warnings() == 0
                    ? messages.render(now.reloaded())
                    : messages.render(now.reloadedWithWarnings(),
                    Placeholder.unparsed("count", String.valueOf(ok.warnings()))));
        } else {
            sender.sendMessage(messages.render(now.reloadFailed()));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        return List.of("toggle", "reload").stream()
                .filter(sub -> sender.hasPermission("autoitem." + sub))
                .filter(sub -> sub.startsWith(typed))
                .toList();
    }
}

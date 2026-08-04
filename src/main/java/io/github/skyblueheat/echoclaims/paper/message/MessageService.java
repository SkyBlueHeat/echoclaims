package io.github.skyblueheat.echoclaims.paper.message;

import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Abstraction for sending localized messages to command senders.
 *
 * <p>Extracted from {@link PaperMessageService} to enable unit testing of command
 * handlers without a running Paper server.</p>
 */
public interface MessageService {

    /**
     * Sends a localized message to the given sender.
     *
     * @param sender the command sender
     * @param key the message key
     */
    void send(CommandSender sender, String key);

    /**
     * Sends a localized message with placeholder substitution.
     *
     * @param sender the command sender
     * @param key the message key
     * @param placeholders placeholder name to replacement value
     */
    void send(CommandSender sender, String key, Map<String, String> placeholders);
}

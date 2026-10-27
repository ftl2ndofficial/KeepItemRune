package com.ftl2nd.keepitemrune.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages {@code messages.yml} using {@link MessageKey} enum mappings and dumb-proof YAML recovery.
 */
public final class MessageManager {

    public static final String DEFAULT_PREFIX = "<gradient:#23FABE:#AEFFE8>ᴋᴇᴇᴘɪᴛᴇᴍʀᴜɴᴇ</gradient> <gray>»</gray> ";

    private final File messagesFile;
    private final Supplier<InputStream> defaultStreamSupplier;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private String prefix = DEFAULT_PREFIX;
    private final Map<MessageKey, String> messages = new EnumMap<>(MessageKey.class);

    public MessageManager(JavaPlugin plugin) {
        this(
                new File(plugin.getDataFolder(), "messages.yml"),
                () -> plugin.getResource("messages.yml"),
                plugin.getLogger()
        );
    }

    public MessageManager(File messagesFile, Supplier<InputStream> defaultStreamSupplier, Logger logger) {
        this.messagesFile = messagesFile;
        this.defaultStreamSupplier = defaultStreamSupplier;
        this.logger = logger;
        load();
    }

    public void load() {
        FileConfiguration config;
        try {
            config = YamlAutoUpdater.loadAndSync(messagesFile, defaultStreamSupplier.get(), logger);
        } catch (IOException e) {
            if (logger != null) {
                logger.log(Level.WARNING, "Failed to load or sync messages.yml, using fallback messages.", e);
            }
            config = YamlConfiguration.loadConfiguration(messagesFile);
        }

        parseMessages(config);
    }

    public void reload() {
        load();
    }

    void parseMessages(FileConfiguration config) {
        Object rawPrefix = config.get("prefix");
        if (rawPrefix == null) {
            this.prefix = DEFAULT_PREFIX;
        } else if (rawPrefix instanceof ConfigurationSection || rawPrefix instanceof List<?>) {
            warn("Invalid type for 'prefix' in messages.yml (expected string); using default prefix.");
            this.prefix = DEFAULT_PREFIX;
        } else {
            this.prefix = String.valueOf(rawPrefix);
        }

        messages.clear();
        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section == null && config.contains("messages")) {
            warn("Section 'messages' in messages.yml is malformed; falling back to built-in default messages.");
        }

        for (MessageKey key : MessageKey.values()) {
            if (section == null || !section.contains(key.path(), true)) {
                messages.put(key, key.defaultMessage());
                continue;
            }

            Object rawVal = section.get(key.path());
            if (rawVal == null) {
                messages.put(key, "");
            } else if (rawVal instanceof ConfigurationSection) {
                warn("Invalid section for 'messages." + key.path() + "' in messages.yml; using default message.");
                messages.put(key, key.defaultMessage());
            } else if (rawVal instanceof List<?> list) {
                List<String> lines = new ArrayList<>(list.size());
                for (Object elem : list) {
                    if (elem != null && !(elem instanceof ConfigurationSection)) {
                        lines.add(String.valueOf(elem));
                    }
                }
                messages.put(key, String.join("<newline>", lines));
            } else {
                messages.put(key, String.valueOf(rawVal));
            }
        }
    }

    private void warn(String msg) {
        if (logger != null) {
            logger.warning(msg);
        }
    }

    public String getPrefix() {
        return prefix;
    }

    public String getRawMessage(MessageKey key) {
        if (key == null) {
            return "";
        }
        return messages.getOrDefault(key, key.defaultMessage());
    }

    public Component getComponent(MessageKey key, TagResolver... resolvers) {
        String raw = getRawMessage(key);
        if (raw == null || raw.isBlank()) {
            return Component.empty();
        }
        return miniMessage.deserialize(prefix + raw, resolvers);
    }

    public void send(CommandSender sender, MessageKey key, TagResolver... resolvers) {
        if (sender == null || key == null) {
            return;
        }
        String raw = getRawMessage(key);
        if (raw == null || raw.isBlank()) {
            return;
        }
        sender.sendMessage(miniMessage.deserialize(prefix + raw, resolvers));
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }
}

package com.ftl2nd.keepitemrune.command;

import com.ftl2nd.keepitemrune.type.RunePermission;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Defines all subcommands for {@code /keepitemrune}.
 */
public enum SubCommand {

    ADD_ITEM(
            "additem",
            Set.of("additem", "protect"),
            RunePermission.ADD_ITEM,
            true,
            "/keepitemrune additem",
            "Protect the item in your main hand"
    ),
    REMOVE_ITEM(
            "removeitem",
            Set.of("removeitem", "unprotect"),
            RunePermission.REMOVE_ITEM,
            true,
            "/keepitemrune removeitem",
            "Remove protection from the item in your main hand"
    ),
    GIVE_RUNE(
            "giverune",
            Set.of("giverune", "givevoucher", "give"),
            RunePermission.GIVE_RUNE,
            false,
            "/keepitemrune giverune <player> [amount]",
            "Give rune(s) to a player"
    ),
    RELOAD(
            "reload",
            Set.of("reload", "reloadconfig"),
            RunePermission.RELOAD,
            false,
            "/keepitemrune reload",
            "Reload configuration files"
    );

    private final String primaryName;
    private final Set<String> aliases;
    private final RunePermission permission;
    private final boolean playerOnly;
    private final String usage;
    private final String description;

    SubCommand(
            String primaryName,
            Set<String> aliases,
            RunePermission permission,
            boolean playerOnly,
            String usage,
            String description
    ) {
        this.primaryName = primaryName;
        this.aliases = aliases;
        this.permission = permission;
        this.playerOnly = playerOnly;
        this.usage = usage;
        this.description = description;
    }

    public String primaryName() {
        return primaryName;
    }

    public Set<String> aliases() {
        return aliases;
    }

    public RunePermission permission() {
        return permission;
    }

    public boolean isPlayerOnly() {
        return playerOnly;
    }

    public String usage() {
        return usage;
    }

    public String description() {
        return description;
    }

    public static Optional<SubCommand> fromInput(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawInput.trim().toLowerCase(Locale.ROOT);
        for (SubCommand cmd : values()) {
            if (cmd.aliases.contains(normalized)) {
                return Optional.of(cmd);
            }
        }
        return Optional.empty();
    }
}

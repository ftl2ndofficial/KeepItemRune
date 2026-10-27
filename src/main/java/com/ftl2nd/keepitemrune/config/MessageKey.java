package com.ftl2nd.keepitemrune.config;

/**
 * Enumeration of all configurable message keys in {@code messages.yml} along with
 * their built-in fallback values if a user's configuration is missing or malformed.
 */
public enum MessageKey {

    NO_PERMISSION("no-permission", "<red>You do not have permission to use this command.</red>"),
    NOT_PLAYER("not-player", "<red>Only players can execute this command.</red>"),
    ITEM_ADD_SUCCESS("item-add-success", "<green>Rune applied to your item.</green>"),
    ITEM_CANNOT_BE_ADDED("item-cannot-be-added", "<red>This item cannot be protected with a rune.</red>"),
    ITEM_IS_NOT_PROTECTED("item-is-not-protected", "<red>This item is not protected.</red>"),
    ITEM_UNPROTECT_SUCCESS("item-unprotect-success", "<green>Removed rune protection from your item.</green>"),
    PLAYER_RESPAWN_WITH_ITEM("player-respawn-with-item", "<green>Your protected items have been returned, and their runes have shattered.</green>"),
    RELOAD_CONFIG_SUCCESS("reload-config-success", "<green>Configuration reloaded.</green>"),
    TOO_MANY_RUNES("too-many-runes", "<red>You can only apply 1 rune at a time.</red>"),
    ITEM_ALREADY_PROTECTED("item-already-protected", "<red>This item is already protected.</red>"),
    PLAYER_IN_CREATIVE("player-in-creative", "<red>Cannot apply runes via inventory click in Creative mode.</red>"),
    NO_ITEM_IN_HAND("no-item-in-hand", "<red>You must hold an item in your main hand.</red>"),
    AMOUNT_LOWER_THAN_ONE("amount-lower-than-1", "<red>Amount must be at least 1.</red>"),
    INVALID_AMOUNT("invalid-amount", "<red>Invalid amount specified.</red>"),
    NO_PLAYER_SELECTED("no-player-selected", "<red>Please specify a player.</red>"),
    PLAYER_NOT_FOUND("player-not-found", "<red>Player not found or offline.</red>"),
    TOO_MANY_ITEMS("too-many-items", "<red>Cannot apply a rune to a stacked item.</red>"),
    GIVE_RUNE_SUCCESS("give-rune-success", "<green>Gave <white><amount></white> rune(s) to <yellow><player></yellow>.</green>");

    private final String path;
    private final String defaultMessage;

    MessageKey(String path, String defaultMessage) {
        this.path = path;
        this.defaultMessage = defaultMessage;
    }

    public String path() {
        return path;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}

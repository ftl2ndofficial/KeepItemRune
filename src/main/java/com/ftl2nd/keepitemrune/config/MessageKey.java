package com.ftl2nd.keepitemrune.config;

/**
 * Enumeration of all configurable message keys in {@code messages.yml} along with
 * their built-in fallback values if a user's configuration is missing or malformed.
 */
public enum MessageKey {

    NO_PERMISSION("no-permission", "<#ff7675>You do not have permission to use this command.</#ff7675>"),
    NOT_PLAYER("not-player", "<#ff7675>Only players can execute this command.</#ff7675>"),
    ITEM_ADD_SUCCESS("item-add-success", "<#55efc4>Rune applied to your item.</#55efc4>"),
    ITEM_CANNOT_BE_ADDED("item-cannot-be-added", "<#ff7675>This item cannot be protected with a rune.</#ff7675>"),
    ITEM_IS_NOT_PROTECTED("item-is-not-protected", "<#ff7675>This item is not protected.</#ff7675>"),
    ITEM_UNPROTECT_SUCCESS("item-unprotect-success", "<#55efc4>Removed rune protection from your item.</#55efc4>"),
    PLAYER_RESPAWN_WITH_ITEM("player-respawn-with-item", "<#55efc4>Your protected items have been returned, and their runes have shattered.</#55efc4>"),
    RELOAD_CONFIG_SUCCESS("reload-config-success", "<#55efc4>Configuration reloaded.</#55efc4>"),
    TOO_MANY_RUNES("too-many-runes", "<#ff7675>You can only apply <#ffbe76>1</#ffbe76> rune at a time.</#ff7675>"),
    ITEM_ALREADY_PROTECTED("item-already-protected", "<#ff7675>This item is already protected.</#ff7675>"),
    PLAYER_IN_CREATIVE("player-in-creative", "<#ff7675>Cannot apply runes via inventory click in Creative mode.</#ff7675>"),
    NO_ITEM_IN_HAND("no-item-in-hand", "<#ff7675>You must hold an item in your main hand.</#ff7675>"),
    AMOUNT_LOWER_THAN_ONE("amount-lower-than-1", "<#ff7675>Amount must be at least <#ffbe76>1</#ffbe76>.</#ff7675>"),
    INVALID_AMOUNT("invalid-amount", "<#ff7675>Invalid amount specified.</#ff7675>"),
    NO_PLAYER_SELECTED("no-player-selected", "<#ff7675>Please specify a player.</#ff7675>"),
    PLAYER_NOT_FOUND("player-not-found", "<#ff7675>Player not found or offline.</#ff7675>"),
    TOO_MANY_ITEMS("too-many-items", "<#ff7675>Cannot apply a rune to a stacked item.</#ff7675>"),
    GIVE_RUNE_SUCCESS("give-rune-success", "<#55efc4>Gave <#ffbe76><amount></#ffbe76> rune(s) to <#ffbe76><player></#ffbe76>.</#55efc4>");

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

package com.ftl2nd.keepitemrune.type;

import com.ftl2nd.keepitemrune.config.MessageKey;

/**
 * Represents the outcome of applying or removing rune protection on an item,
 * paired with its feedback {@link MessageKey} and {@link RuneSound}.
 */
public enum ProtectionApplyResult {

    SUCCESS(MessageKey.ITEM_ADD_SUCCESS, RuneSound.APPLY_SUCCESS, true),
    UNPROTECT_SUCCESS(MessageKey.ITEM_UNPROTECT_SUCCESS, RuneSound.REMOVE_SUCCESS, true),
    EMPTY_ITEM(MessageKey.NO_ITEM_IN_HAND, RuneSound.APPLY_FAIL, false),
    CREATIVE_MODE_BLOCKED(MessageKey.PLAYER_IN_CREATIVE, RuneSound.APPLY_FAIL, false),
    ITEM_NOT_WHITELISTED(MessageKey.ITEM_CANNOT_BE_ADDED, RuneSound.APPLY_FAIL, false),
    CANNOT_PROTECT_RUNE(MessageKey.ITEM_CANNOT_BE_ADDED, RuneSound.APPLY_FAIL, false),
    STACKED_RUNE(MessageKey.TOO_MANY_RUNES, RuneSound.APPLY_FAIL, false),
    STACKED_TARGET_ITEM(MessageKey.TOO_MANY_ITEMS, RuneSound.APPLY_FAIL, false),
    ALREADY_PROTECTED(MessageKey.ITEM_ALREADY_PROTECTED, RuneSound.APPLY_FAIL, false),
    NOT_PROTECTED(MessageKey.ITEM_IS_NOT_PROTECTED, RuneSound.APPLY_FAIL, false);

    private final MessageKey messageKey;
    private final RuneSound sound;
    private final boolean success;

    ProtectionApplyResult(MessageKey messageKey, RuneSound sound, boolean success) {
        this.messageKey = messageKey;
        this.sound = sound;
        this.success = success;
    }

    public MessageKey messageKey() {
        return messageKey;
    }

    public RuneSound sound() {
        return sound;
    }

    public boolean isSuccess() {
        return success;
    }
}

package com.ftl2nd.keepitemrune.type;

import org.bukkit.NamespacedKey;

/**
 * Defines all PersistentDataContainer (PDC) and NMS CustomData keys used by KeepItemRune.
 */
public enum RuneTagKey {

    RUNE("rune"),
    PROTECTED("protected"),
    PROTECTION_UUID("protection_uuid"),
    CLIENT_LORE_COUNT("client_lore_count");

    public static final String NAMESPACE = "keepitemrune";

    private final String key;
    private final String fullKey;
    private final NamespacedKey namespacedKey;

    RuneTagKey(String key) {
        this.key = key;
        this.fullKey = NAMESPACE + ":" + key;
        this.namespacedKey = new NamespacedKey(NAMESPACE, key);
    }

    public String key() {
        return key;
    }

    public String fullKey() {
        return fullKey;
    }

    public NamespacedKey namespacedKey() {
        return namespacedKey;
    }
}

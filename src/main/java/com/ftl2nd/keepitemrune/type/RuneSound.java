package com.ftl2nd.keepitemrune.type;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Defines all sound triggers in KeepItemRune along with their default configuration values
 * and cross-version Adventure {@link Sound} resolution (compatible with both 1.21 enum and 1.21.3+ interface).
 */
public enum RuneSound {

    APPLY_SUCCESS("apply-success", "BLOCK_ENCHANTMENT_TABLE_USE", "block.enchantment_table.use", 1.0f, 1.2f),
    APPLY_FAIL("apply-fail", "ENTITY_VILLAGER_NO", "entity.villager.no", 1.0f, 1.0f),
    DEATH_TRIGGER("death-trigger", "BLOCK_AMETHYST_CLUSTER_BREAK", "block.amethyst_cluster.break", 1.2f, 0.8f),
    RESPAWN_RESTORE("respawn-restore", "ITEM_TOTEM_USE", "item.totem.use", 0.6f, 1.2f),
    REMOVE_SUCCESS("remove-success", "BLOCK_GRINDSTONE_USE", "block.grindstone.use", 1.0f, 1.0f);

    private final String configPath;
    private final String defaultBukkitName;
    private final Key defaultAdventureKey;
    private final float defaultVolume;
    private final float defaultPitch;

    RuneSound(String configPath, String defaultBukkitName, String defaultVanillaKey, float defaultVolume, float defaultPitch) {
        this.configPath = configPath;
        this.defaultBukkitName = defaultBukkitName;
        this.defaultAdventureKey = Key.key(Key.MINECRAFT_NAMESPACE, defaultVanillaKey);
        this.defaultVolume = defaultVolume;
        this.defaultPitch = defaultPitch;
    }

    public String configPath() {
        return configPath;
    }

    public String defaultBukkitName() {
        return defaultBukkitName;
    }

    public Key defaultAdventureKey() {
        return defaultAdventureKey;
    }

    public float defaultVolume() {
        return defaultVolume;
    }

    public float defaultPitch() {
        return defaultPitch;
    }

    public SoundEntry defaultEntry() {
        return new SoundEntry(true, defaultAdventureKey, defaultVolume, defaultPitch);
    }

    /**
     * Resolves a user-configured sound name (either Bukkit style like {@code BLOCK_ENCHANTMENT_TABLE_USE}
     * or resource key style like {@code minecraft:block.enchantment_table.use}) into an Adventure {@link Key}.
     * Returns {@code null} if the input cannot be resolved to a valid sound.
     */
    public static Key resolveSoundKey(String rawSound) {
        if (rawSound == null || rawSound.isBlank()) {
            return null;
        }
        String trimmed = rawSound.trim();
        if ("NONE".equalsIgnoreCase(trimmed) || "FALSE".equalsIgnoreCase(trimmed) || "OFF".equalsIgnoreCase(trimmed)) {
            return null;
        }

        // 1. Try resolving as a Bukkit Sound constant via reflection (safe across 1.21 enum and 1.21.3+ interface)
        String fieldCandidate = trimmed.toUpperCase(Locale.ROOT)
                .replace('.', '_')
                .replace('-', '_')
                .replace(' ', '_');
        if (fieldCandidate.startsWith("MINECRAFT:")) {
            fieldCandidate = fieldCandidate.substring("MINECRAFT:".length());
        }
        try {
            Field f = org.bukkit.Sound.class.getField(fieldCandidate);
            Object soundConstant = f.get(null);
            if (soundConstant instanceof Keyed keyed) {
                return keyed.key();
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }

        // 2. If written in dotted resource key format (e.g. "block.enchantment_table.use" or "minecraft:entity.villager.no")
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains(".") || lower.contains(":")) {
            String namespace = Key.MINECRAFT_NAMESPACE;
            String value = lower;
            int colon = lower.indexOf(':');
            if (colon >= 0) {
                namespace = lower.substring(0, colon);
                value = lower.substring(colon + 1);
            }
            if (Key.parseableNamespace(namespace) && Key.parseableValue(value)) {
                return Key.key(namespace, value);
            }
        }

        return null;
    }

    /**
     * Immutable resolved sound configuration ready to be played to a player or at a world location.
     */
    public record SoundEntry(boolean enabled, Key soundKey, float volume, float pitch) {

        public static final SoundEntry DISABLED = new SoundEntry(false, null, 0.0f, 1.0f);

        public void play(Player player) {
            if (!enabled || soundKey == null || player == null || volume <= 0.0f) {
                return;
            }
            try {
                Sound adventureSound = Sound.sound(soundKey, Sound.Source.PLAYER, volume, pitch);
                player.playSound(adventureSound, Sound.Emitter.self());
            } catch (Throwable ignored) {
            }
        }

        public void playAtLocation(Player player) {
            if (!enabled || soundKey == null || player == null || volume <= 0.0f) {
                return;
            }
            try {
                Sound adventureSound = Sound.sound(soundKey, Sound.Source.PLAYER, volume, pitch);
                World world = player.getWorld();
                Location loc = player.getLocation();
                if (world != null && loc != null) {
                    world.playSound(adventureSound, loc.getX(), loc.getY(), loc.getZ());
                } else {
                    player.playSound(adventureSound, Sound.Emitter.self());
                }
            } catch (Throwable ignored) {
            }
        }
    }
}

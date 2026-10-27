package com.ftl2nd.keepitemrune.type;

import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Defines configurable visual particle and entity effects for player death and respawn
 * when protected items are involved.
 */
public enum RuneEffect {

    DEATH("death", Particle.TOTEM_OF_UNDYING, 35, 0.4, 0.8, 0.4, 0.15, false),
    RESPAWN("respawn", Particle.END_ROD, 25, 0.4, 0.8, 0.4, 0.08, false);

    private final String configPath;
    private final Particle defaultParticle;
    private final int defaultCount;
    private final double defaultOffsetX;
    private final double defaultOffsetY;
    private final double defaultOffsetZ;
    private final double defaultSpeed;
    private final boolean defaultTotemAnimation;

    RuneEffect(
            String configPath,
            Particle defaultParticle,
            int defaultCount,
            double defaultOffsetX,
            double defaultOffsetY,
            double defaultOffsetZ,
            double defaultSpeed,
            boolean defaultTotemAnimation
    ) {
        this.configPath = configPath;
        this.defaultParticle = defaultParticle;
        this.defaultCount = defaultCount;
        this.defaultOffsetX = defaultOffsetX;
        this.defaultOffsetY = defaultOffsetY;
        this.defaultOffsetZ = defaultOffsetZ;
        this.defaultSpeed = defaultSpeed;
        this.defaultTotemAnimation = defaultTotemAnimation;
    }

    public String configPath() {
        return configPath;
    }

    public Particle defaultParticle() {
        return defaultParticle;
    }

    public int defaultCount() {
        return defaultCount;
    }

    public double defaultOffsetX() {
        return defaultOffsetX;
    }

    public double defaultOffsetY() {
        return defaultOffsetY;
    }

    public double defaultOffsetZ() {
        return defaultOffsetZ;
    }

    public double defaultSpeed() {
        return defaultSpeed;
    }

    public boolean defaultTotemAnimation() {
        return defaultTotemAnimation;
    }

    public EffectEntry defaultEntry() {
        return new EffectEntry(
                true,
                defaultParticle,
                defaultCount,
                defaultOffsetX,
                defaultOffsetY,
                defaultOffsetZ,
                defaultSpeed,
                defaultTotemAnimation
        );
    }

    /**
     * Resolves a user-configured particle name (Bukkit enum name or Minecraft resource key) into a {@link Particle}.
     * Returns {@code null} if invalid or disabled.
     */
    public static Particle resolveParticle(String rawParticle) {
        if (rawParticle == null || rawParticle.isBlank()) {
            return null;
        }
        String trimmed = rawParticle.trim();
        if ("NONE".equalsIgnoreCase(trimmed) || "FALSE".equalsIgnoreCase(trimmed) || "OFF".equalsIgnoreCase(trimmed)) {
            return null;
        }

        String normalized = trimmed.toUpperCase(Locale.ROOT)
                .replace('.', '_')
                .replace('-', '_')
                .replace(' ', '_');
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }

        normalized = switch (normalized) {
            case "TOTEM" -> "TOTEM_OF_UNDYING";
            case "REDSTONE" -> "DUST";
            case "VILLAGER_HAPPY" -> "HAPPY_VILLAGER";
            case "SPELL_WITCH" -> "WITCH";
            case "ENCHANTMENT_TABLE" -> "ENCHANT";
            default -> normalized;
        };

        try {
            return Particle.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
        }

        for (Particle p : Particle.values()) {
            try {
                if (p.getKey().getKey().equalsIgnoreCase(normalized.toLowerCase(Locale.ROOT))) {
                    return p;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * Immutable resolved visual effect configuration ready to be spawned for a player.
     */
    public record EffectEntry(
            boolean enabled,
            Particle particle,
            int count,
            double offsetX,
            double offsetY,
            double offsetZ,
            double speed,
            boolean totemAnimation
    ) {
        public static final EffectEntry DISABLED = new EffectEntry(false, null, 0, 0, 0, 0, 0, false);

        public void playAtPlayer(Player player) {
            if (!enabled || player == null) {
                return;
            }
            if (totemAnimation) {
                try {
                    player.playEffect(EntityEffect.PROTECTED_FROM_DEATH);
                } catch (Throwable ignored) {
                }
            }
            if (particle == null || count <= 0) {
                return;
            }
            World world = player.getWorld();
            Location loc = player.getLocation();
            if (world == null || loc == null) {
                return;
            }
            Location center = loc.clone().add(0.0, 1.0, 0.0);
            try {
                Class<?> dataType = particle.getDataType();
                if (dataType == Void.class) {
                    world.spawnParticle(particle, center, count, offsetX, offsetY, offsetZ, speed);
                } else if (dataType == Particle.DustOptions.class) {
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(192, 132, 252), 1.2f);
                    world.spawnParticle(particle, center, count, offsetX, offsetY, offsetZ, speed, dust);
                } else if (dataType == Color.class) {
                    world.spawnParticle(particle, center, count, offsetX, offsetY, offsetZ, speed, Color.fromRGB(192, 132, 252));
                } else if (dataType == Float.class) {
                    world.spawnParticle(particle, center, count, offsetX, offsetY, offsetZ, speed, 1.0f);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}

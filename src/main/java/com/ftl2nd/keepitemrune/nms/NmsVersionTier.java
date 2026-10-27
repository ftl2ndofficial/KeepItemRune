package com.ftl2nd.keepitemrune.nms;

import org.bukkit.Bukkit;

/**
 * Classifies the running server version across all 17 releases from 1.21 to 26.3.
 */
public enum NmsVersionTier {

    /**
     * Minecraft 1.21 and 1.21.1:
     * - Uses net.minecraft.resources.ResourceLocation
     * - ClientboundContainerSetContentPacket is a class with getter methods
     * - Does not have ClientboundSetCursorItemPacket or ClientboundSetPlayerInventoryPacket
     */
    TIER_1_21_0,

    /**
     * Minecraft 1.21.2 through 1.21.10:
     * - Uses net.minecraft.resources.ResourceLocation
     * - ClientboundContainerSetContentPacket is a record
     * - Adds ClientboundSetCursorItemPacket and ClientboundSetPlayerInventoryPacket
     */
    TIER_1_21_2,

    /**
     * Minecraft 1.21.11 through 26.3+:
     * - Renames ResourceLocation to net.minecraft.resources.Identifier
     * - ClientboundContainerSetContentPacket is a record
     * - Has ClientboundSetCursorItemPacket and ClientboundSetPlayerInventoryPacket
     * - Full 1.21.11 - 26.3 item registry (Spears, Copper equipment, Mace, etc.)
     */
    TIER_1_21_11_PLUS;

    private static volatile NmsVersionTier detectedTier;

    public static NmsVersionTier current() {
        NmsVersionTier tier = detectedTier;
        if (tier == null) {
            tier = detectRuntime();
            detectedTier = tier;
        }
        return tier;
    }

    public static NmsVersionTier fromVersionString(String rawVersion) {
        if (rawVersion == null || rawVersion.isBlank()) {
            return TIER_1_21_11_PLUS;
        }
        String clean = rawVersion.trim();
        int dashIdx = clean.indexOf('-');
        if (dashIdx > 0) {
            clean = clean.substring(0, dashIdx);
        }

        String[] parts = clean.split("\\.");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

            if (major >= 26) {
                return TIER_1_21_11_PLUS;
            }
            if (major == 1 && minor == 21) {
                if (patch <= 1) {
                    return TIER_1_21_0;
                }
                if (patch <= 10) {
                    return TIER_1_21_2;
                }
                return TIER_1_21_11_PLUS;
            }
            if (major == 1 && minor > 21) {
                return TIER_1_21_11_PLUS;
            }
        } catch (NumberFormatException ignored) {
        }
        return TIER_1_21_11_PLUS;
    }

    private static NmsVersionTier detectRuntime() {
        try {
            String mcVersion = Bukkit.getMinecraftVersion();
            return fromVersionString(mcVersion);
        } catch (Throwable ignored) {
            try {
                Class.forName("net.minecraft.resources.Identifier");
                return TIER_1_21_11_PLUS;
            } catch (ClassNotFoundException e) {
                try {
                    Class.forName("net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket");
                    return TIER_1_21_2;
                } catch (ClassNotFoundException ex) {
                    return TIER_1_21_0;
                }
            }
        }
    }

    public boolean usesIdentifierClass() {
        return this == TIER_1_21_11_PLUS;
    }

    public boolean hasDedicatedCursorAndPlayerInventoryPackets() {
        return this == TIER_1_21_2 || this == TIER_1_21_11_PLUS;
    }
}

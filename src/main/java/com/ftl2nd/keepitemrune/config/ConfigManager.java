package com.ftl2nd.keepitemrune.config;

import com.ftl2nd.keepitemrune.nms.NmsBridge;
import com.ftl2nd.keepitemrune.type.RuneEffect;
import com.ftl2nd.keepitemrune.type.RuneEffect.EffectEntry;
import com.ftl2nd.keepitemrune.type.RuneSound;
import com.ftl2nd.keepitemrune.type.RuneSound.SoundEntry;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages {@code config.yml} with dumb-proof type coercion, syntax recovery,
 * and automatic validation against invalid user edits.
 */
public final class ConfigManager {

    public static final Material DEFAULT_RUNE_MATERIAL = Material.AMETHYST_SHARD;
    public static final String DEFAULT_RUNE_NAME = "<gradient:#C084FC:#38BDF8><bold>Keep Item Rune</bold></gradient>";
    public static final List<String> DEFAULT_RUNE_LORE = List.of(
            "<gray>Drag and drop onto an item in your inventory</gray>",
            "<gray>to protect it from dropping on death.</gray>",
            "",
            "<#ffbe76>Consumed upon death.</#ffbe76>"
    );
    public static final String DEFAULT_PROTECTED_LORE = "<gradient:#C084FC:#38BDF8><bold>PROTECTED FROM DEATH</bold></gradient>";

    // Items introduced in 1.21.9+ / 1.21.11+ so older 1.21.x servers do not warn on the default whitelist
    private static final Set<String> FUTURE_VERSION_ITEMS = Set.of(
            "COPPER_SWORD", "COPPER_AXE", "COPPER_PICKAXE", "COPPER_SHOVEL", "COPPER_HOE", "COPPER_SPEAR",
            "COPPER_HELMET", "COPPER_CHESTPLATE", "COPPER_LEGGINGS", "COPPER_BOOTS",
            "WOODEN_SPEAR", "STONE_SPEAR", "IRON_SPEAR", "GOLDEN_SPEAR", "DIAMOND_SPEAR", "NETHERITE_SPEAR"
    );

    private final File configFile;
    private final Supplier<InputStream> defaultStreamSupplier;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private Material runeMaterial = DEFAULT_RUNE_MATERIAL;
    private Component runeName;
    private List<Component> runeLore = Collections.emptyList();
    private boolean runeGlow = true;

    private List<Component> protectedLore = Collections.emptyList();
    private final Set<String> whitelistItemKeys = new HashSet<>();
    private boolean allowAllItems = false;
    private final Map<RuneSound, SoundEntry> sounds = new EnumMap<>(RuneSound.class);
    private final Map<RuneEffect, EffectEntry> effects = new EnumMap<>(RuneEffect.class);

    public ConfigManager(JavaPlugin plugin) {
        this(
                new File(plugin.getDataFolder(), "config.yml"),
                () -> plugin.getResource("config.yml"),
                plugin.getLogger()
        );
    }

    public ConfigManager(File configFile, Supplier<InputStream> defaultStreamSupplier, Logger logger) {
        this.configFile = configFile;
        this.defaultStreamSupplier = defaultStreamSupplier;
        this.logger = logger;
        load();
    }

    public void load() {
        FileConfiguration config;
        try {
            config = YamlAutoUpdater.loadAndSync(configFile, defaultStreamSupplier.get(), logger);
        } catch (IOException e) {
            if (logger != null) {
                logger.log(Level.WARNING, "Failed to load or sync config.yml, using fallback configuration.", e);
            }
            config = YamlConfiguration.loadConfiguration(configFile);
        }

        parseConfiguration(config);
    }

    public void reload() {
        load();
    }

    void parseConfiguration(FileConfiguration config) {
        if (config.contains("rune") && !config.isConfigurationSection("rune")) {
            warn("Section 'rune' in config.yml is invalid (expected a section); using default rune settings.");
        }

        Object rawMatObj = config.isConfigurationSection("rune")
                ? config.get("rune.material")
                : config.get("voucher-material");
        this.runeMaterial = parseMaterialSafe(rawMatObj, DEFAULT_RUNE_MATERIAL);

        Object rawNameObj = config.isConfigurationSection("rune") ? config.get("rune.name") : null;
        String rawRuneName = parseScalarStringSafe(rawNameObj, "rune.name", DEFAULT_RUNE_NAME);
        if (rawRuneName.isBlank()) {
            rawRuneName = DEFAULT_RUNE_NAME;
        }
        this.runeName = deserializeNoItalic(rawRuneName);

        Object rawRuneLoreObj = config.isConfigurationSection("rune") ? config.get("rune.lore") : DEFAULT_RUNE_LORE;
        List<String> rawRuneLore = parseStringListSafe(rawRuneLoreObj, "rune.lore", DEFAULT_RUNE_LORE);
        List<Component> parsedRuneLore = new ArrayList<>(rawRuneLore.size());
        for (String line : rawRuneLore) {
            parsedRuneLore.add(deserializeNoItalic(line));
        }
        this.runeLore = Collections.unmodifiableList(parsedRuneLore);

        Object rawGlowObj = config.isConfigurationSection("rune") ? config.get("rune.glow") : null;
        this.runeGlow = parseBooleanSafe(rawGlowObj, "rune.glow", true);

        Object rawLoreObj = config.get("lore");
        List<String> rawProtectedLore = parseStringListSafe(rawLoreObj, "lore", List.of(DEFAULT_PROTECTED_LORE));
        List<Component> parsedProtectedLore = new ArrayList<>(rawProtectedLore.size());
        for (String line : rawProtectedLore) {
            if (!line.isEmpty() || rawProtectedLore.size() > 1) {
                parsedProtectedLore.add(deserializeNoItalic(line));
            }
        }
        this.protectedLore = Collections.unmodifiableList(parsedProtectedLore);

        parseSoundsSafe(config);
        parseEffectsSafe(config);
        parseWhitelistSafe(config.get("whitelist-items"));
    }

    private void parseSoundsSafe(FileConfiguration config) {
        sounds.clear();
        if (config.contains("sounds") && !config.isConfigurationSection("sounds")) {
            warn("Section 'sounds' in config.yml is invalid (expected a section); using default sound settings.");
            for (RuneSound rs : RuneSound.values()) {
                sounds.put(rs, rs.defaultEntry());
            }
            return;
        }

        ConfigurationSection soundsSec = config.getConfigurationSection("sounds");
        for (RuneSound rs : RuneSound.values()) {
            if (soundsSec == null || !soundsSec.contains(rs.configPath(), true)) {
                sounds.put(rs, rs.defaultEntry());
                continue;
            }

            String fullPath = "sounds." + rs.configPath();
            Object rawNode = soundsSec.get(rs.configPath());

            if (rawNode instanceof ConfigurationSection sec) {
                boolean enabled = parseBooleanSafe(sec.get("enabled"), fullPath + ".enabled", true);
                String rawSoundName = parseScalarStringSafe(sec.get("sound"), fullPath + ".sound", rs.defaultBukkitName());
                if ("NONE".equalsIgnoreCase(rawSoundName.trim()) || rawSoundName.isBlank()) {
                    sounds.put(rs, SoundEntry.DISABLED);
                    continue;
                }

                Key resolvedKey = RuneSound.resolveSoundKey(rawSoundName);
                if (resolvedKey == null) {
                    warn("Invalid sound '" + rawSoundName + "' at '" + fullPath + ".sound' in config.yml; falling back to "
                            + rs.defaultBukkitName() + ".");
                    resolvedKey = rs.defaultAdventureKey();
                }

                float volume = parseFloatSafe(sec.get("volume"), fullPath + ".volume", rs.defaultVolume(), 0.0f, 10.0f);
                float pitch = parseFloatSafe(sec.get("pitch"), fullPath + ".pitch", rs.defaultPitch(), 0.5f, 2.0f);
                sounds.put(rs, new SoundEntry(enabled, resolvedKey, volume, pitch));
            } else if (rawNode instanceof Boolean boolVal) {
                sounds.put(rs, boolVal ? rs.defaultEntry() : SoundEntry.DISABLED);
            } else if (rawNode instanceof String strVal) {
                String trimmed = strVal.trim();
                if (trimmed.isEmpty() || "NONE".equalsIgnoreCase(trimmed) || "FALSE".equalsIgnoreCase(trimmed) || "OFF".equalsIgnoreCase(trimmed)) {
                    sounds.put(rs, SoundEntry.DISABLED);
                } else {
                    Key resolvedKey = RuneSound.resolveSoundKey(trimmed);
                    if (resolvedKey == null) {
                        warn("Invalid sound '" + strVal + "' at '" + fullPath + "' in config.yml; falling back to "
                                + rs.defaultBukkitName() + ".");
                        resolvedKey = rs.defaultAdventureKey();
                    }
                    sounds.put(rs, new SoundEntry(true, resolvedKey, rs.defaultVolume(), rs.defaultPitch()));
                }
            } else {
                warn("Invalid type for '" + fullPath + "' in config.yml; using default sound.");
                sounds.put(rs, rs.defaultEntry());
            }
        }
    }

    private void parseEffectsSafe(FileConfiguration config) {
        effects.clear();
        if (config.contains("effects") && !config.isConfigurationSection("effects")) {
            warn("Section 'effects' in config.yml is invalid (expected a section); using default effect settings.");
            for (RuneEffect re : RuneEffect.values()) {
                effects.put(re, re.defaultEntry());
            }
            return;
        }

        ConfigurationSection effectsSec = config.getConfigurationSection("effects");
        for (RuneEffect re : RuneEffect.values()) {
            if (effectsSec == null || !effectsSec.contains(re.configPath(), true)) {
                effects.put(re, re.defaultEntry());
                continue;
            }

            String fullPath = "effects." + re.configPath();
            Object rawNode = effectsSec.get(re.configPath());

            if (rawNode instanceof ConfigurationSection sec) {
                boolean enabled = parseBooleanSafe(sec.get("enabled"), fullPath + ".enabled", true);
                String rawParticle = parseScalarStringSafe(sec.get("particle"), fullPath + ".particle", re.defaultParticle().name());
                boolean totemAnim = parseBooleanSafe(sec.get("totem-animation"), fullPath + ".totem-animation", re.defaultTotemAnimation());

                Particle resolvedParticle = null;
                if (!rawParticle.isBlank() && !"NONE".equalsIgnoreCase(rawParticle.trim())) {
                    resolvedParticle = RuneEffect.resolveParticle(rawParticle);
                    if (resolvedParticle == null || !isSupportedParticleDataType(resolvedParticle)) {
                        warn("Invalid or unsupported particle '" + rawParticle + "' at '" + fullPath + ".particle' in config.yml; falling back to "
                                + re.defaultParticle().name() + ".");
                        resolvedParticle = re.defaultParticle();
                    }
                }

                int count = (int) parseFloatSafe(sec.get("count"), fullPath + ".count", re.defaultCount(), 0.0f, 500.0f);
                double offsetX = parseFloatSafe(sec.get("offset-x"), fullPath + ".offset-x", (float) re.defaultOffsetX(), 0.0f, 10.0f);
                double offsetY = parseFloatSafe(sec.get("offset-y"), fullPath + ".offset-y", (float) re.defaultOffsetY(), 0.0f, 10.0f);
                double offsetZ = parseFloatSafe(sec.get("offset-z"), fullPath + ".offset-z", (float) re.defaultOffsetZ(), 0.0f, 10.0f);
                double speed = parseFloatSafe(sec.get("speed"), fullPath + ".speed", (float) re.defaultSpeed(), 0.0f, 10.0f);

                effects.put(re, new EffectEntry(enabled, resolvedParticle, count, offsetX, offsetY, offsetZ, speed, totemAnim));
            } else if (rawNode instanceof Boolean boolVal) {
                effects.put(re, boolVal ? re.defaultEntry() : EffectEntry.DISABLED);
            } else if (rawNode instanceof String strVal) {
                String trimmed = strVal.trim();
                if (trimmed.isEmpty() || "NONE".equalsIgnoreCase(trimmed) || "FALSE".equalsIgnoreCase(trimmed) || "OFF".equalsIgnoreCase(trimmed)) {
                    effects.put(re, EffectEntry.DISABLED);
                } else {
                    Particle resolved = RuneEffect.resolveParticle(trimmed);
                    if (resolved == null || !isSupportedParticleDataType(resolved)) {
                        warn("Invalid particle '" + strVal + "' at '" + fullPath + "' in config.yml; falling back to "
                                + re.defaultParticle().name() + ".");
                        resolved = re.defaultParticle();
                    }
                    effects.put(re, new EffectEntry(
                            true,
                            resolved,
                            re.defaultCount(),
                            re.defaultOffsetX(),
                            re.defaultOffsetY(),
                            re.defaultOffsetZ(),
                            re.defaultSpeed(),
                            re.defaultTotemAnimation()
                    ));
                }
            } else {
                warn("Invalid type for '" + fullPath + "' in config.yml; using default effect.");
                effects.put(re, re.defaultEntry());
            }
        }
    }

    private static boolean isSupportedParticleDataType(Particle particle) {
        Class<?> dt = particle.getDataType();
        return dt == Void.class || dt == Particle.DustOptions.class || dt == Color.class || dt == Float.class;
    }

    private float parseFloatSafe(Object rawObj, String path, float fallback, float min, float max) {
        if (rawObj == null) {
            return fallback;
        }
        float val;
        if (rawObj instanceof Number num) {
            val = num.floatValue();
        } else if (rawObj instanceof String str) {
            try {
                val = Float.parseFloat(str.trim());
            } catch (NumberFormatException e) {
                warn("Invalid number '" + str + "' for '" + path + "' in config.yml; using default (" + fallback + ").");
                return fallback;
            }
        } else {
            warn("Invalid type for '" + path + "' in config.yml (expected number); using default (" + fallback + ").");
            return fallback;
        }
        if (Float.isNaN(val) || Float.isInfinite(val)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, val));
    }

    private Material parseMaterialSafe(Object rawMatObj, Material fallback) {
        if (rawMatObj == null || rawMatObj instanceof ConfigurationSection || rawMatObj instanceof List<?>) {
            if (rawMatObj != null) {
                warn("Invalid type for 'rune.material' in config.yml; falling back to " + fallback.name() + ".");
            }
            return fallback;
        }

        String matName = String.valueOf(rawMatObj).trim();
        if (matName.isEmpty()) {
            warn("Empty 'rune.material' in config.yml; falling back to " + fallback.name() + ".");
            return fallback;
        }

        String normalized = matName.toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }

        Material resolved = Material.matchMaterial(normalized);
        if (resolved == null) {
            resolved = Material.matchMaterial(matName);
        }

        if (resolved == null || resolved.isAir() || !resolved.isItem()) {
            warn("Invalid or non-item rune material '" + matName + "' in config.yml; falling back to " + fallback.name() + ".");
            return fallback;
        }
        return resolved;
    }

    private String parseScalarStringSafe(Object rawObj, String path, String fallback) {
        if (rawObj == null) {
            return fallback;
        }
        if (rawObj instanceof ConfigurationSection) {
            warn("Invalid section at '" + path + "' in config.yml (expected a text string); using default.");
            return fallback;
        }
        if (rawObj instanceof List<?> list) {
            warn("Found a list at '" + path + "' in config.yml where a single string was expected; joining elements.");
            List<String> parts = new ArrayList<>();
            for (Object elem : list) {
                if (elem != null) {
                    parts.add(String.valueOf(elem));
                }
            }
            return parts.isEmpty() ? fallback : String.join(" ", parts);
        }
        return String.valueOf(rawObj);
    }

    private List<String> parseStringListSafe(Object rawObj, String path, List<String> fallback) {
        if (rawObj == null) {
            return fallback;
        }
        if (rawObj instanceof ConfigurationSection) {
            warn("Invalid section at '" + path + "' in config.yml (expected string or list); using default.");
            return fallback;
        }
        if (rawObj instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object elem : list) {
                if (elem != null && !(elem instanceof ConfigurationSection)) {
                    result.add(String.valueOf(elem));
                }
            }
            return result;
        }
        String single = String.valueOf(rawObj);
        if (single.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of(single);
    }

    private boolean parseBooleanSafe(Object rawObj, String path, boolean fallback) {
        if (rawObj == null) {
            return fallback;
        }
        if (rawObj instanceof Boolean b) {
            return b;
        }
        if (rawObj instanceof Number num) {
            return num.intValue() != 0;
        }
        if (rawObj instanceof String str) {
            String clean = str.trim().toLowerCase(Locale.ROOT);
            return switch (clean) {
                case "true", "yes", "on", "1", "enable", "enabled" -> true;
                case "false", "no", "off", "0", "disable", "disabled" -> false;
                default -> {
                    warn("Invalid boolean value '" + str + "' for '" + path + "' in config.yml; using default (" + fallback + ").");
                    yield fallback;
                }
            };
        }
        warn("Invalid type for '" + path + "' in config.yml (expected boolean); using default (" + fallback + ").");
        return fallback;
    }

    private void parseWhitelistSafe(Object rawWhitelistObj) {
        whitelistItemKeys.clear();
        allowAllItems = false;

        List<String> rawEntries = new ArrayList<>();
        if (rawWhitelistObj instanceof List<?> list) {
            for (Object elem : list) {
                if (elem != null && !(elem instanceof ConfigurationSection)) {
                    rawEntries.add(String.valueOf(elem));
                }
            }
        } else if (rawWhitelistObj instanceof String singleStr) {
            for (String split : singleStr.split(",")) {
                if (!split.isBlank()) {
                    rawEntries.add(split.trim());
                }
            }
        } else if (rawWhitelistObj != null) {
            warn("Invalid type for 'whitelist-items' in config.yml (expected a list of item names).");
            return;
        }

        for (String entry : rawEntries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();
            if ("*".equals(trimmed) || "ALL".equalsIgnoreCase(trimmed)) {
                allowAllItems = true;
                continue;
            }

            String normalized = trimmed.toUpperCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_');
            if (normalized.startsWith("MINECRAFT:")) {
                normalized = normalized.substring("MINECRAFT:".length());
            }

            if (!NmsBridge.isItemRegistered(normalized) && !FUTURE_VERSION_ITEMS.contains(normalized)) {
                warn("Unknown item '" + entry + "' in 'whitelist-items' of config.yml; ignoring.");
                continue;
            }
            whitelistItemKeys.add(normalized);
        }
    }

    private void warn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }

    public boolean isWhitelisted(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (allowAllItems) {
            return true;
        }
        String nmsKey = NmsBridge.resolveItemKey(item);
        if (whitelistItemKeys.contains(nmsKey)) {
            return true;
        }
        return whitelistItemKeys.contains(item.getType().name().toUpperCase(Locale.ROOT));
    }

    public SoundEntry getSound(RuneSound sound) {
        if (sound == null) {
            return SoundEntry.DISABLED;
        }
        return sounds.getOrDefault(sound, sound.defaultEntry());
    }

    public void playSound(Player player, RuneSound sound) {
        if (player == null || sound == null) {
            return;
        }
        getSound(sound).play(player);
    }

    public void playSoundAtLocation(Player player, RuneSound sound) {
        if (player == null || sound == null) {
            return;
        }
        getSound(sound).playAtLocation(player);
    }

    public EffectEntry getEffect(RuneEffect effect) {
        if (effect == null) {
            return EffectEntry.DISABLED;
        }
        return effects.getOrDefault(effect, effect.defaultEntry());
    }

    public void playEffect(Player player, RuneEffect effect) {
        if (player == null || effect == null) {
            return;
        }
        getEffect(effect).playAtPlayer(player);
    }

    public Material getRuneMaterial() {
        return runeMaterial;
    }

    public Component getRuneName() {
        return runeName;
    }

    public List<Component> getRuneLore() {
        return runeLore;
    }

    public boolean isRuneGlow() {
        return runeGlow;
    }

    public List<Component> getProtectedLore() {
        return protectedLore;
    }

    public Set<String> getWhitelistItemKeys() {
        return Collections.unmodifiableSet(whitelistItemKeys);
    }

    public boolean isAllItemsAllowed() {
        return allowAllItems;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    private Component deserializeNoItalic(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty().decoration(TextDecoration.ITALIC, false);
        }
        return miniMessage.deserialize(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}

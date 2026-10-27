package com.ftl2nd.keepitemrune.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Automatically updates and validates YAML configuration files on disk.
 * Includes dumb-proof recovery for malformed YAML syntax (creating a .bak backup and restoring defaults)
 * as well as automatic tab-indentation and BOM sanitization.
 */
public final class YamlAutoUpdater {

    private YamlAutoUpdater() {
    }

    /**
     * Loads and auto-updates a YAML file on disk against its bundled default resource.
     * If the file on disk has broken YAML syntax, backs it up as {@code <name>.broken-<timestamp>.bak}
     * and restores the default configuration so the plugin never crashes.
     */
    public static YamlConfiguration loadAndSync(File diskFile, InputStream defaultResourceStream, Logger logger) throws IOException {
        String defaultContent = "";
        if (defaultResourceStream != null) {
            try (InputStream in = defaultResourceStream) {
                defaultContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        YamlConfiguration defaultConfig = new YamlConfiguration();
        if (!defaultContent.isEmpty()) {
            try {
                defaultConfig.loadFromString(defaultContent);
            } catch (InvalidConfigurationException e) {
                if (logger != null) {
                    logger.warning("Bundled default configuration for " + diskFile.getName() + " could not be parsed: " + e.getMessage());
                }
            }
        }

        if (!diskFile.exists()) {
            File parent = diskFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.writeString(diskFile.toPath(), defaultContent, StandardCharsets.UTF_8);
            return defaultConfig;
        }

        String rawUserContent = Files.readString(diskFile.toPath(), StandardCharsets.UTF_8);
        String sanitizedUserContent = sanitizeRawYaml(rawUserContent, diskFile.getName(), logger);

        YamlConfiguration userConfig = new YamlConfiguration();
        try {
            userConfig.loadFromString(sanitizedUserContent);
        } catch (InvalidConfigurationException e) {
            String backupName = diskFile.getName() + ".broken-" + System.currentTimeMillis() + ".bak";
            File backupFile = new File(diskFile.getParentFile(), backupName);
            try {
                Files.copy(diskFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            if (logger != null) {
                logger.warning("Malformed YAML syntax detected in " + diskFile.getName() + " (" + e.getMessage().split("\n")[0] + "). "
                        + "Backed up broken file to " + backupName + " and restored default configuration.");
            }
            if (!defaultContent.isEmpty()) {
                Files.writeString(diskFile.toPath(), defaultContent, StandardCharsets.UTF_8);
            }
            return defaultConfig;
        }

        if (defaultContent.isEmpty()) {
            return userConfig;
        }

        int missingKeys = countMissingKeys(defaultConfig, userConfig);
        int defaultVersion = parseIntSafe(defaultConfig.get("config-version"), 1);
        int userVersion = parseIntSafe(userConfig.get("config-version"), 0);

        if (missingKeys > 0 || userVersion < defaultVersion || !rawUserContent.equals(sanitizedUserContent)) {
            String mergedContent = mergeWithComments(defaultContent, defaultConfig, userConfig, defaultVersion);
            Files.writeString(diskFile.toPath(), mergedContent, StandardCharsets.UTF_8);
            if (logger != null && missingKeys > 0) {
                logger.info("Updated " + diskFile.getName() + " with " + missingKeys + " new setting(s).");
            }
            YamlConfiguration reloaded = new YamlConfiguration();
            try {
                reloaded.loadFromString(mergedContent);
                return reloaded;
            } catch (InvalidConfigurationException ignored) {
                return userConfig;
            }
        }

        return userConfig;
    }

    public static int update(File diskFile, InputStream defaultResourceStream, Logger logger) throws IOException {
        if (defaultResourceStream == null) {
            return 0;
        }
        String defaultContent;
        try (InputStream in = defaultResourceStream) {
            defaultContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        if (!diskFile.exists()) {
            File parent = diskFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.writeString(diskFile.toPath(), defaultContent, StandardCharsets.UTF_8);
            return 0;
        }

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new StringReader(defaultContent));
        String rawUser = Files.readString(diskFile.toPath(), StandardCharsets.UTF_8);
        String sanitized = sanitizeRawYaml(rawUser, diskFile.getName(), logger);

        YamlConfiguration userConfig = new YamlConfiguration();
        try {
            userConfig.loadFromString(sanitized);
        } catch (InvalidConfigurationException e) {
            String backupName = diskFile.getName() + ".broken-" + System.currentTimeMillis() + ".bak";
            File backupFile = new File(diskFile.getParentFile(), backupName);
            Files.copy(diskFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (logger != null) {
                logger.warning("Malformed YAML in " + diskFile.getName() + ". Backed up to " + backupName + " and restored defaults.");
            }
            Files.writeString(diskFile.toPath(), defaultContent, StandardCharsets.UTF_8);
            return 0;
        }

        int missingKeys = countMissingKeys(defaultConfig, userConfig);
        int defaultVersion = parseIntSafe(defaultConfig.get("config-version"), 1);
        int userVersion = parseIntSafe(userConfig.get("config-version"), 0);

        if (missingKeys == 0 && userVersion >= defaultVersion) {
            return 0;
        }

        String mergedContent = mergeWithComments(defaultContent, defaultConfig, userConfig, defaultVersion);
        Files.writeString(diskFile.toPath(), mergedContent, StandardCharsets.UTF_8);

        if (logger != null && missingKeys > 0) {
            logger.info("Updated " + diskFile.getName() + " with " + missingKeys + " new setting(s).");
        }
        return missingKeys;
    }

    static String sanitizeRawYaml(String raw, String fileName, Logger logger) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String result = raw;
        if (result.startsWith("\uFEFF")) {
            result = result.substring(1);
        }
        if (result.indexOf('\t') >= 0) {
            if (logger != null) {
                logger.warning("Detected tab characters in " + fileName + "; automatically converting tabs to spaces.");
            }
            result = result.replace("\t", "  ");
        }
        return result;
    }

    private static int countMissingKeys(YamlConfiguration defaultConfig, YamlConfiguration userConfig) {
        int missing = 0;
        for (String key : defaultConfig.getKeys(true)) {
            if (defaultConfig.isConfigurationSection(key)) {
                continue;
            }
            if (!userConfig.contains(key, true) || userConfig.isConfigurationSection(key)) {
                missing++;
            }
        }
        return missing;
    }

    private static int parseIntSafe(Object val, int fallback) {
        if (val instanceof Number num) {
            return num.intValue();
        }
        if (val instanceof String str) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    static String mergeWithComments(
            String templateContent,
            YamlConfiguration defaultConfig,
            YamlConfiguration userConfig,
            int targetVersion
    ) {
        String[] lines = templateContent.replace("\r\n", "\n").split("\n", -1);
        List<String> output = new ArrayList<>(lines.length + 16);
        Map<Integer, String> pathStack = new LinkedHashMap<>();

        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                output.add(line);
                i++;
                continue;
            }

            if (trimmed.startsWith("-")) {
                i++;
                continue;
            }

            int colonIdx = line.indexOf(':');
            if (colonIdx <= 0) {
                output.add(line);
                i++;
                continue;
            }

            int indent = countLeadingSpaces(line);
            String rawKey = line.substring(indent, colonIdx).trim();
            pathStack.entrySet().removeIf(entry -> entry.getKey() >= indent);
            pathStack.put(indent, rawKey);

            String fullPath = buildPath(pathStack);
            String inlineComment = extractInlineComment(line.substring(colonIdx + 1));

            if (defaultConfig.isConfigurationSection(fullPath)) {
                output.add(line);
                i++;
                continue;
            }

            int nextIdx = i + 1;
            while (nextIdx < lines.length) {
                String nextLine = lines[nextIdx];
                String nextTrimmed = nextLine.trim();
                if (nextTrimmed.isEmpty() || nextTrimmed.startsWith("-")) {
                    if (nextTrimmed.isEmpty()) {
                        int lookAhead = nextIdx + 1;
                        while (lookAhead < lines.length && lines[lookAhead].trim().isEmpty()) {
                            lookAhead++;
                        }
                        if (lookAhead < lines.length && lines[lookAhead].trim().startsWith("-")) {
                            nextIdx = lookAhead;
                            continue;
                        }
                        break;
                    }
                    nextIdx++;
                } else {
                    break;
                }
            }

            Object valueToWrite;
            if ("config-version".equals(fullPath)) {
                valueToWrite = targetVersion;
            } else if (userConfig.contains(fullPath, true) && !userConfig.isConfigurationSection(fullPath)) {
                valueToWrite = userConfig.get(fullPath);
            } else {
                valueToWrite = defaultConfig.get(fullPath);
            }

            String indentStr = " ".repeat(indent);
            if (valueToWrite instanceof List<?> list) {
                if (list.isEmpty()) {
                    output.add(indentStr + rawKey + ": []" + inlineComment);
                } else {
                    output.add(indentStr + rawKey + ":" + inlineComment);
                    String itemIndent = " ".repeat(indent + 2);
                    for (Object elem : list) {
                        if (elem != null) {
                            output.add(itemIndent + "- " + formatScalar(elem));
                        }
                    }
                }
            } else {
                output.add(indentStr + rawKey + ": " + formatScalar(valueToWrite) + inlineComment);
            }

            i = nextIdx;
        }

        for (String topKey : userConfig.getKeys(false)) {
            ConfigurationSection userSec = userConfig.getConfigurationSection(topKey);
            ConfigurationSection defSec = defaultConfig.getConfigurationSection(topKey);
            if (userSec != null && defSec != null) {
                Set<String> userSubKeys = userSec.getKeys(false);
                for (String subKey : userSubKeys) {
                    if (!defSec.contains(subKey, true) && !userSec.isConfigurationSection(subKey)) {
                        Object extraVal = userSec.get(subKey);
                        output.add("  " + subKey + ": " + formatScalar(extraVal));
                    }
                }
            }
        }

        return String.join("\n", output);
    }

    private static int countLeadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String buildPath(Map<Integer, String> pathStack) {
        return String.join(".", pathStack.values());
    }

    private static String extractInlineComment(String afterColon) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < afterColon.length(); i++) {
            char c = afterColon.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle && (i == 0 || afterColon.charAt(i - 1) != '\\')) {
                inDouble = !inDouble;
            } else if (c == '#' && !inSingle && !inDouble && i > 0 && Character.isWhitespace(afterColon.charAt(i - 1))) {
                return " " + afterColon.substring(i).trim();
            }
        }
        return "";
    }

    private static String formatScalar(Object value) {
        if (value == null) {
            return "\"\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        String str = value.toString();
        String escaped = str
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }
}

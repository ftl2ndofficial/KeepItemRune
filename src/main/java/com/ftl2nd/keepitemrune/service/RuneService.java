package com.ftl2nd.keepitemrune.service;

import com.ftl2nd.keepitemrune.config.ConfigManager;
import com.ftl2nd.keepitemrune.type.ProtectionApplyResult;
import com.ftl2nd.keepitemrune.type.RuneTagKey;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/**
 * Manages creation of Keep Item Runes and PDC-based item protection state.
 * Server-side item lore is never modified; protection lore is injected client-side via Netty packets.
 */
public final class RuneService {

    public static final String NAMESPACE = RuneTagKey.NAMESPACE;
    public static final NamespacedKey RUNE_KEY = RuneTagKey.RUNE.namespacedKey();
    public static final NamespacedKey PROTECTED_KEY = RuneTagKey.PROTECTED.namespacedKey();
    public static final NamespacedKey PROTECTION_UUID_KEY = RuneTagKey.PROTECTION_UUID.namespacedKey();
    public static final NamespacedKey CLIENT_LORE_COUNT_KEY = RuneTagKey.CLIENT_LORE_COUNT.namespacedKey();

    private final ConfigManager configManager;

    public RuneService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public ItemStack createRuneItem(int amount) {
        int safeAmount = Math.max(1, Math.min(64, amount));
        ItemStack item = new ItemStack(configManager.getRuneMaterial(), safeAmount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Component name = configManager.getRuneName();
            meta.displayName(name);
            try {
                meta.itemName(name);
            } catch (NoSuchMethodError ignored) {
            }

            List<Component> lore = configManager.getRuneLore();
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }

            if (configManager.isRuneGlow()) {
                try {
                    meta.setEnchantmentGlintOverride(true);
                } catch (NoSuchMethodError ignored) {
                }
            }

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(RUNE_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Validates applying a cursor rune onto a target inventory item and returns a {@link ProtectionApplyResult}.
     */
    public ProtectionApplyResult validateCursorApply(GameMode gameMode, ItemStack cursorRune, ItemStack targetItem) {
        if (gameMode == GameMode.CREATIVE) {
            return ProtectionApplyResult.CREATIVE_MODE_BLOCKED;
        }
        if (targetItem == null || targetItem.getType().isAir()) {
            return ProtectionApplyResult.EMPTY_ITEM;
        }
        if (isRune(targetItem)) {
            return ProtectionApplyResult.CANNOT_PROTECT_RUNE;
        }
        if (!configManager.isWhitelisted(targetItem)) {
            return ProtectionApplyResult.ITEM_NOT_WHITELISTED;
        }
        if (cursorRune != null && cursorRune.getAmount() > 1) {
            return ProtectionApplyResult.STACKED_RUNE;
        }
        if (targetItem.getAmount() > 1) {
            return ProtectionApplyResult.STACKED_TARGET_ITEM;
        }
        if (isProtected(targetItem)) {
            return ProtectionApplyResult.ALREADY_PROTECTED;
        }
        return ProtectionApplyResult.SUCCESS;
    }

    /**
     * Validates applying protection to a held item via {@code /keepitemrune additem}.
     */
    public ProtectionApplyResult validateCommandProtect(ItemStack handItem) {
        if (handItem == null || handItem.getType().isAir()) {
            return ProtectionApplyResult.EMPTY_ITEM;
        }
        if (isRune(handItem)) {
            return ProtectionApplyResult.CANNOT_PROTECT_RUNE;
        }
        if (!configManager.isWhitelisted(handItem)) {
            return ProtectionApplyResult.ITEM_NOT_WHITELISTED;
        }
        if (handItem.getAmount() > 1) {
            return ProtectionApplyResult.STACKED_TARGET_ITEM;
        }
        if (isProtected(handItem)) {
            return ProtectionApplyResult.ALREADY_PROTECTED;
        }
        return ProtectionApplyResult.SUCCESS;
    }

    /**
     * Validates removing protection from a held item via {@code /keepitemrune removeitem}.
     */
    public ProtectionApplyResult validateCommandUnprotect(ItemStack handItem) {
        if (handItem == null || handItem.getType().isAir()) {
            return ProtectionApplyResult.EMPTY_ITEM;
        }
        if (!isProtected(handItem)) {
            return ProtectionApplyResult.NOT_PROTECTED;
        }
        return ProtectionApplyResult.UNPROTECT_SUCCESS;
    }

    public void protectItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(PROTECTED_KEY, PersistentDataType.BYTE, (byte) 1);
        pdc.set(PROTECTION_UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta);
    }

    public void unprotectItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(PROTECTED_KEY);
        pdc.remove(PROTECTION_UUID_KEY);
        pdc.remove(CLIENT_LORE_COUNT_KEY);
        item.setItemMeta(meta);
    }

    public boolean isRune(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte byteVal = pdc.get(RUNE_KEY, PersistentDataType.BYTE);
        if (byteVal != null && byteVal == 1) {
            return true;
        }
        String strVal = pdc.get(RUNE_KEY, PersistentDataType.STRING);
        return "true".equalsIgnoreCase(strVal);
    }

    public boolean isProtected(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte byteVal = pdc.get(PROTECTED_KEY, PersistentDataType.BYTE);
        if (byteVal != null && byteVal == 1) {
            return true;
        }
        String strVal = pdc.get(PROTECTED_KEY, PersistentDataType.STRING);
        return "true".equalsIgnoreCase(strVal);
    }

    public String getProtectionUuid(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(PROTECTION_UUID_KEY, PersistentDataType.STRING);
    }
}

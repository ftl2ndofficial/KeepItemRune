package com.ftl2nd.keepitemrune.service;

import com.ftl2nd.keepitemrune.config.ConfigManager;
import com.ftl2nd.keepitemrune.config.MessageKey;
import com.ftl2nd.keepitemrune.config.MessageManager;
import com.ftl2nd.keepitemrune.nms.NmsBridge;
import com.ftl2nd.keepitemrune.type.RuneEffect;
import com.ftl2nd.keepitemrune.type.RuneSound;
import com.ftl2nd.keepitemrune.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles saving protected items on player death and restoring them in their original
 * unprotected state upon respawn.
 *
 * <p>Design guarantees:
 * <ul>
 *   <li><b>No cross-session persistence:</b> If a player dies and quits the server before respawning
 *       (or is killed on combat-log quit by plugins like PvPManager), items are never saved across sessions
 *       to eliminate disconnect duplication vectors.</li>
 *   <li><b>Two-phase death interception (LOW -> HIGHEST -> MONITOR):</b> Protected items are extracted at
 *       {@code EventPriority.LOW} before PvPManager ({@code HIGH}) can transfer them to the killer
 *       ({@code DropMode.TRANSFER}) or clear drops ({@code DropMode.CLEAR}). At {@code MONITOR}, if
 *       PvPManager or another plugin set {@code keepInventory = true} or cancelled the death event,
 *       staged items are discarded so the player never receives duplicates.</li>
 *   <li><b>Instant respawn & multi-event safety:</b> Supports Paper's {@code PlayerPostRespawnEvent},
 *       {@code PlayerRespawnEvent}, {@code doImmediateRespawn}, and plugins calling {@code spigot().respawn()}
 *       inside {@code PlayerDeathEvent}, with retry ticks if the entity is still transitioning.</li>
 * </ul>
 */
public final class DeathRecoveryService {

    static final long DEATH_COOLDOWN_MS = 500L;
    private static final int MAX_RESTORE_ATTEMPTS = 10;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final RuneService runeService;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    private final Map<UUID, StagedDeathBatch> stagedDeaths = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> savedItems = new ConcurrentHashMap<>();
    private final Set<UUID> quittingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> deathCooldowns = new ConcurrentHashMap<>();

    public DeathRecoveryService(JavaPlugin plugin, RuneService runeService, ConfigManager configManager, MessageManager messageManager) {
        this(plugin, plugin != null ? plugin.getLogger() : Logger.getLogger("KeepItemRune"), runeService, configManager, messageManager);
    }

    public DeathRecoveryService(JavaPlugin plugin, Logger logger, RuneService runeService, ConfigManager configManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.logger = logger;
        this.runeService = runeService;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    /**
     * Phase 1 ({@code EventPriority.LOW}): Runs before PvPManager ({@code HIGH}) and grave plugins.
     * Extracts protected items from {@code event.getDrops()} into staging so third-party plugins
     * cannot transfer them to a killer or wipe them.
     */
    public void handleDeathLow(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        // If player is disconnecting (e.g. PvPManager KILL_ON_LOGOUT during PlayerQuitEvent),
        // do NOT save items for the quitting player; strip protection so dropped items shatter their rune.
        if (quittingPlayers.contains(uuid) || !player.isOnline() || player.hasMetadata("NPC")) {
            stripProtectionFromDrops(event.getDrops());
            return;
        }

        if (processingPlayers.contains(uuid) || event.getKeepInventory()) {
            return;
        }

        List<ItemStack> drops = event.getDrops();
        if (drops.isEmpty()) {
            return;
        }

        if (savedItems.containsKey(uuid)) {
            if (logger != null) {
                logger.warning("Player " + player.getName()
                        + " died while still holding unclaimed protected items; skipping to prevent duplication.");
            }
            return;
        }

        long now = System.currentTimeMillis();
        Long lastDeath = deathCooldowns.get(uuid);
        if (lastDeath != null && (now - lastDeath) < DEATH_COOLDOWN_MS) {
            if (logger != null) {
                logger.warning("Player " + player.getName()
                        + " died too quickly (" + (now - lastDeath) + "ms); skipping to prevent duplication.");
            }
            return;
        }

        StagedDeathBatch batch = new StagedDeathBatch(now);
        extractProtectedItems(player, drops, batch);

        if (!batch.items.isEmpty()) {
            stagedDeaths.put(uuid, batch);
        }
    }

    /**
     * Phase 2 ({@code EventPriority.HIGHEST}): Captures any protected items that another plugin
     * may have re-added to {@code event.getDrops()} between {@code LOW} and {@code HIGHEST}.
     */
    public void handleDeathHighest(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (quittingPlayers.contains(uuid) || !player.isOnline() || player.hasMetadata("NPC")) {
            stagedDeaths.remove(uuid);
            stripProtectionFromDrops(event.getDrops());
            return;
        }

        if (event.isCancelled() || event.getKeepInventory() || processingPlayers.contains(uuid)) {
            stagedDeaths.remove(uuid);
            return;
        }

        List<ItemStack> drops = event.getDrops();
        if (drops.isEmpty()) {
            return;
        }

        StagedDeathBatch batch = stagedDeaths.get(uuid);
        if (batch == null) {
            if (savedItems.containsKey(uuid)) {
                return;
            }
            long now = System.currentTimeMillis();
            Long lastDeath = deathCooldowns.get(uuid);
            if (lastDeath != null && (now - lastDeath) < DEATH_COOLDOWN_MS) {
                return;
            }
            batch = new StagedDeathBatch(now);
            extractProtectedItems(player, drops, batch);
            if (!batch.items.isEmpty()) {
                stagedDeaths.put(uuid, batch);
            }
        } else {
            extractProtectedItems(player, drops, batch);
        }
    }

    /**
     * Phase 3 ({@code EventPriority.MONITOR}): Commits staged items only if the death was not cancelled
     * and {@code keepInventory} was not enabled by a plugin such as PvPManager.
     */
    public void handleDeathMonitor(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        StagedDeathBatch batch = stagedDeaths.remove(uuid);
        if (batch == null || batch.items.isEmpty()) {
            return;
        }

        // If death was cancelled, or keepInventory was turned on by PvPManager (DropMode.KEEP / DROP)
        // or another plugin, the player keeps their inventory natively. Discard staged copies to prevent dupes.
        if (event.isCancelled() || event.getKeepInventory() || quittingPlayers.contains(uuid) || !player.isOnline()) {
            return;
        }

        savedItems.put(uuid, batch.items);
        deathCooldowns.put(uuid, batch.timestamp);

        if (configManager != null) {
            configManager.playSoundAtLocation(player, RuneSound.DEATH_TRIGGER);
            configManager.playEffect(player, RuneEffect.DEATH);
        }
    }

    /**
     * Convenience single-call entry point (equivalent to LOW + MONITOR when no other plugin intervenes).
     */
    public void handleDeath(PlayerDeathEvent event) {
        handleDeathLow(event);
        handleDeathHighest(event);
        handleDeathMonitor(event);
    }

    private void extractProtectedItems(Player player, List<ItemStack> drops, StagedDeathBatch batch) {
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (item == null || item.getType().isAir()) {
                continue;
            }

            if (runeService.isProtected(item)) {
                String protUuid = runeService.getProtectionUuid(item);
                if (protUuid != null && !batch.seenProtectionUuids.add(protUuid)) {
                    if (logger != null) {
                        logger.warning("Detected duplicate protected item UUID (" + protUuid
                                + ") on death of " + player.getName() + "; discarding duplicate protection.");
                    }
                    runeService.unprotectItem(item);
                    continue;
                }

                ItemStack restoredCopy = item.clone();
                runeService.unprotectItem(restoredCopy);
                batch.items.add(restoredCopy);
                iterator.remove();
            }
        }
    }

    private void stripProtectionFromDrops(List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) {
            return;
        }
        for (ItemStack item : drops) {
            if (item != null && !item.getType().isAir() && runeService.isProtected(item)) {
                runeService.unprotectItem(item);
            }
        }
    }

    /**
     * Schedules item restoration for a respawning player, with automatic retry ticks if the player
     * entity is still marked dead during transition.
     */
    public void scheduleRestore(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        promoteStagedIfPresent(uuid);

        if (!savedItems.containsKey(uuid) || quittingPlayers.contains(uuid)) {
            return;
        }
        if (!processingPlayers.add(uuid)) {
            return;
        }

        if (plugin == null) {
            try {
                restoreItemsNow(player);
            } finally {
                processingPlayers.remove(uuid);
            }
            return;
        }

        attemptDelayedRestore(player, 1);
    }

    private void attemptDelayedRestore(Player player, int attempt) {
        UUID uuid = player.getUniqueId();
        FoliaScheduler.runAtEntityLater(plugin, player, () -> {
            try {
                Player onlinePlayer = Bukkit.getPlayer(uuid);
                if (onlinePlayer == null || !onlinePlayer.isOnline() || quittingPlayers.contains(uuid)) {
                    savedItems.remove(uuid);
                    processingPlayers.remove(uuid);
                    return;
                }

                if (onlinePlayer.isDead() || onlinePlayer.getHealth() <= 0.0) {
                    if (attempt < MAX_RESTORE_ATTEMPTS) {
                        attemptDelayedRestore(onlinePlayer, attempt + 1);
                    } else {
                        processingPlayers.remove(uuid);
                    }
                    return;
                }

                try {
                    restoreItemsNow(onlinePlayer);
                } finally {
                    processingPlayers.remove(uuid);
                }
            } catch (Throwable t) {
                processingPlayers.remove(uuid);
                throw t;
            }
        }, 1L);
    }

    /**
     * Restores any saved items to the given alive online player immediately.
     *
     * @return {@code true} if at least one item was restored
     */
    public boolean restoreItemsNow(Player onlinePlayer) {
        if (onlinePlayer == null) {
            return false;
        }
        UUID uuid = onlinePlayer.getUniqueId();
        promoteStagedIfPresent(uuid);

        if (quittingPlayers.contains(uuid) || !onlinePlayer.isOnline()) {
            savedItems.remove(uuid);
            return false;
        }
        if (onlinePlayer.isDead()) {
            return false;
        }

        List<ItemStack> items = savedItems.remove(uuid);
        if (items == null || items.isEmpty()) {
            return false;
        }

        boolean anyReturned = false;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            runeService.unprotectItem(item);
            Map<Integer, ItemStack> leftover = onlinePlayer.getInventory().addItem(item);
            if (leftover != null && !leftover.isEmpty() && onlinePlayer.getWorld() != null) {
                for (ItemStack drop : leftover.values()) {
                    if (drop != null && !drop.getType().isAir()) {
                        onlinePlayer.getWorld().dropItemNaturally(onlinePlayer.getLocation(), drop);
                    }
                }
            }
            anyReturned = true;
        }

        if (anyReturned) {
            NmsBridge.syncPlayerContainer(onlinePlayer);
            if (plugin != null) {
                FoliaScheduler.runAtEntityLater(plugin, onlinePlayer, () -> {
                    if (onlinePlayer.isOnline() && !onlinePlayer.isDead()) {
                        NmsBridge.syncPlayerContainer(onlinePlayer);
                    }
                }, 2L);
            }
            if (configManager != null) {
                configManager.playSound(onlinePlayer, RuneSound.RESPAWN_RESTORE);
                configManager.playEffect(onlinePlayer, RuneEffect.RESPAWN);
            }
            messageManager.send(onlinePlayer, MessageKey.PLAYER_RESPAWN_WITH_ITEM);
        }
        return anyReturned;
    }

    private void promoteStagedIfPresent(UUID uuid) {
        StagedDeathBatch batch = stagedDeaths.remove(uuid);
        if (batch != null && !batch.items.isEmpty()) {
            savedItems.putIfAbsent(uuid, batch.items);
            deathCooldowns.put(uuid, batch.timestamp);
        }
    }

    /**
     * Called at {@code EventPriority.LOWEST} on {@code PlayerQuitEvent} / {@code PlayerKickEvent}
     * before combat-log plugins like PvPManager ({@code EventPriority.NORMAL}) execute logout kills.
     */
    public void handleQuitStart(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        quittingPlayers.add(uuid);
        stagedDeaths.remove(uuid);
        savedItems.remove(uuid);
    }

    /**
     * Called at {@code EventPriority.MONITOR} on {@code PlayerQuitEvent} and on join cleanup.
     * Clears all in-memory state for the player.
     */
    public void handleQuitEnd(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        quittingPlayers.remove(uuid);
        stagedDeaths.remove(uuid);
        savedItems.remove(uuid);
        deathCooldowns.remove(uuid);
        processingPlayers.remove(uuid);
    }

    public void handleJoin(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        quittingPlayers.remove(uuid);
        stagedDeaths.remove(uuid);
        savedItems.remove(uuid);
        processingPlayers.remove(uuid);
    }

    public void clearAll() {
        stagedDeaths.clear();
        savedItems.clear();
        quittingPlayers.clear();
        deathCooldowns.clear();
        processingPlayers.clear();
    }

    public boolean hasSavedItems(UUID uuid) {
        return savedItems.containsKey(uuid);
    }

    public List<ItemStack> getSavedItemsView(UUID uuid) {
        List<ItemStack> list = savedItems.get(uuid);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    private static final class StagedDeathBatch {
        final long timestamp;
        final List<ItemStack> items = new ArrayList<>();
        final Set<String> seenProtectionUuids = new HashSet<>();

        StagedDeathBatch(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}

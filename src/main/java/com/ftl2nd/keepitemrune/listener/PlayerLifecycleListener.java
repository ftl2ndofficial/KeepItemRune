package com.ftl2nd.keepitemrune.listener;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.ftl2nd.keepitemrune.config.ConfigManager;
import com.ftl2nd.keepitemrune.nms.PacketLoreInjector;
import com.ftl2nd.keepitemrune.service.DeathRecoveryService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Coordinates player lifecycle events for Netty packet lore injection and two-phase death/respawn recovery.
 */
public final class PlayerLifecycleListener implements Listener {

    private final ConfigManager configManager;
    private final DeathRecoveryService deathRecoveryService;

    public PlayerLifecycleListener(ConfigManager configManager, DeathRecoveryService deathRecoveryService) {
        this.configManager = configManager;
        this.deathRecoveryService = deathRecoveryService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        deathRecoveryService.handleJoin(event.getPlayer());
        PacketLoreInjector.injectPlayer(event.getPlayer(), configManager);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerKickLowest(PlayerKickEvent event) {
        deathRecoveryService.handleQuitStart(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKickMonitor(PlayerKickEvent event) {
        if (event.isCancelled()) {
            deathRecoveryService.handleJoin(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuitLowest(PlayerQuitEvent event) {
        deathRecoveryService.handleQuitStart(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuitMonitor(PlayerQuitEvent event) {
        PacketLoreInjector.uninjectPlayer(event.getPlayer());
        deathRecoveryService.handleQuitEnd(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerDeathLow(PlayerDeathEvent event) {
        deathRecoveryService.handleDeathLow(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeathHighest(PlayerDeathEvent event) {
        deathRecoveryService.handleDeathHighest(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeathMonitor(PlayerDeathEvent event) {
        deathRecoveryService.handleDeathMonitor(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        deathRecoveryService.scheduleRestore(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        deathRecoveryService.scheduleRestore(event.getPlayer());
    }
}

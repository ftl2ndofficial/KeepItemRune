package com.ftl2nd.keepitemrune;

import com.ftl2nd.keepitemrune.command.KeepItemRuneCommand;
import com.ftl2nd.keepitemrune.config.ConfigManager;
import com.ftl2nd.keepitemrune.config.MessageManager;
import com.ftl2nd.keepitemrune.listener.PlayerLifecycleListener;
import com.ftl2nd.keepitemrune.listener.RuneInteractListener;
import com.ftl2nd.keepitemrune.nms.NmsVersionTier;
import com.ftl2nd.keepitemrune.nms.PacketLoreInjector;
import com.ftl2nd.keepitemrune.service.DeathRecoveryService;
import com.ftl2nd.keepitemrune.service.RuneService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class KeepItemRune extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private RuneService runeService;
    private DeathRecoveryService deathRecoveryService;

    @Override
    public void onEnable() {
        NmsVersionTier tier = NmsVersionTier.current();
        getLogger().info("Detected NMS adapter tier: " + tier.name() + " (Minecraft " + Bukkit.getMinecraftVersion() + ")");

        this.configManager = new ConfigManager(this);
        this.messageManager = new MessageManager(this);
        this.runeService = new RuneService(configManager);
        this.deathRecoveryService = new DeathRecoveryService(this, runeService, configManager, messageManager);

        KeepItemRuneCommand commandHandler = new KeepItemRuneCommand(this, configManager, messageManager, runeService);
        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("keepitemrune"), "Command 'keepitemrune' is missing from plugin.yml");
        pluginCommand.setExecutor(commandHandler);
        pluginCommand.setTabCompleter(commandHandler);

        getServer().getPluginManager().registerEvents(new RuneInteractListener(runeService, configManager, messageManager), this);
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(configManager, deathRecoveryService), this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            PacketLoreInjector.injectPlayer(player, configManager);
        }

        getLogger().info("KeepItemRune enabled successfully (" + KeepItemRuneCommand.DISCORD_URL + ").");
    }

    @Override
    public void onDisable() {
        if (deathRecoveryService != null) {
            deathRecoveryService.clearAll();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PacketLoreInjector.uninjectPlayer(player);
        }
        getLogger().info("KeepItemRune disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public RuneService getRuneService() {
        return runeService;
    }

    public DeathRecoveryService getDeathRecoveryService() {
        return deathRecoveryService;
    }
}

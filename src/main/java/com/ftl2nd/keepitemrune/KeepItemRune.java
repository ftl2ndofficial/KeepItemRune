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
import com.ftl2nd.keepitemrune.util.FoliaScheduler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class KeepItemRune extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 34979;
    private static final MiniMessage MM = MiniMessage.miniMessage();

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

        try {
            new Metrics(this, BSTATS_PLUGIN_ID);
        } catch (Exception e) {
            getLogger().warning("Failed to initialize bStats metrics.");
        }

        logBanner("");
        logBanner("<gradient:#C084FC:#38BDF8> __  __     __     ______    </gradient>");
        logBanner("<gradient:#C084FC:#38BDF8>/\\ \\/ /    /\\ \\   /\\  == \\   </gradient>");
        logBanner("<gradient:#C084FC:#38BDF8>\\ \\  _\"-.  \\ \\ \\  \\ \\  __<   </gradient>");
        logBanner("<gradient:#C084FC:#38BDF8> \\ \\_\\ \\_\\  \\ \\_\\  \\ \\_\\ \\_\\ </gradient>");
        logBanner("<gradient:#C084FC:#38BDF8>  \\/_/\\/_/   \\/_/   \\/_/ /_/ </gradient>");
        logBanner("");
        logBanner("<gray>Version: <white>" + getPluginMeta().getVersion() + "</white> <dark_gray>|</dark_gray> Author: <white>FTL2nd</white></gray>");
        logBanner("<gray>Website: <#38BDF8>" + KeepItemRuneCommand.DISCORD_URL + "</#38BDF8></gray>");
        logBanner("<gray>Platform: <#ffbe76>" + (FoliaScheduler.isFolia() ? "Folia" : "Paper/Spigot") + "</#ffbe76> <dark_gray>|</dark_gray> Status: <#55efc4>Enabled</#55efc4></gray>");
        logBanner("");
    }

    private void logBanner(String miniMessageLine) {
        Bukkit.getConsoleSender().sendMessage(MM.deserialize(miniMessageLine));
    }

    @Override
    public void onDisable() {
        if (deathRecoveryService != null) {
            deathRecoveryService.clearAll();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PacketLoreInjector.uninjectPlayer(player);
        }
        getLogger().info("KeepItemRune has been disabled.");
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

package com.ftl2nd.keepitemrune.command;

import com.ftl2nd.keepitemrune.config.ConfigManager;
import com.ftl2nd.keepitemrune.config.MessageKey;
import com.ftl2nd.keepitemrune.config.MessageManager;
import com.ftl2nd.keepitemrune.nms.NmsBridge;
import com.ftl2nd.keepitemrune.service.RuneService;
import com.ftl2nd.keepitemrune.type.ProtectionApplyResult;
import com.ftl2nd.keepitemrune.type.RunePermission;
import com.ftl2nd.keepitemrune.util.FoliaScheduler;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class KeepItemRuneCommand implements CommandExecutor, TabCompleter {

    public static final String DISCORD_URL = "https://discord.knualabs.icu";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final RuneService runeService;

    public KeepItemRuneCommand(
            JavaPlugin plugin,
            ConfigManager configManager,
            MessageManager messageManager,
            RuneService runeService
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.runeService = runeService;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        Optional<SubCommand> resolved = SubCommand.fromInput(args[0]);
        if (resolved.isEmpty()) {
            sendHelp(sender);
            return true;
        }

        SubCommand subCommand = resolved.get();
        if (!subCommand.permission().has(sender)) {
            messageManager.send(sender, MessageKey.NO_PERMISSION);
            return true;
        }

        if (subCommand.isPlayerOnly() && !(sender instanceof Player)) {
            messageManager.send(sender, MessageKey.NOT_PLAYER);
            return true;
        }

        switch (subCommand) {
            case RELOAD -> {
                configManager.reload();
                messageManager.reload();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    NmsBridge.syncPlayerContainer(online);
                }
                messageManager.send(sender, MessageKey.RELOAD_CONFIG_SUCCESS);
                if (plugin != null) {
                    plugin.getLogger().info("Configuration reloaded successfully.");
                }
            }

            case GIVE_RUNE -> {
                if (args.length < 2) {
                    messageManager.send(sender, MessageKey.NO_PLAYER_SELECTED);
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messageManager.send(sender, MessageKey.PLAYER_NOT_FOUND);
                    return true;
                }

                int amount = 1;
                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                        if (amount <= 0) {
                            messageManager.send(sender, MessageKey.AMOUNT_LOWER_THAN_ONE);
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        messageManager.send(sender, MessageKey.INVALID_AMOUNT);
                        return true;
                    }
                }

                final int totalAmount = amount;
                Runnable giveTask = () -> {
                    int remaining = totalAmount;
                    while (remaining > 0) {
                        int batch = Math.min(remaining, 64);
                        ItemStack runeStack = runeService.createRuneItem(batch);
                        Map<Integer, ItemStack> leftover = target.getInventory().addItem(runeStack);
                        for (ItemStack drop : leftover.values()) {
                            target.getWorld().dropItemNaturally(target.getLocation(), drop);
                        }
                        remaining -= batch;
                    }
                    NmsBridge.syncPlayerContainer(target);
                };

                if (plugin != null) {
                    FoliaScheduler.runAtEntity(plugin, target, giveTask);
                } else {
                    giveTask.run();
                }

                messageManager.send(
                        sender,
                        MessageKey.GIVE_RUNE_SUCCESS,
                        Placeholder.parsed("amount", String.valueOf(amount)),
                        Placeholder.parsed("player", target.getName())
                );
            }

            case ADD_ITEM -> {
                Player player = (Player) sender;
                ItemStack item = player.getInventory().getItemInMainHand();
                ProtectionApplyResult result = runeService.validateCommandProtect(item);
                if (!result.isSuccess()) {
                    configManager.playSound(player, result.sound());
                    messageManager.send(player, result.messageKey());
                    return true;
                }

                runeService.protectItem(item);
                NmsBridge.syncPlayerContainer(player);
                configManager.playSound(player, result.sound());
                messageManager.send(player, result.messageKey());
            }

            case REMOVE_ITEM -> {
                Player player = (Player) sender;
                ItemStack item = player.getInventory().getItemInMainHand();
                ProtectionApplyResult result = runeService.validateCommandUnprotect(item);
                if (!result.isSuccess()) {
                    configManager.playSound(player, result.sound());
                    messageManager.send(player, result.messageKey());
                    return true;
                }

                runeService.unprotectItem(item);
                NmsBridge.syncPlayerContainer(player);
                configManager.playSound(player, result.sound());
                messageManager.send(player, result.messageKey());
            }
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(messageManager.getMiniMessage().deserialize(
                "<newline><gradient:#23FABE:#AEFFE8><bold>KeepItemRune</bold></gradient> <gray>- by FTL2nd ("
                        + "<click:open_url:'" + DISCORD_URL + "'><underlined>" + DISCORD_URL + "</underlined></click>)</gray>"
        ));

        if (RunePermission.hasAnyCommandPermission(sender)) {
            StringBuilder helpBuilder = new StringBuilder("<newline><white>Available commands:</white>");
            for (SubCommand cmd : SubCommand.values()) {
                if (cmd.permission().has(sender)) {
                    helpBuilder.append("<newline><gray>")
                            .append(cmd.usage())
                            .append("</gray> <white>- ")
                            .append(cmd.description())
                            .append("</white>");
                }
            }
            sender.sendMessage(messageManager.getMiniMessage().deserialize(helpBuilder.toString()));
        }
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();
            for (SubCommand cmd : SubCommand.values()) {
                if (cmd.permission().has(sender) && cmd.primaryName().startsWith(prefix)) {
                    completions.add(cmd.primaryName());
                }
            }
            return completions;
        }

        if (args.length == 2) {
            Optional<SubCommand> sub = SubCommand.fromInput(args[0]);
            if (sub.isPresent() && sub.get() == SubCommand.GIVE_RUNE && SubCommand.GIVE_RUNE.permission().has(sender)) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return sender.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
        }

        if (args.length == 3) {
            Optional<SubCommand> sub = SubCommand.fromInput(args[0]);
            if (sub.isPresent() && sub.get() == SubCommand.GIVE_RUNE && SubCommand.GIVE_RUNE.permission().has(sender)) {
                List<String> suggestions = List.of("1", "5", "10", "16", "32", "64");
                return suggestions.stream()
                        .filter(n -> n.startsWith(args[2]))
                        .toList();
            }
        }

        return Collections.emptyList();
    }
}

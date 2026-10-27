package com.ftl2nd.keepitemrune.nms;

import com.ftl2nd.keepitemrune.config.ConfigManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

import java.util.NoSuchElementException;

/**
 * Netty pipeline handler that injects protection lore on outgoing item packets
 * (client-side only) and strips it from incoming creative slot packets.
 */
public final class PacketLoreInjector extends ChannelDuplexHandler {

    public static final String HANDLER_NAME = "keepitemrune_lore_injector";

    private final ConfigManager configManager;

    public PacketLoreInjector(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            msg = NmsBridge.transformOutgoingPacket(msg, configManager.getProtectedLore());
        } catch (Throwable ignored) {
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            msg = NmsBridge.transformIncomingPacket(msg);
        } catch (Throwable ignored) {
        }
        super.channelRead(ctx, msg);
    }

    public static void injectPlayer(Player player, ConfigManager configManager) {
        Channel channel = NmsBridge.getNettyChannel(player);
        if (channel == null) {
            return;
        }
        channel.eventLoop().execute(() -> {
            try {
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.get(HANDLER_NAME) != null) {
                    pipeline.remove(HANDLER_NAME);
                }
                if (pipeline.get("packet_handler") != null) {
                    pipeline.addBefore("packet_handler", HANDLER_NAME, new PacketLoreInjector(configManager));
                }
            } catch (NoSuchElementException | IllegalArgumentException ignored) {
            }
        });
    }

    public static void uninjectPlayer(Player player) {
        Channel channel = NmsBridge.getNettyChannel(player);
        if (channel == null) {
            return;
        }
        channel.eventLoop().execute(() -> {
            try {
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.get(HANDLER_NAME) != null) {
                    pipeline.remove(HANDLER_NAME);
                }
            } catch (NoSuchElementException | IllegalArgumentException ignored) {
            }
        });
    }
}

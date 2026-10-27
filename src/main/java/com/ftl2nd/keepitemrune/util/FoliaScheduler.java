package com.ftl2nd.keepitemrune.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class FoliaScheduler {

    private static final boolean IS_FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
        }
        IS_FOLIA = folia;
    }

    private FoliaScheduler() {
    }

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public static void runAtEntity(Plugin plugin, Entity entity, Runnable task) {
        try {
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        } catch (UnsupportedOperationException | NoSuchMethodError e) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runAtEntityLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        try {
            entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, Math.max(1L, delayTicks));
        } catch (UnsupportedOperationException | NoSuchMethodError e) {
            Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
        }
    }
}

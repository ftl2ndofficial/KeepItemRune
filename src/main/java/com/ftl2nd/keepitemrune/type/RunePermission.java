package com.ftl2nd.keepitemrune.type;

import org.bukkit.command.CommandSender;

/**
 * Defines all permission nodes for KeepItemRune.
 */
public enum RunePermission {

    ADMIN("keepitemrune.admin"),
    RELOAD("keepitemrune.reload"),
    GIVE_RUNE("keepitemrune.giverune"),
    ADD_ITEM("keepitemrune.additem"),
    REMOVE_ITEM("keepitemrune.removeitem");

    private final String node;

    RunePermission(String node) {
        this.node = node;
    }

    public String node() {
        return node;
    }

    /**
     * Checks whether the given sender holds this permission or the overarching {@link #ADMIN} permission.
     */
    public boolean has(CommandSender sender) {
        if (sender == null) {
            return false;
        }
        return sender.hasPermission(node) || sender.hasPermission(ADMIN.node);
    }

    public static boolean hasAnyCommandPermission(CommandSender sender) {
        if (sender == null) {
            return false;
        }
        for (RunePermission perm : values()) {
            if (sender.hasPermission(perm.node)) {
                return true;
            }
        }
        return false;
    }
}

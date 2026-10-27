package com.ftl2nd.keepitemrune.listener;

import com.ftl2nd.keepitemrune.config.ConfigManager;
import com.ftl2nd.keepitemrune.config.MessageManager;
import com.ftl2nd.keepitemrune.nms.NmsBridge;
import com.ftl2nd.keepitemrune.service.RuneService;
import com.ftl2nd.keepitemrune.type.ProtectionApplyResult;
import com.ftl2nd.keepitemrune.type.RuneSound;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

public final class RuneInteractListener implements Listener {

    private final RuneService runeService;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public RuneInteractListener(RuneService runeService, ConfigManager configManager, MessageManager messageManager) {
        this.runeService = runeService;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapWithCursor(InventoryClickEvent event) {
        if (event.getAction() != InventoryAction.SWAP_WITH_CURSOR) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack currentItem = event.getCurrentItem();

        if (cursor == null || currentItem == null || cursor.getType().isAir() || currentItem.getType().isAir()) {
            return;
        }

        if (!runeService.isRune(cursor)) {
            return;
        }

        if (event.getClickedInventory() == null || event.getClickedInventory().getType() != InventoryType.PLAYER) {
            event.setCancelled(true);
            NmsBridge.syncPlayerContainer(player);
            configManager.playSound(player, RuneSound.APPLY_FAIL);
            return;
        }

        ProtectionApplyResult result = runeService.validateCursorApply(player.getGameMode(), cursor, currentItem);
        event.setCancelled(true);

        if (!result.isSuccess()) {
            NmsBridge.syncPlayerContainer(player);
            configManager.playSound(player, result.sound());
            messageManager.send(player, result.messageKey());
            return;
        }

        player.setItemOnCursor(null);
        runeService.protectItem(currentItem);
        NmsBridge.syncPlayerContainer(player);
        configManager.playSound(player, result.sound());
        messageManager.send(player, result.messageKey());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHotbarSwap(InventoryClickEvent event) {
        if (event.getAction() != InventoryAction.HOTBAR_SWAP
                && event.getClick() != ClickType.NUMBER_KEY) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int hotbarButton = event.getHotbarButton();
        if (hotbarButton < 0) {
            return;
        }

        ItemStack hotbarItem = player.getInventory().getItem(hotbarButton);
        ItemStack slotItem = event.getCurrentItem();

        if (event.getClickedInventory() != null && event.getClickedInventory().getType() != InventoryType.PLAYER) {
            if ((hotbarItem != null && runeService.isRune(hotbarItem))
                    || (slotItem != null && runeService.isRune(slotItem))) {
                event.setCancelled(true);
                NmsBridge.syncPlayerContainer(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (runeService.isRune(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack matrixItem : event.getInventory().getMatrix()) {
            if (matrixItem != null && runeService.isRune(matrixItem)) {
                event.getInventory().setResult(new ItemStack(Material.AIR));
                return;
            }
        }
    }
}

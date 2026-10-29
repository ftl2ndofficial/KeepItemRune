package com.ftl2nd.keepitemrune.nms;

import com.ftl2nd.keepitemrune.type.RuneTagKey;
import com.mojang.datafixers.util.Pair;
import io.netty.channel.Channel;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Multi-version NMS bridge supporting Minecraft 1.21 through 26.3.
 * Handles direct DataComponents manipulation, item registry inspection,
 * container state synchronization, and client-side packet lore transformation.
 */
public final class NmsBridge {

    public static final String PUBLIC_BUKKIT_VALUES = "PublicBukkitValues";
    public static final String RUNE_KEY = RuneTagKey.RUNE.fullKey();
    public static final String PROTECTED_KEY = RuneTagKey.PROTECTED.fullKey();
    public static final String PROTECTION_UUID_KEY = RuneTagKey.PROTECTION_UUID.fullKey();
    public static final String CLIENT_LORE_MARKER_KEY = RuneTagKey.CLIENT_LORE_COUNT.fullKey();

    private static Field listenerConnectionField;
    private static Field networkChannelField;

    // Cross-tier reflection handles for 1.21 - 26.3 differences
    private static Method parseResourceKeyMethod;
    private static Method registryContainsKeyMethod;
    private static Method registryGetKeyMethod;

    private static Method setContentContainerIdGetter;
    private static Method setContentStateIdGetter;
    private static Method setContentItemsGetter;
    private static Method setContentCarriedGetter;
    private static Constructor<?> setContentRecordCtor;
    private static Constructor<?> setContentLegacyCtor;

    private static Class<?> setCursorPacketClass;
    private static Method setCursorContentsGetter;
    private static Constructor<?> setCursorCtor;

    private static Class<?> setPlayerInvPacketClass;
    private static Method setPlayerInvSlotGetter;
    private static Method setPlayerInvContentsGetter;
    private static Constructor<?> setPlayerInvCtor;

    private static Method compoundTagRemoveMethod;
    private static Method numericTagByteMethod;
    private static Method numericTagIntMethod;

    static {
        initReflectionBindings();
    }

    private NmsBridge() {
    }

    private static void initReflectionBindings() {
        try {
            for (Field f : ServerCommonPacketListenerImpl.class.getDeclaredFields()) {
                if (Connection.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    listenerConnectionField = f;
                    break;
                }
            }
            for (Field f : Connection.class.getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    networkChannelField = f;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }

        // Bind Identifier (1.21.11 - 26.3) or ResourceLocation (1.21 - 1.21.10)
        try {
            Class<?> idClass;
            try {
                idClass = Class.forName("net.minecraft.resources.Identifier");
            } catch (ClassNotFoundException e) {
                idClass = Class.forName("net.minecraft.resources.ResourceLocation");
            }
            parseResourceKeyMethod = idClass.getMethod("tryParse", String.class);
            Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
            for (Method m : registryClass.getMethods()) {
                if (m.getName().equals("containsKey") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == idClass) {
                    registryContainsKeyMethod = m;
                } else if (m.getName().equals("getKey") && m.getParameterCount() == 1) {
                    registryGetKeyMethod = m;
                }
            }
        } catch (Throwable ignored) {
        }

        // Bind ClientboundContainerSetContentPacket (1.21-1.21.1 class vs 1.21.2-26.3 record)
        try {
            Class<?> cls = ClientboundContainerSetContentPacket.class;
            setContentContainerIdGetter = findMethod(cls, "containerId", "getContainerId");
            setContentStateIdGetter = findMethod(cls, "stateId", "getStateId");
            setContentItemsGetter = findMethod(cls, "items", "getItems");
            setContentCarriedGetter = findMethod(cls, "carriedItem", "getCarriedItem");

            try {
                setContentRecordCtor = cls.getConstructor(int.class, int.class, List.class, ItemStack.class);
            } catch (NoSuchMethodException e) {
                setContentLegacyCtor = cls.getConstructor(int.class, int.class, NonNullList.class, ItemStack.class);
            }
        } catch (Throwable ignored) {
        }

        // Bind ClientboundSetCursorItemPacket (1.21.2 - 26.3)
        try {
            setCursorPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket");
            setCursorContentsGetter = findMethod(setCursorPacketClass, "contents", "getContents");
            setCursorCtor = setCursorPacketClass.getConstructor(ItemStack.class);
        } catch (Throwable ignored) {
        }

        // Bind ClientboundSetPlayerInventoryPacket (1.21.2 - 26.3)
        try {
            setPlayerInvPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket");
            setPlayerInvSlotGetter = findMethod(setPlayerInvPacketClass, "slot", "getSlot");
            setPlayerInvContentsGetter = findMethod(setPlayerInvPacketClass, "contents", "getContents");
            setPlayerInvCtor = setPlayerInvPacketClass.getConstructor(int.class, ItemStack.class);
        } catch (Throwable ignored) {
        }

        // Bind NBT methods whose signatures changed between 1.21.1 and 1.21.5+ / 26.x
        try {
            compoundTagRemoveMethod = CompoundTag.class.getMethod("remove", String.class);
            numericTagByteMethod = findMethod(NumericTag.class, "byteValue", "getAsByte");
            numericTagIntMethod = findMethod(NumericTag.class, "intValue", "getAsInt");
        } catch (Throwable ignored) {
        }
    }

    private static Method findMethod(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Method m = clazz.getMethod(name);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    /**
     * Checks whether the given item identifier exists in the running server's NMS BuiltInRegistries.ITEM
     * or Bukkit Material enum.
     */
    public static boolean isItemRegistered(String rawItemName) {
        if (rawItemName == null || rawItemName.isBlank()) {
            return false;
        }
        String clean = rawItemName.trim().toLowerCase(Locale.ROOT);
        if (!clean.contains(":")) {
            clean = "minecraft:" + clean;
        }

        try {
            if (parseResourceKeyMethod != null && registryContainsKeyMethod != null) {
                Object resourceKey = parseResourceKeyMethod.invoke(null, clean);
                if (resourceKey != null) {
                    Object exists = registryContainsKeyMethod.invoke(BuiltInRegistries.ITEM, resourceKey);
                    if (exists instanceof Boolean b) {
                        return b && !"minecraft:air".equals(clean);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        String enumName = rawItemName.trim().toUpperCase(Locale.ROOT);
        if (enumName.startsWith("MINECRAFT:")) {
            enumName = enumName.substring("MINECRAFT:".length());
        }
        Material mat = Material.matchMaterial(enumName);
        return mat != null && !mat.isAir();
    }

    /**
     * Resolves the uppercase item name (e.g. "NETHERITE_SPEAR") of a Bukkit ItemStack via NMS registry.
     */
    public static String resolveItemKey(org.bukkit.inventory.ItemStack bukkitStack) {
        if (bukkitStack == null || bukkitStack.getType().isAir()) {
            return "AIR";
        }
        try {
            ItemStack nms = CraftItemStack.asNMSCopy(bukkitStack);
            if (!nms.isEmpty() && registryGetKeyMethod != null) {
                Object keyObj = registryGetKeyMethod.invoke(BuiltInRegistries.ITEM, nms.getItem());
                if (keyObj != null) {
                    String full = keyObj.toString();
                    int colon = full.indexOf(':');
                    String path = colon >= 0 ? full.substring(colon + 1) : full;
                    return path.toUpperCase(Locale.ROOT);
                }
            }
        } catch (Throwable ignored) {
        }
        return bukkitStack.getType().name().toUpperCase(Locale.ROOT);
    }

    public static Channel getNettyChannel(Player player) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            return null;
        }
        try {
            ServerPlayer handle = craftPlayer.getHandle();
            if (handle == null || handle.connection == null || listenerConnectionField == null || networkChannelField == null) {
                return null;
            }
            Connection conn = (Connection) listenerConnectionField.get(handle.connection);
            if (conn == null) {
                return null;
            }
            return (Channel) networkChannelField.get(conn);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Forces an immediate NMS container state & cursor synchronization to eliminate ghost items and click-drop dupes.
     */
    public static void syncPlayerContainer(Player player) {
        if (player instanceof CraftPlayer craftPlayer) {
            try {
                ServerPlayer serverPlayer = craftPlayer.getHandle();
                if (serverPlayer != null && serverPlayer.containerMenu != null) {
                    serverPlayer.containerMenu.sendAllDataToRemote();
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        player.updateInventory();
    }

    public static boolean isProtectedNmsItem(ItemStack nmsStack) {
        if (nmsStack == null || nmsStack.isEmpty()) {
            return false;
        }
        CustomData customData = nmsStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return false;
        }
        CompoundTag root = customData.copyTag();
        Tag pbvTag = root.get(PUBLIC_BUKKIT_VALUES);
        if (!(pbvTag instanceof CompoundTag pbv)) {
            return false;
        }
        Tag val = pbv.get(PROTECTED_KEY);
        Byte byteVal = extractByteValue(val);
        if (byteVal != null) {
            return byteVal == 1;
        }
        if (val instanceof StringTag str) {
            return "true".equalsIgnoreCase(unwrapStringTag(str));
        }
        return false;
    }

    public static ItemStack injectClientLoreIfNeeded(ItemStack nmsStack, List<Component> adventureLoreLines) {
        if (!isProtectedNmsItem(nmsStack) || adventureLoreLines == null || adventureLoreLines.isEmpty()) {
            return null;
        }

        CustomData customData = nmsStack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag root = customData.copyTag();
            if (root.get(PUBLIC_BUKKIT_VALUES) instanceof CompoundTag pbv && pbv.contains(CLIENT_LORE_MARKER_KEY)) {
                return null;
            }
        }

        ItemStack copy = nmsStack.copy();
        List<net.minecraft.network.chat.Component> vanillaLoreToAdd = PaperAdventure.asVanilla(adventureLoreLines);

        ItemLore existingLore = copy.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        List<net.minecraft.network.chat.Component> combined = new ArrayList<>(existingLore.lines().size() + vanillaLoreToAdd.size());
        combined.addAll(existingLore.lines());
        combined.addAll(vanillaLoreToAdd);
        copy.set(DataComponents.LORE, new ItemLore(combined));

        int addedCount = vanillaLoreToAdd.size();
        CustomData.update(DataComponents.CUSTOM_DATA, copy, rootTag -> {
            CompoundTag pbv;
            if (rootTag.get(PUBLIC_BUKKIT_VALUES) instanceof CompoundTag existingPbv) {
                pbv = existingPbv;
            } else {
                pbv = new CompoundTag();
                rootTag.put(PUBLIC_BUKKIT_VALUES, pbv);
            }
            pbv.putInt(CLIENT_LORE_MARKER_KEY, addedCount);
        });

        return copy;
    }

    public static ItemStack stripClientLoreIfNeeded(ItemStack nmsStack) {
        if (nmsStack == null || nmsStack.isEmpty()) {
            return null;
        }
        CustomData customData = nmsStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return null;
        }
        CompoundTag root = customData.copyTag();
        if (!(root.get(PUBLIC_BUKKIT_VALUES) instanceof CompoundTag pbv)) {
            return null;
        }
        Integer injectedCountObj = extractIntValue(pbv.get(CLIENT_LORE_MARKER_KEY));
        if (injectedCountObj == null) {
            return null;
        }

        int injectedCount = injectedCountObj;
        ItemStack copy = nmsStack.copy();

        ItemLore existingLore = copy.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        List<net.minecraft.network.chat.Component> currentLines = existingLore.lines();
        if (injectedCount > 0 && !currentLines.isEmpty()) {
            int newSize = Math.max(0, currentLines.size() - injectedCount);
            if (newSize == 0) {
                copy.remove(DataComponents.LORE);
            } else {
                copy.set(DataComponents.LORE, new ItemLore(new ArrayList<>(currentLines.subList(0, newSize))));
            }
        }

        CustomData.update(DataComponents.CUSTOM_DATA, copy, rootTag -> {
            if (rootTag.get(PUBLIC_BUKKIT_VALUES) instanceof CompoundTag innerPbv) {
                removeCompoundKey(innerPbv, CLIENT_LORE_MARKER_KEY);
                if (innerPbv.isEmpty()) {
                    removeCompoundKey(rootTag, PUBLIC_BUKKIT_VALUES);
                }
            }
        });

        return copy;
    }

    @SuppressWarnings("unchecked")
    public static Object transformOutgoingPacket(Object packet, List<Component> adventureLoreLines) {
        if (packet == null || adventureLoreLines == null || adventureLoreLines.isEmpty()) {
            return packet;
        }

        // 1. ClientboundContainerSetSlotPacket (1.21 -> 26.3)
        if (packet instanceof ClientboundContainerSetSlotPacket slotPacket) {
            ItemStack modified = injectClientLoreIfNeeded(slotPacket.getItem(), adventureLoreLines);
            if (modified != null) {
                return new ClientboundContainerSetSlotPacket(
                        slotPacket.getContainerId(),
                        slotPacket.getStateId(),
                        slotPacket.getSlot(),
                        modified
                );
            }
            return packet;
        }

        // 2. ClientboundContainerSetContentPacket (1.21-1.21.1 class & 1.21.2-26.3 record)
        if (packet instanceof ClientboundContainerSetContentPacket) {
            try {
                int containerId = (int) setContentContainerIdGetter.invoke(packet);
                int stateId = (int) setContentStateIdGetter.invoke(packet);
                List<ItemStack> items = (List<ItemStack>) setContentItemsGetter.invoke(packet);
                ItemStack carried = (ItemStack) setContentCarriedGetter.invoke(packet);

                boolean anyModified = false;
                List<ItemStack> newItems = new ArrayList<>(items.size());
                for (ItemStack item : items) {
                    ItemStack mod = injectClientLoreIfNeeded(item, adventureLoreLines);
                    if (mod != null) {
                        newItems.add(mod);
                        anyModified = true;
                    } else {
                        newItems.add(item);
                    }
                }

                ItemStack modCarried = injectClientLoreIfNeeded(carried, adventureLoreLines);
                if (modCarried != null) {
                    anyModified = true;
                } else {
                    modCarried = carried;
                }

                if (anyModified) {
                    if (setContentRecordCtor != null) {
                        return setContentRecordCtor.newInstance(containerId, stateId, newItems, modCarried);
                    } else if (setContentLegacyCtor != null) {
                        NonNullList<ItemStack> nnList = NonNullList.withSize(newItems.size(), ItemStack.EMPTY);
                        for (int i = 0; i < newItems.size(); i++) {
                            nnList.set(i, newItems.get(i));
                        }
                        return setContentLegacyCtor.newInstance(containerId, stateId, nnList, modCarried);
                    }
                }
            } catch (Throwable ignored) {
            }
            return packet;
        }

        // 3. ClientboundSetCursorItemPacket (1.21.2 -> 26.3)
        if (setCursorPacketClass != null && setCursorPacketClass.isInstance(packet)) {
            try {
                ItemStack contents = (ItemStack) setCursorContentsGetter.invoke(packet);
                ItemStack modified = injectClientLoreIfNeeded(contents, adventureLoreLines);
                if (modified != null) {
                    return setCursorCtor.newInstance(modified);
                }
            } catch (Throwable ignored) {
            }
            return packet;
        }

        // 4. ClientboundSetPlayerInventoryPacket (1.21.2 -> 26.3)
        if (setPlayerInvPacketClass != null && setPlayerInvPacketClass.isInstance(packet)) {
            try {
                int slot = (int) setPlayerInvSlotGetter.invoke(packet);
                ItemStack contents = (ItemStack) setPlayerInvContentsGetter.invoke(packet);
                ItemStack modified = injectClientLoreIfNeeded(contents, adventureLoreLines);
                if (modified != null) {
                    return setPlayerInvCtor.newInstance(slot, modified);
                }
            } catch (Throwable ignored) {
            }
            return packet;
        }

        // 5. ClientboundSetEquipmentPacket (1.21 -> 26.3)
        if (packet instanceof ClientboundSetEquipmentPacket equipPacket) {
            List<Pair<EquipmentSlot, ItemStack>> slots = equipPacket.getSlots();
            boolean anyModified = false;
            List<Pair<EquipmentSlot, ItemStack>> newSlots = new ArrayList<>(slots.size());
            for (Pair<EquipmentSlot, ItemStack> pair : slots) {
                ItemStack mod = injectClientLoreIfNeeded(pair.getSecond(), adventureLoreLines);
                if (mod != null) {
                    newSlots.add(Pair.of(pair.getFirst(), mod));
                    anyModified = true;
                } else {
                    newSlots.add(pair);
                }
            }
            if (anyModified) {
                return new ClientboundSetEquipmentPacket(equipPacket.getEntity(), newSlots);
            }
            return packet;
        }

        // 6. ClientboundBundlePacket (1.21 -> 26.3)
        if (packet instanceof ClientboundBundlePacket bundlePacket) {
            boolean anyModified = false;
            List<Packet<? super ClientGamePacketListener>> newSubPackets = new ArrayList<>();
            for (Packet<? super ClientGamePacketListener> sub : bundlePacket.subPackets()) {
                Object transformed = transformOutgoingPacket(sub, adventureLoreLines);
                if (transformed != sub && transformed instanceof Packet<?> p) {
                    newSubPackets.add((Packet<? super ClientGamePacketListener>) p);
                    anyModified = true;
                } else {
                    newSubPackets.add(sub);
                }
            }
            if (anyModified) {
                return new ClientboundBundlePacket(newSubPackets);
            }
            return packet;
        }

        return packet;
    }

    public static Object transformIncomingPacket(Object packet) {
        if (packet instanceof ServerboundSetCreativeModeSlotPacket creativePacket) {
            ItemStack stripped = stripClientLoreIfNeeded(creativePacket.itemStack());
            if (stripped != null) {
                return new ServerboundSetCreativeModeSlotPacket(creativePacket.slotNum(), stripped);
            }
        }
        return packet;
    }

    private static Byte extractByteValue(Tag tag) {
        if (tag == null) {
            return null;
        }
        if (ByteTag.ONE.equals(tag)) {
            return (byte) 1;
        }
        if (ByteTag.ZERO.equals(tag)) {
            return (byte) 0;
        }
        if (numericTagByteMethod != null && NumericTag.class.isInstance(tag)) {
            try {
                Object val = numericTagByteMethod.invoke(tag);
                if (val instanceof Number n) {
                    return n.byteValue();
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Integer extractIntValue(Tag tag) {
        if (tag == null) {
            return null;
        }
        if (numericTagIntMethod != null && NumericTag.class.isInstance(tag)) {
            try {
                Object val = numericTagIntMethod.invoke(tag);
                if (val instanceof Number n) {
                    return n.intValue();
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void removeCompoundKey(CompoundTag tag, String key) {
        if (tag == null || key == null || compoundTagRemoveMethod == null) {
            return;
        }
        try {
            compoundTagRemoveMethod.invoke(tag, key);
        } catch (Throwable ignored) {
        }
    }

    private static String unwrapStringTag(StringTag tag) {
        String raw = tag.toString();
        if (raw.length() >= 2 && ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'")))) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }
}

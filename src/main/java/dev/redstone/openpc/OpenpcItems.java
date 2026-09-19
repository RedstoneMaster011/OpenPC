package dev.redstone.openpc;

import dev.redstone.openpc.hardware.HardwareDefinition;
import dev.redstone.openpc.hardware.HardwareRegistry;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenpcItems {

    private static final Map<String, Item> BY_HARDWARE_ID = new LinkedHashMap<>();

    private OpenpcItems() {
    }

    public static void registerAll() {
        for (HardwareDefinition definition : HardwareRegistry.all()) {
            Item.Settings settings = new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, definition.identifier()));
            Item item = new Item(settings);
            Registry.register(Registries.ITEM, definition.identifier(), item);
            BY_HARDWARE_ID.put(definition.id(), item);
        }
    }

    public static Item forHardware(String definitionId) {
        return BY_HARDWARE_ID.get(definitionId);
    }

    public static boolean isHardwareItem(Item item) {
        return BY_HARDWARE_ID.containsValue(item);
    }
}
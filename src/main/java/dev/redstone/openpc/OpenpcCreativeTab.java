package dev.redstone.openpc;

import dev.redstone.openpc.hardware.HardwareCategory;
import dev.redstone.openpc.hardware.HardwareDefinition;
import dev.redstone.openpc.hardware.HardwareRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class OpenpcCreativeTab {

    public static final ItemGroup TAB = ItemGroup.create(ItemGroup.Row.TOP, 7)
            .displayName(Text.translatable("itemGroup.openpc"))
            .icon(() -> new ItemStack(OpenpcBlocks.PC_CASE))
            .entries((context, entries) -> {
                entries.add(OpenpcBlocks.PC_CASE);
                for (HardwareCategory category : HardwareCategory.values()) {
                    for (HardwareDefinition definition : HardwareRegistry.ofCategory(category)) {
                        Item item = OpenpcItems.forHardware(definition.id());
                        if (item != null) {
                            entries.add(item);
                        }
                    }
                }
            })
            .build();

    private OpenpcCreativeTab() {
    }

    public static void register() {
        Registry.register(Registries.ITEM_GROUP, Identifier.of("openpc", "hardware"), TAB);
    }
}
package dev.redstone.openpc;

import dev.redstone.openpc.block.PcCaseBlock;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class OpenpcBlocks {

    public static final Identifier PC_CASE_ID = Identifier.of("openpc", "pc_case");
    public static final PcCaseBlock PC_CASE = new PcCaseBlock(RegistryKey.of(RegistryKeys.BLOCK, PC_CASE_ID));

    private OpenpcBlocks() {
    }

    public static void registerAll() {
        Registry.register(Registries.BLOCK, PC_CASE_ID, PC_CASE);
        Items.register(PC_CASE, new Item.Settings());
    }
}
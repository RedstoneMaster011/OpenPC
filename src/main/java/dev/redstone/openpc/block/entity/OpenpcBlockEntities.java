package dev.redstone.openpc.block.entity;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class OpenpcBlockEntities {

    public static final BlockEntityType<PcCaseBlockEntity> PC_CASE =
            FabricBlockEntityTypeBuilder.create(PcCaseBlockEntity::new, dev.redstone.openpc.OpenpcBlocks.PC_CASE).build();

    private OpenpcBlockEntities() {
    }

    public static void registerAll() {
        Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of("openpc", "pc_case"), PC_CASE);
    }
}
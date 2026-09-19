package dev.redstone.openpc.block;

import com.mojang.serialization.MapCodec;
import dev.redstone.openpc.OpenpcNetworking;
import dev.redstone.openpc.block.entity.OpenpcBlockEntities;
import dev.redstone.openpc.block.entity.PcCaseBlockEntity;
import dev.redstone.openpc.data.PcConfig;
import dev.redstone.openpc.service.PcService;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class PcCaseBlock extends BlockWithEntity {

    private static final MapCodec<PcCaseBlock> CODEC = createCodec(PcCaseBlock::new);

    public PcCaseBlock(RegistryKey<Block> blockKey) {
        this(AbstractBlock.Settings.create()
                .registryKey(blockKey)
                .mapColor(MapColor.IRON_GRAY)
                .sounds(BlockSoundGroup.METAL)
                .strength(2.0f, 6.0f));
    }

    public PcCaseBlock(Settings settings) {
        super(settings);
        setDefaultState(stateManager.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<PcCaseBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(HorizontalFacingBlock.FACING, context.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PcCaseBlockEntity(pos, state);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootWorldContext.Builder builder) {
        List<ItemStack> drops = new ArrayList<>();
        ItemStack stack = new ItemStack(this);
        BlockEntity blockEntity = builder.get(LootContextParameters.BLOCK_ENTITY);
        if (blockEntity instanceof PcCaseBlockEntity pc) {
            NbtWriteView view = NbtWriteView.create(ErrorReporter.EMPTY, builder.getWorld().getRegistryManager());
            pc.writeDataWithoutId(view);
            BlockItem.setBlockEntityData(stack, OpenpcBlockEntities.PC_CASE, view);
        }
        drops.add(stack);
        return drops;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        PcConfig config = PcService.ensurePcExists((ServerWorld) world, pos);
        if (config != null && player instanceof ServerPlayerEntity serverPlayer) {
            OpenpcNetworking.sendSnapshotTo(serverPlayer, pos, config);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
}

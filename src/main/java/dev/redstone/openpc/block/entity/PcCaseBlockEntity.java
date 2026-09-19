package dev.redstone.openpc.block.entity;

import dev.redstone.openpc.data.PcConfig;
import dev.redstone.openpc.data.PcDataStore;
import dev.redstone.openpc.data.PcSerialization;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PcCaseBlockEntity extends BlockEntity {

    public static final String DATA_KEY = "pc_data";

    private PcConfig config;

    public PcCaseBlockEntity(BlockPos pos, BlockState state) {
        this(OpenpcBlockEntities.PC_CASE, pos, state);
    }

    public PcCaseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.config = new PcConfig(0);
    }

    public PcConfig getConfig() {
        return config;
    }

    public synchronized void setConfig(PcConfig config) {
        this.config = config == null ? new PcConfig(0) : config;
        markDirty();
        World world = getWorld();
        if (world != null && !world.isClient()) {
            BlockState state = getCachedState();
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
            if (this.config.pcId() != 0) {
                PcDataStore.writeConfigMirrorAsync(this.config.pcId(), this.config);
            }
        }
    }

    public synchronized void installConfigAndMarkDirty(PcConfig config) {
        setConfig(config);
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.putString(DATA_KEY, PcSerialization.encode(config));
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        String data = view.getOptionalString(DATA_KEY).orElse("");
        this.config = PcSerialization.decode(data);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}

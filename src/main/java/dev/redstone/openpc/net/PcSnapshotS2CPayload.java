package dev.redstone.openpc.net;

import dev.redstone.openpc.data.PcConfig;
import dev.redstone.openpc.data.PcSerialization;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record PcSnapshotS2CPayload(BlockPos pos, PcConfig config) implements CustomPayload {

    public static final Id<PcSnapshotS2CPayload> ID = new Id<>(Identifier.of("openpc", "pc_snapshot"));
    public static final PacketCodec<RegistryByteBuf, PcSnapshotS2CPayload> CODEC = PacketCodec.ofStatic(
            (buf, value) -> {
                buf.writeBlockPos(value.pos());
                buf.writeString(PcSerialization.encode(value.config()));
            },
            buf -> new PcSnapshotS2CPayload(buf.readBlockPos(), PcSerialization.decode(buf.readString())));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
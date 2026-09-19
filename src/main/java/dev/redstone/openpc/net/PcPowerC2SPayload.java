package dev.redstone.openpc.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record PcPowerC2SPayload(BlockPos pos, boolean start) implements CustomPayload {

    public static final Id<PcPowerC2SPayload> ID = new Id<>(Identifier.of("openpc", "pc_power"));
    public static final PacketCodec<RegistryByteBuf, PcPowerC2SPayload> CODEC = PacketCodec.ofStatic(
            (buf, value) -> {
                buf.writeBlockPos(value.pos());
                buf.writeBoolean(value.start());
            },
            buf -> new PcPowerC2SPayload(buf.readBlockPos(), buf.readBoolean()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
package dev.redstone.openpc.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record PcModifyC2SPayload(BlockPos pos, String configJson) implements CustomPayload {

    public static final Id<PcModifyC2SPayload> ID = new Id<>(Identifier.of("openpc", "pc_modify"));
    public static final PacketCodec<RegistryByteBuf, PcModifyC2SPayload> CODEC = PacketCodec.ofStatic(
            (buf, value) -> {
                buf.writeBlockPos(value.pos());
                buf.writeString(value.configJson());
            },
            buf -> new PcModifyC2SPayload(buf.readBlockPos(), buf.readString()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
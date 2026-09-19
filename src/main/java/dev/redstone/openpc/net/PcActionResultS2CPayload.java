package dev.redstone.openpc.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PcActionResultS2CPayload(boolean success) implements CustomPayload {

    public static final Id<PcActionResultS2CPayload> ID = new Id<>(Identifier.of("openpc", "pc_result"));
    public static final PacketCodec<RegistryByteBuf, PcActionResultS2CPayload> CODEC = PacketCodec.ofStatic(
            (buf, value) -> buf.writeBoolean(value.success()),
            buf -> new PcActionResultS2CPayload(buf.readBoolean()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
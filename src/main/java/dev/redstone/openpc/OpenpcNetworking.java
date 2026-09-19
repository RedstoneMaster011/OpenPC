package dev.redstone.openpc;

import dev.redstone.openpc.data.PcConfig;
import dev.redstone.openpc.data.PcSerialization;
import dev.redstone.openpc.net.PcActionResultS2CPayload;
import dev.redstone.openpc.net.PcModifyC2SPayload;
import dev.redstone.openpc.net.PcPowerC2SPayload;
import dev.redstone.openpc.net.PcSnapshotS2CPayload;
import dev.redstone.openpc.service.PcService;
import dev.redstone.openpc.validation.PcValidationResult;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public final class OpenpcNetworking {

    private OpenpcNetworking() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playC2S().register(PcModifyC2SPayload.ID, PcModifyC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PcPowerC2SPayload.ID, PcPowerC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PcSnapshotS2CPayload.ID, PcSnapshotS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PcActionResultS2CPayload.ID, PcActionResultS2CPayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(PcModifyC2SPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            PcValidationResult result;
            try {
                dev.redstone.openpc.data.PcConfig proposed = dev.redstone.openpc.data.PcSerialization.decode(payload.configJson());
                result = PcService.applyModification(player, payload.pos(), proposed);
            } catch (Exception e) {
                dev.redstone.openpc.Openpc.LOGGER.warn("Rejected malformed modify payload from {}", player.getName().getString(), e);
                result = PcValidationResult.failed(java.util.List.of());
            }
            sendResultTo(player, result);
        });

        ServerPlayNetworking.registerGlobalReceiver(PcPowerC2SPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            PcValidationResult result = PcService.applyPower(player, payload.pos(), payload.start());
            sendResultTo(player, result);
        });
    }

    public static void sendSnapshotTo(ServerPlayerEntity player, BlockPos pos, dev.redstone.openpc.data.PcConfig config) {
        ServerPlayNetworking.send(player, new PcSnapshotS2CPayload(pos, config));
    }

    public static void sendResultTo(ServerPlayerEntity player, PcValidationResult result) {
        ServerPlayNetworking.send(player, new PcActionResultS2CPayload(result.isValid()));
    }
}
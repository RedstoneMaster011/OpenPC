package dev.redstone.openpc.client.net;

import dev.redstone.openpc.client.PcClientController;
import dev.redstone.openpc.data.PcConfig;
import dev.redstone.openpc.net.PcActionResultS2CPayload;
import dev.redstone.openpc.net.PcModifyC2SPayload;
import dev.redstone.openpc.net.PcPowerC2SPayload;
import dev.redstone.openpc.net.PcSnapshotS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.BlockPos;

public final class OpenpcClientNetworking {

    private OpenpcClientNetworking() {
    }

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(PcSnapshotS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> PcClientController.onSnapshot(payload.pos(), payload.config()));
        });
        ClientPlayNetworking.registerGlobalReceiver(PcActionResultS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> PcClientController.onActionResult(payload.success()));
        });
    }

    public static void sendModify(BlockPos pos, PcConfig config) {
        ClientPlayNetworking.send(new PcModifyC2SPayload(pos, dev.redstone.openpc.data.PcSerialization.encode(config)));
    }

    public static void sendPower(BlockPos pos, boolean start) {
        ClientPlayNetworking.send(new PcPowerC2SPayload(pos, start));
    }
}
package dev.redstone.openpc.client;

import dev.redstone.openpc.client.net.OpenpcClientNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class OpenpcClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        QemuSetup.initialize();
        PcClientController.init();
        OpenpcClientNetworking.registerReceivers();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> QemuProcessManager.stopAll());
    }
}

package dev.redstone.openpc.client;

import dev.redstone.openpc.client.net.OpenpcClientNetworking;
import net.fabricmc.api.ClientModInitializer;

public class OpenpcClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        QemuSetup.initialize();
        PcClientController.init();
        OpenpcClientNetworking.registerReceivers();
    }
}

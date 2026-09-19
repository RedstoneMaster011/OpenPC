package dev.redstone.openpc.client;

import dev.redstone.openpc.client.net.OpenpcClientNetworking;
import dev.redstone.openpc.data.PcDataStore;
import net.fabricmc.api.ClientModInitializer;

import java.io.IOException;
import java.nio.file.Files;

public class OpenpcClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        QemuSetup.initialize();
        try {
            Files.createDirectories(PcDataStore.isoDirectory());
        } catch (IOException ignored) {
        }
        PcClientController.init();
        OpenpcClientNetworking.registerReceivers();
    }
}

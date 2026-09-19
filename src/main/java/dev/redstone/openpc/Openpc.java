package dev.redstone.openpc;

import dev.redstone.openpc.block.entity.OpenpcBlockEntities;
import dev.redstone.openpc.hardware.HardwareDefinitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

public class Openpc implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("openpc");

    @Override
    public void onInitialize() {
        HardwareDefinitions.registerAll();
        OpenpcBlocks.registerAll();
        OpenpcItems.registerAll();
        OpenpcBlockEntities.registerAll();
        OpenpcCreativeTab.register();
        OpenpcNetworking.registerPayloadTypes();
        OpenpcNetworking.registerServerReceivers();
        LOGGER.info("OpenPC initialized");
    }
}
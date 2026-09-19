package dev.redstone.openpc.client;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class OpenpcQemuRuntime {

    public static final Logger LOGGER = LoggerFactory.getLogger("openpc-qemu");

    private OpenpcQemuRuntime() {
    }

    public static Path dataRoot() {
        return FabricLoader.getInstance().getGameDir().resolve("openpc/data");
    }

    public static Path pcDirectory(long pcId) {
        return dataRoot().resolve("pc_" + padId(pcId));
    }

    private static String padId(long pcId) {
        return String.format("%06d", pcId);
    }

    public static void logError(String message, Throwable error) {
        LOGGER.error(message, error);
    }

    public static void logInfo(String message) {
        LOGGER.info(message);
    }

    public static void logWarn(String message) {
        LOGGER.warn(message);
    }
}
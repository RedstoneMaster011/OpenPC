package dev.redstone.openpc.client;

import dev.redstone.openpc.Openpc;
import dev.redstone.openpc.client.gui.PcBuilderScreen;
import dev.redstone.openpc.client.gui.RunningPcScreen;
import dev.redstone.openpc.client.net.OpenpcClientNetworking;
import dev.redstone.openpc.data.PcConfig;
import dev.redstone.openpc.data.PcPowerState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.BlockPos;

public final class PcClientController {

    private static BlockPos activePos;
    private static PcConfig activeConfig;
    private static PcBuilderScreen builderScreen;
    private static RunningPcScreen runningScreen;

    private PcClientController() {
    }

    public static void init() {
        QemuProcessManager.setOnExitListener(PcClientController::onQemuExit);
    }

    public static BlockPos activePos() {
        return activePos;
    }

    public static PcConfig activeConfig() {
        return activeConfig;
    }

    public static void onSnapshot(BlockPos pos, PcConfig config) {
        activePos = pos;
        activeConfig = config;
        MinecraftClient client = MinecraftClient.getInstance();

        if (config.isPowerOn()) {
            ensureQemuRunning(config);
            if (runningScreen != null && runningScreen.posEquals(pos) && client.currentScreen == runningScreen) {
                runningScreen.refreshConfig(config);
                return;
            }
            if (builderScreen != null) {
                builderScreen = null;
            }
            runningScreen = new RunningPcScreen(pos, config);
            client.setScreen(runningScreen);
            return;
        }

        if (runningScreen != null && runningScreen.posEquals(pos)) {
            runningScreen.beforeDispose();
            if (client.currentScreen == runningScreen) {
                client.setScreen(null);
            }
            runningScreen = null;
        }
        QemuProcessManager.requestStop(config.pcId());

        if (builderScreen != null && builderScreen.posEquals(pos) && client.currentScreen == builderScreen) {
            builderScreen.applySnapshot(config);
            return;
        }
        builderScreen = new PcBuilderScreen(pos, config);
        client.setScreen(builderScreen);
    }

    public static void onActionResult(boolean success) {
        if (builderScreen != null) {
            builderScreen.notifyActionResult(success);
        }
        if (runningScreen != null && !success) {
            runningScreen.notifyActionResult(success);
        }
    }

    public static void ensureQemuRunning(PcConfig config) {
        if (QemuSetup.isReady() && QemuEnvironment.isUsable()) {
            if (!QemuProcessManager.isRunning(config.pcId())) {
                QemuProcessManager.launch(config);
            }
        } else {
            Openpc.LOGGER.warn("QEMU is not usable; PC display will be unavailable.");
        }
    }

    private static void onQemuExit(long pcId, int exitCode) {
        MinecraftClient.getInstance().execute(() -> {
            PcConfig config = activeConfig;
            if (config != null && config.pcId() == pcId) {
                if (runningScreen != null && runningScreen.pcIdEquals(pcId)) {
                    runningScreen.beforeDispose();
                    runningScreen = null;
                }
                PcConfig updated = config.copy();
                updated.setPowerState(PcPowerState.OFF);
                activeConfig = updated;
                if (activePos != null) {
                    OpenpcClientNetworking.sendPower(activePos, false);
                }
            }
        });
    }

    public static void onBuilderClosed(Screen screen) {
        if (builderScreen == screen) {
            builderScreen = null;
        }
    }

    public static void onRunningClosed(Screen screen) {
        if (runningScreen == screen) {
            runningScreen = null;
        }
    }

    public static PcBuilderScreen currentBuilderScreen() {
        return builderScreen;
    }
}

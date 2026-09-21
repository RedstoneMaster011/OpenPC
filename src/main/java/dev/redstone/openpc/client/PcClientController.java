package dev.redstone.openpc.client;

import dev.redstone.openpc.Openpc;
import dev.redstone.openpc.client.gui.ManagePcsScreen;
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

        // PC is off - ensure QEMU is stopped
        if (QemuProcessManager.isRunning(config.pcId())) {
            QemuProcessManager.requestStop(config.pcId());
        }

        if (runningScreen != null && runningScreen.posEquals(pos)) {
            runningScreen.beforeDispose();
            if (client.currentScreen == runningScreen) {
                client.setScreen(null);
            }
            runningScreen = null;
        }

        if (builderScreen != null && builderScreen.posEquals(pos)) {
            builderScreen.applySnapshot(config);
            if (client.currentScreen == builderScreen) {
                return;
            }
            if (!(client.currentScreen instanceof ManagePcsScreen)) {
                client.setScreen(builderScreen);
            }
            return;
        }
        builderScreen = new PcBuilderScreen(pos, config);
        if (client.currentScreen instanceof ManagePcsScreen) {
            return;
        }
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
            if (config == null || config.pcId() != pcId) {
                return;
            }
            if (exitCode == 0) {
                // Exit code 0 means the guest sent a reboot request: relaunch QEMU and reconnect.
                Openpc.LOGGER.info("QEMU for pc_" + pcId + " exited with code 0; treating as reboot and relaunching");
                ensureQemuRunning(config);
                if (runningScreen != null && runningScreen.pcIdEquals(pcId)) {
                    runningScreen.beforeDispose();
                    runningScreen = null;
                }
                if (activePos != null) {
                    runningScreen = new RunningPcScreen(activePos, config);
                    MinecraftClient.getInstance().setScreen(runningScreen);
                }
                return;
            }
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
        });
    }

    public static void onBuilderClosed(Screen screen) {
        if (builderScreen == screen) {
            builderScreen = null;
        }
    }

    public static void onPcDeleted(long pcId) {
        QemuProcessManager.requestStop(pcId);
        MinecraftClient client = MinecraftClient.getInstance();
        boolean active = activeConfig != null && activeConfig.pcId() == pcId;
        if (runningScreen != null && runningScreen.pcIdEquals(pcId)) {
            runningScreen.beforeDispose();
            runningScreen = null;
        }
        if (builderScreen != null && active) {
            builderScreen = null;
        }
        if (active) {
            PcConfig updated = activeConfig.copy();
            updated.setPowerState(PcPowerState.OFF);
            activeConfig = updated;
            if (activePos != null) {
                OpenpcClientNetworking.sendPower(activePos, false);
            }
        }
        if (active) {
            client.execute(() -> client.setScreen(null));
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

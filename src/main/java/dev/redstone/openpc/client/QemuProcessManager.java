package dev.redstone.openpc.client;

import dev.redstone.openpc.data.PcConfig;
import dev.redstone.openpc.data.PcDataStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public final class QemuProcessManager {

    private static final ConcurrentMap<Long, ManagedProcess> PROCESSES = new ConcurrentHashMap<>();
    private static volatile BiConsumer<Long, Integer> onExitListener;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (ManagedProcess managed : PROCESSES.values()) {
                if (managed.isAlive()) {
                    managed.process.destroyForcibly();
                }
            }
        }));
    }

    private QemuProcessManager() {
    }

    public static void setOnExitListener(BiConsumer<Long, Integer> listener) {
        onExitListener = listener;
    }

    public static boolean isRunning(long pcId) {
        ManagedProcess managed = PROCESSES.get(pcId);
        return managed != null && managed.isAlive();
    }

    public static Process processOf(long pcId) {
        ManagedProcess managed = PROCESSES.get(pcId);
        return managed == null ? null : managed.process;
    }

    public static boolean launch(PcConfig config) {
        long pcId = config.pcId();
        ManagedProcess existing = PROCESSES.get(pcId);
        if (existing != null && existing.isAlive()) {
            return false;
        }

        Path directory = PcDataStore.pcDirectory(pcId);
        List<String> command = QemuArguments.build(config, QemuArguments.vncDisplayFor(pcId));
        OpenpcQemuRuntime.logInfo("QEMU command: " + String.join(" ", command));
        try {
            Files.createDirectories(directory);
            Path logFile = directory.resolve("qemu.log");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(directory.toFile());
            builder.redirectErrorStream(true);
            builder.environment().put("LD_LIBRARY_PATH", QemuSetup.requireGameQemuDirectory().toAbsolutePath().toString());
            Process process = builder.start();
            ManagedProcess managed = new ManagedProcess(process, logFile);
            PROCESSES.put(pcId, managed);
            managed.spawnPump(pcId);
            managed.spawnWaiter(pcId);
            OpenpcQemuRuntime.logInfo("Launched QEMU for pc_" + pcId + " on VNC display " + QemuArguments.vncDisplayFor(pcId));
            return true;
        } catch (IOException error) {
            OpenpcQemuRuntime.logError("Failed to launch QEMU for pc_" + pcId, error);
            return false;
        }
    }

    public static void requestStop(long pcId) {
        ManagedProcess managed = PROCESSES.get(pcId);
        if (managed == null) {
            return;
        }
        Thread stopper = new Thread(managed::requestStop, "openpc-qemu-stop-" + pcId);
        stopper.setDaemon(true);
        stopper.start();
    }

    private static final class ManagedProcess {
        private final Process process;
        private final Path logFile;
        private final AtomicBoolean stopping = new AtomicBoolean(false);
        private Thread pump;

        private ManagedProcess(Process process, Path logFile) {
            this.process = process;
            this.logFile = logFile;
        }

        private boolean isAlive() {
            return process.isAlive();
        }

        private void requestStop() {
            if (stopping.getAndSet(true)) {
                return;
            }
            sendPowerOffIfPossible();
            try {
                Thread.sleep(1500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            process.destroyForcibly();
        }

        private void sendPowerOffIfPossible() {
            try {
                OutputStream output = process.getOutputStream();
                output.write("system_powerdown\n".getBytes());
                output.flush();
            } catch (IOException | IllegalStateException ignored) {
            }
        }

        private void spawnPump(long pcId) {
            pump = new Thread(() -> {
                try (InputStream input = process.getInputStream();
                     OutputStream log = Files.newOutputStream(logFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1) {
                        String line = new String(buffer, 0, bytesRead);
                        log.write(buffer, 0, bytesRead);
                        log.flush();
                        OpenpcQemuRuntime.logInfo("QEMU pc_" + pcId + ": " + line.trim());
                    }
                } catch (IOException error) {
                    OpenpcQemuRuntime.logError("QEMU log streaming failed for pc_" + pcId, error);
                }
            }, "openpc-qemu-pump-" + pcId);
            pump.setDaemon(true);
            pump.start();
        }

        private void spawnWaiter(long pcId) {
            Thread waiter = new Thread(() -> {
                int exitCode;
                try {
                    exitCode = process.waitFor();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    exitCode = -1;
                } finally {
                    PROCESSES.remove(pcId, this);
                    pump.interrupt();
                }
                OpenpcQemuRuntime.logInfo("QEMU for pc_" + pcId + " exited with code " + exitCode);
                BiConsumer<Long, Integer> listener = onExitListener;
                if (listener != null) {
                    listener.accept(pcId, exitCode);
                }
            }, "openpc-qemu-wait-" + pcId);
            waiter.setDaemon(true);
            waiter.start();
        }
    }
}
package dev.redstone.openpc.client;

import dev.redstone.openpc.data.PcConfig;
import dev.redstone.openpc.data.PcDataStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public final class QemuProcessManager {

    private static final ConcurrentMap<Long, ManagedProcess> PROCESSES = new ConcurrentHashMap<>();
    private static final java.util.Set<Long> STOPPING = ConcurrentHashMap.newKeySet();
    private static volatile BiConsumer<Long, Integer> onExitListener;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (ManagedProcess managed : PROCESSES.values()) {
                if (managed.isAlive()) {
                    managed.terminate();
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
            ManagedProcess managed = new ManagedProcess(pcId, process, logFile);
            PROCESSES.put(pcId, managed);
            STOPPING.remove(pcId);
            managed.spawnPump(pcId);
            managed.spawnWaiter(pcId);
            OpenpcQemuRuntime.logInfo("Launched QEMU for pc_" + pcId + " on VNC display " + QemuArguments.vncDisplayFor(pcId));
            return true;
        } catch (IOException error) {
            OpenpcQemuRuntime.logError("Failed to launch QEMU for pc_" + pcId, error);
            return false;
        }
    }

    public static void stopAll() {
        for (Long pcId : PROCESSES.keySet()) {
            requestStop(pcId);
        }
    }

    public static void requestStop(long pcId) {
        ManagedProcess managed = PROCESSES.get(pcId);
        if (managed == null) {
            return;
        }
        STOPPING.add(pcId);
        Thread stopper = new Thread(managed::requestStop, "openpc-qemu-stop-" + pcId);
        stopper.setDaemon(true);
        stopper.start();
    }

    public static boolean isStopping(long pcId) {
        return STOPPING.contains(pcId);
    }

    public static boolean consumeStopRequest(long pcId) {
        return STOPPING.remove(pcId);
    }

    private static final class ManagedProcess {
        private final long pcId;
        private final Process process;
        private final Path logFile;
        private final AtomicBoolean stopping = new AtomicBoolean(false);
        private Thread pump;

        private ManagedProcess(long pcId, Process process, Path logFile) {
            this.pcId = pcId;
            this.process = process;
            this.logFile = logFile;
        }

        private boolean isAlive() {
            return process.isAlive();
        }

        private void requestStop() {
            terminate();
        }

        private void terminate() {
            if (stopping.getAndSet(true)) {
                return;
            }
            sendMonitorCommand("quit");
            if (awaitExit(1500)) {
                return;
            }
            forceKillTree();
            if (awaitExit(1500)) {
                return;
            }
            forceKillTree();
        }

        private boolean awaitExit(long millis) {
            try {
                return process.waitFor(millis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private void forceKillTree() {
            try {
                process.toHandle().descendants().forEach(handle -> handle.destroyForcibly());
            } catch (UnsupportedOperationException ignored) {
            }
            process.destroyForcibly();
        }

        private boolean sendMonitorCommand(String command) {
            int port = QemuArguments.qmpPortFor(pcId);
            try (Socket socket = new Socket("127.0.0.1", port)) {
                socket.setSoTimeout(2000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                Writer writer = new OutputStreamWriter(socket.getOutputStream());
                if (reader.readLine() == null) {
                    return false;
                }
                writer.write("{\"execute\":\"qmp_capabilities\"}\n");
                writer.flush();
                if (reader.readLine() == null) {
                    return false;
                }
                writer.write("{\"execute\":\"" + command + "\"}\n");
                writer.flush();
                while (reader.readLine() != null) {
                    // drain until QEMU closes the connection
                }
                return true;
            } catch (IOException error) {
                return false;
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
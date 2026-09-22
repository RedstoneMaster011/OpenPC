package dev.redstone.openpc.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QemuEnvironment {

    public enum Os {
        WINDOWS,
        LINUX,
        MAC,
        OTHER
    }

    private static final AtomicBoolean PROBE_STARTED = new AtomicBoolean(false);
    private static volatile boolean probed;
    private static volatile boolean binaryUsable;
    private static volatile List<String> probesInstructions = List.of();
    private static volatile List<String> soundBackends = List.of();
    private static volatile List<String> machineNames = List.of();

    private QemuEnvironment() {
    }

    public static Os detectOs() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return Os.WINDOWS;
        }
        if (name.contains("linux")) {
            return Os.LINUX;
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return Os.MAC;
        }
        return Os.OTHER;
    }

    public static String binaryName() {
        return detectOs() == Os.WINDOWS ? "qemu-system-x86_64.exe" : "qemu-system-x86_64";
    }

    public static String qemuImgName() {
        return detectOs() == Os.WINDOWS ? "qemu-img.exe" : "qemu-img";
    }

    public static Path binaryPath() {
        return QemuSetup.requireGameQemuDirectory().resolve(binaryName());
    }

    public static Path qemuImgPath() {
        return QemuSetup.requireGameQemuDirectory().resolve(qemuImgName());
    }

    public static boolean binaryExists() {
        try {
            return Files.isRegularFile(binaryPath());
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isUsable() {
        return binaryUsable;
    }

    public static List<String> soundBackends() {
        return soundBackends;
    }

    public static List<String> machineNames() {
        return machineNames;
    }

    public static String configuredMachine(boolean defaultSupported) {
        if (machineNames.contains("q35")) {
            return "q35";
        }
        return "pc";
    }

    public static String pickSoundBackend() {
        if (detectOs() == Os.WINDOWS) {
            return "dsound";
        }
        if (detectOs() == Os.LINUX) {
            return "pa";
        }
        for (String candidate : List.of("sdl", "pa", "alsa", "wav")) {
            if (soundBackends.contains(candidate)) {
                return candidate;
            }
        }
        return "none";
    }

    /**
     * Kicks off the QEMU environment probe on a background thread. Safe to call
     * any number of times; the probe runs exactly once and never blocks the caller.
     */
    public static void startProbe() {
        if (!PROBE_STARTED.compareAndSet(false, true)) {
            return;
        }
        Thread probe = new Thread(() -> {
            QemuSetup.awaitReady();
            synchronized (QemuEnvironment.class) {
                if (probed) {
                    return;
                }
                runProbe();
                probed = true;
            }
        }, "openpc-qemu-probe");
        probe.setDaemon(true);
        probe.start();
    }

    private static void runProbe() {
        if (!QemuSetup.isReady() || !binaryExists()) {
            binaryUsable = false;
            return;
        }
        Path binary = binaryPath();
        ProbeResult version = runProbeProcess(binary, 6, "-version");
        if (version.exitCode() != 0) {
            OpenpcQemuRuntime.logWarn("QEMU binary answered with exit code " + version.exitCode() + ": " + version.output().trim());
            binaryUsable = false;
            return;
        }
        binaryUsable = true;
        probesInstructions = parseInstructions(version.output());
        soundBackends = parseSoundBackends(runProbeProcess(binary, 4, "-audiodev", "help").output());
        machineNames = parseNames(runProbeProcess(binary, 4, "-machine", "help").output());
    }

    private static ProbeResult runProbeProcess(Path binary, int timeoutSeconds, String... args) {
        List<String> command = new ArrayList<>();
        command.add(binary.toAbsolutePath().toString());
        command.addAll(List.of(args));

        ProcessBuilder probe = new ProcessBuilder(command);
        probe.redirectErrorStream(true);
        probe.environment().put("LD_LIBRARY_PATH", QemuSetup.requireGameQemuDirectory().toAbsolutePath().toString());

        Process process;
        try {
            process = probe.start();
        } catch (IOException error) {
            OpenpcQemuRuntime.logError("QEMU probe could not start the bundled binary", error);
            return new ProbeResult(-1, "");
        }

        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    output.append(line).append('\n');
                }
            } catch (IOException ignored) {
            }
        }, "openpc-qemu-probe-reader");
        reader.setDaemon(true);
        reader.start();

        int exitCode;
        try {
            if (process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                exitCode = process.exitValue();
            } else {
                process.destroyForcibly();
                exitCode = -1;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            exitCode = -1;
        }
        try {
            reader.join(100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return new ProbeResult(exitCode, output.toString());
    }

    private static List<String> parseSoundBackends(String help) {
        List<String> result = new ArrayList<>();
        if (help == null || help.isEmpty()) {
            return result;
        }
        Pattern pattern = Pattern.compile("\\b(dsound|sdl|pa|alsa|pipewire|oss|coreaudio|none|wav)\\b");
        Matcher matcher = pattern.matcher(help);
        while (matcher.find()) {
            String backend = matcher.group(1);
            if (!result.contains(backend)) {
                result.add(backend);
            }
        }
        // If no backends found but help contains "Available audio drivers", add common backends
        if (result.isEmpty() && help.contains("Available audio drivers")) {
            for (String line : help.split("\n")) {
                if (line.trim().equals("none")) result.add("none");
                if (line.trim().equals("wav")) result.add("wav");
                if (line.trim().equals("pa")) result.add("pa");
            }
        }
        return result;
    }

    private static List<String> parseNames(String help) {
        List<String> result = new ArrayList<>();
        if (help == null || help.isEmpty()) {
            return result;
        }
        Pattern pattern = Pattern.compile("\\b(pc|q35|isapc|pc-[a-z0-9.+-]+)\\b");
        Matcher matcher = pattern.matcher(help);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!result.contains(name) && !name.startsWith("pc-")) {
                result.add(name);
            }
        }
        return result;
    }

    private static List<String> parseInstructions(String help) {
        List<String> result = new ArrayList<>();
        if (help == null || help.isEmpty()) {
            return result;
        }
        for (String flag : List.of("-vnc", "-machine", "-cpu", "-audiodev", "-drive", "-netdev", "-device", "-vga")) {
            if (help.contains(flag)) {
                result.add(flag);
            }
        }
        return result;
    }

    private record ProbeResult(int exitCode, String output) {
    }
}

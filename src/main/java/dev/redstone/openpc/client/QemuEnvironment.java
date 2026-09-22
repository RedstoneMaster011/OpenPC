package dev.redstone.openpc.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QemuEnvironment {

    public enum Os {
        WINDOWS,
        LINUX,
        MAC,
        OTHER
    }

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
        ensureProbed();
        return binaryUsable;
    }

    public static List<String> soundBackends() {
        ensureProbed();
        return soundBackends;
    }

    public static List<String> machineNames() {
        ensureProbed();
        return machineNames;
    }

    public static String configuredMachine(boolean defaultSupported) {
        ensureProbed();
        if (machineNames.contains("q35")) {
            return "q35";
        }
        return "pc";
    }

    public static String pickSoundBackend() {
        ensureProbed();
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

    private static void ensureProbed() {
        if (probed) {
            return;
        }
        synchronized (QemuEnvironment.class) {
            if (probed) {
                return;
            }
            runProbe();
            probed = true;
        }
    }

    private static void runProbe() {
        if (!QemuSetup.isReady() || !binaryExists()) {
            binaryUsable = false;
            return;
        }
        Path binary = binaryPath();
        ProcessBuilder probe = new ProcessBuilder(binary.toAbsolutePath().toString(), "-version");
        probe.redirectErrorStream(true);
        probe.environment().put("LD_LIBRARY_PATH", QemuSetup.requireGameQemuDirectory().toAbsolutePath().toString());
        try {
            Process process = probe.start();
            String combined = new String(process.getInputStream().readAllBytes());
            boolean exited = process.waitFor(6, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                binaryUsable = false;
                return;
            }
            if (process.exitValue() != 0) {
                OpenpcQemuRuntime.logWarn("QEMU binary answered with exit code " + process.exitValue() + ": " + combined.trim());
                binaryUsable = false;
                return;
            }
            binaryUsable = true;
            probesInstructions = parseInstructions(readProbe(combined, binary, "-h"));
            soundBackends = parseSoundBackends(readProbe(null, binary, "-audiodev", "help"));
            machineNames = parseNames(readProbe(null, binary, "-machine", "help"));
        } catch (IOException error) {
            OpenpcQemuRuntime.logError("QEMU probe could not start the bundled binary", error);
            binaryUsable = false;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            binaryUsable = false;
        }
    }

    private static String readProbe(String alreadyRead, Path binary, String... args) {
        if (alreadyRead != null && args.length == 1 && args[0].equals("-h")) {
            return alreadyRead;
        }
        ProcessBuilder probe = new ProcessBuilder(new ArrayList<String>() {{
            add(binary.toAbsolutePath().toString());
            addAll(List.of(args));
        }});
        probe.redirectErrorStream(true);
        probe.environment().put("LD_LIBRARY_PATH", QemuSetup.requireGameQemuDirectory().toAbsolutePath().toString());
        try {
            Process process = probe.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean exited = process.waitFor(4, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                return "";
            }
            return output;
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "";
        }
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
}

package dev.redstone.openpc.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class QemuSetup {

    private static final AtomicBoolean extracted = new AtomicBoolean(false);
    private static volatile CompletableFuture<Void> extraction;

    private QemuSetup() {
    }

    public static void initialize() {
        synchronized (QemuSetup.class) {
            if (extraction == null) {
                extraction = CompletableFuture.runAsync(QemuSetup::runExtraction);
                extraction.exceptionally(error -> {
                    OpenpcQemuRuntime.logError("QEMU extraction failed", error);
                    return null;
                });
            }
        }
    }

    public static Path requireGameQemuDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("openpc/qemu");
    }

    public static boolean isReady() {
        return extracted.get() && Files.isDirectory(requireGameQemuDirectory());
    }

    private static void runExtraction() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path source = findBundledQemu();

        if (source == null) {
            throw new IllegalStateException("OpenPC could not locate the bundled qemu folder inside its jar.");
        }

        Path target = requireGameQemuDirectory();

        try {
            if (Files.exists(target)) {
                extracted.set(true);
                return;
            }

            try (Stream<Path> walk = Files.walk(source)) {
                walk.forEach(current -> {
                    try {
                        Path relative = source.relativize(current);
                        Path destination = target.resolve(relative);

                        if (Files.isDirectory(current)) {
                            Files.createDirectories(destination);
                        } else {
                            Files.createDirectories(destination.getParent());
                            Files.copy(current, destination, StandardCopyOption.REPLACE_EXISTING);
                            applyExecutablePermission(destination);
                        }
                    } catch (IOException error) {
                        throw new ExtractionFailure("Failed to extract bundled QEMU file " + current, error);
                    }
                });
            }

            extracted.set(true);
        } catch (IOException error) {
            throw new ExtractionFailure("Failed to create the openpc/qemu directory underneath " + gameDir, error);
        }
    }

    private static Path findBundledQemu() {
        return FabricLoader.getInstance()
                .getModContainer("openpc")
                .map(container -> {
                    for (Path root : container.getRootPaths()) {
                        Path candidate = root.resolve("qemu");
                        if (Files.isDirectory(candidate)) {
                            return candidate;
                        }
                    }
                    return null;
                })
                .orElse(null);
    }

    private static void applyExecutablePermission(Path file) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return;
        }
        String name = file.getFileName().toString();
        if (name.equals("qemu-system-x86_64") || name.equals("qemu-img") || name.equals("qemu-ga")) {
            try {
                Files.setPosixFilePermissions(file, Files.getPosixFilePermissions(file));
                file.toFile().setExecutable(true, false);
            } catch (UnsupportedOperationException ignored) {
                file.toFile().setExecutable(true, false);
            } catch (IOException error) {
                OpenpcQemuRuntime.logError("Could not mark QEMU executable " + file, error);
            }
        }
    }

    private static final class ExtractionFailure extends RuntimeException {
        private ExtractionFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

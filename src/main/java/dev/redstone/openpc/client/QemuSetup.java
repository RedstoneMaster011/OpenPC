package dev.redstone.openpc.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
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

    public static void awaitReady() {
        synchronized (QemuSetup.class) {
            if (extraction == null) {
                initialize();
            }
        }
        CompletableFuture<Void> future = extraction;
        if (future != null) {
            try {
                future.get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.ExecutionException error) {
                // Extraction failed; isReady() will report false and callers fall back gracefully.
            }
        }
    }

    private static void runExtraction() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path source = findBundledQemu();

        if (source == null) {
            throw new IllegalStateException("OpenPC could not locate the bundled qemu folder inside its jar.");
        }

        Path target = requireGameQemuDirectory();

        try {
            if (Files.exists(target) && isComplete(target)) {
                extracted.set(true);
                return;
            }
            if (Files.exists(target)) {
                deleteRecursively(target);
            }
            Files.createDirectories(target);

            try (Stream<Path> walk = Files.walk(source)) {
                List<Path> entries = walk.sorted().toList();
                for (Path current : entries) {
                    String relative = source.relativize(current).toString().replace('\\', '/');
                    Path destination = target.resolve(relative);

                    if (Files.isDirectory(current)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(current, destination, StandardCopyOption.REPLACE_EXISTING);
                        applyExecutablePermission(destination);
                    }
                }
            }

            if (!isComplete(target)) {
                throw new ExtractionFailure("Bundled QEMU extraction did not produce a usable binary.", null);
            }
            extracted.set(true);
        } catch (IOException error) {
            throw new ExtractionFailure("Failed to create the openpc/qemu directory underneath " + gameDir, error);
        }
    }

    private static boolean isComplete(Path dir) {
        try (Stream<Path> children = Files.list(dir)) {
            return children.map(path -> path.getFileName().toString())
                    .anyMatch(name -> name.equals("qemu-system-x86_64") || name.equals("qemu-system-x86_64.exe")
                            || name.equals("qemu-img") || name.equals("qemu-img.exe"));
        } catch (IOException error) {
            return false;
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
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

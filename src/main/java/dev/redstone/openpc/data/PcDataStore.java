package dev.redstone.openpc.data;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public final class PcDataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("openpc-data");
    private static final AtomicLong NEXT_ID_HINT = new AtomicLong(1);

    private PcDataStore() {
    }

    public static Path dataRoot() {
        return FabricLoader.getInstance().getGameDir().resolve("openpc/data");
    }

    public static Path pcDirectory(long pcId) {
        return dataRoot().resolve("pc_" + String.format("%06d", pcId));
    }

    public static Path configFile(long pcId) {
        return pcDirectory(pcId).resolve("pc.json");
    }

    public static Path diskFile(long pcId) {
        return pcDirectory(pcId).resolve("disk0.raw");
    }

    public static Path biosDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("openpc/qemu");
    }

    public static Path isoDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("openpc/isos");
    }

    public static Path isoFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        Path resolved = isoDirectory().resolve(fileName).normalize();
        if (!resolved.startsWith(isoDirectory().normalize())) {
            return null;
        }
        return resolved;
    }

    public static long allocateUniquePcId() {
        Path file = dataRoot().resolve("pc_next_id.txt");
        long reserved = NEXT_ID_HINT.getAndIncrement();
        try {
            Files.createDirectories(dataRoot());
            if (!Files.exists(file)) {
                Files.writeString(file, Long.toString(reserved + 1), StandardOpenOption.CREATE);
                return reserved;
            }
            long current = Long.parseLong(Files.readString(file).trim());
            Files.writeString(file, Long.toString(current + 1), StandardOpenOption.TRUNCATE_EXISTING);
            return current;
        } catch (IOException | NumberFormatException error) {
            return reserved;
        }
    }

    public static void writeConfigMirrorAsync(long pcId, PcConfig config) {
        CompletableFuture.runAsync(() -> writeConfigMirrorQuietly(pcId, config));
    }

    public static void writeConfigMirror(long pcId, PcConfig config) {
        try {
            Path file = configFile(pcId);
            Files.createDirectories(file.getParent());
            Files.writeString(file, PcSerialization.encodePretty(config), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException error) {
            throw new DataStoreException("Failed to write PC metadata for pc_" + pcId, error);
        }
    }

    private static void writeConfigMirrorQuietly(long pcId, PcConfig config) {
        try {
            writeConfigMirror(pcId, config);
        } catch (DataStoreException error) {
            LOGGER.error("Failed to mirror PC config for pc_{}", pcId, error);
        }
    }

    public static boolean hasPersistedDisk(long pcId) {
        return Files.exists(diskFile(pcId));
    }

    public static Path floppyFile(long pcId) {
        return pcDirectory(pcId).resolve("floppy.img");
    }

    public static Path opticalFile(long pcId) {
        return pcDirectory(pcId).resolve("optical.iso");
    }

    public static void ensureFloppyImage(long pcId) {
        Path file = floppyFile(pcId);
        if (Files.exists(file)) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            try (RandomAccessFile floppy = new RandomAccessFile(file.toFile(), "rw")) {
                floppy.setLength(1440L * 1024L);
            }
        } catch (IOException error) {
            throw new DataStoreException("Failed to create floppy image for pc_" + pcId, error);
        }
    }

    public static void ensureOpticalImage(long pcId) {
        Path file = opticalFile(pcId);
        if (Files.exists(file)) {
            return;
        }
        byte[] header = Iso9660Minimal.blankVolumeDescriptor();
        try {
            Files.createDirectories(file.getParent());
            try (java.io.OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                out.write(header);
                long remaining = 4L * 1024L * 1024L - header.length;
                byte[] zeros = new byte[4096];
                while (remaining > 0) {
                    int chunk = (int) Math.min(zeros.length, remaining);
                    out.write(zeros, 0, chunk);
                    remaining -= chunk;
                }
            }
        } catch (IOException error) {
            throw new DataStoreException("Failed to create optical image for pc_" + pcId, error);
        }
    }

    public static void ensureDiskImage(long pcId, long capacityMb) {
        Path disk = diskFile(pcId);
        if (Files.exists(disk)) {
            return;
        }
        try {
            Files.createDirectories(disk.getParent());
            long bytes = capacityMb * 1024L * 1024L;
            try (RandomAccessFile file = new RandomAccessFile(disk.toFile(), "rw")) {
                file.setLength(bytes);
            }
        } catch (IOException error) {
            throw new DataStoreException("Failed to create disk image for pc_" + pcId + " with " + capacityMb + " MB", error);
        }
    }

    public static class DataStoreException extends RuntimeException {
        public DataStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class Iso9660Minimal {
        private static byte[] blankVolumeDescriptor() {
            byte[] data = new byte[2048];
            data[0] = 1;
            String id = "OPENPC";
            byte[] idBytes = id.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(idBytes, 0, data, 1, Math.min(idBytes.length, 5));
            return data;
        }
    }
}
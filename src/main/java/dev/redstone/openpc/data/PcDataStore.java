package dev.redstone.openpc.data;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

    public static Path diskFile(long pcId, int slot) {
        Path legacy = rawDiskFile(pcId, slot);
        if (Files.exists(legacy)) {
            return legacy;
        }
        Path qcow2 = qcow2DiskFile(pcId, slot);
        if (Files.exists(qcow2)) {
            return qcow2;
        }
        return qemuImgAvailable() ? qcow2 : legacy;
    }

    public static Path qcow2DiskFile(long pcId, int slot) {
        return pcDirectory(pcId).resolve(slot == 0 ? "disk0.qcow2" : "disk" + slot + ".qcow2");
    }

    public static Path rawDiskFile(long pcId, int slot) {
        return pcDirectory(pcId).resolve(slot == 0 ? "disk0.raw" : "disk" + slot + ".raw");
    }

    public static String diskFormat(long pcId, int slot) {
        return diskFile(pcId, slot).getFileName().toString().endsWith(".qcow2") ? "qcow2" : "raw";
    }

    public static Path biosDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("openpc/qemu");
    }

    public static Path isoFile(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            Path resolved = Path.of(path).toAbsolutePath().normalize();
            String fileName = resolved.getFileName() == null ? "" : resolved.getFileName().toString();
            if (!fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".iso")) {
                return null;
            }
            return resolved;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static Path floppyMediaFile(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            Path resolved = Path.of(path).toAbsolutePath().normalize();
            String fileName = resolved.getFileName() == null ? "" : resolved.getFileName().toString();
            if (!fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".img")) {
                return null;
            }
            return resolved;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static java.util.List<Long> listPcIds() {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        Path root = dataRoot();
        if (!Files.isDirectory(root)) {
            return ids;
        }
        try (java.util.stream.Stream<Path> entries = Files.list(root)) {
            entries.forEach(entry -> {
                if (!Files.isDirectory(entry)) {
                    return;
                }
                String name = entry.getFileName() == null ? "" : entry.getFileName().toString();
                if (!name.startsWith("pc_")) {
                    return;
                }
                try {
                    ids.add(Long.parseLong(name.substring(3)));
                } catch (NumberFormatException ignored) {
                }
            });
        } catch (IOException error) {
            LOGGER.warn("Failed to list PCs in {}", root, error);
        }
        ids.sort(Long::compareTo);
        return ids;
    }

    public static void deletePcDirectory(long pcId) throws IOException {
        Path directory = pcDirectory(pcId);
        if (!Files.exists(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    LOGGER.warn("Failed to delete {} while removing pc_{}", path, pcId, error);
                }
            });
        }
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

    public static PcConfig readConfigMirror(long pcId) {
        try {
            Path file = configFile(pcId);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            return PcSerialization.decode(Files.readString(file));
        } catch (IOException error) {
            LOGGER.warn("Failed to read PC metadata for pc_{}", pcId, error);
            return null;
        }
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
        return Files.exists(diskFile(pcId, 0));
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
            try (java.io.RandomAccessFile floppy = new java.io.RandomAccessFile(file.toFile(), "rw")) {
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

    public static void ensureDiskImage(long pcId, int slot, long capacityMb) {
        if (capacityMb <= 0) {
            return;
        }
        Path disk = diskFile(pcId, slot);
        if (Files.exists(disk)) {
            if (diskMatchesCapacity(disk, capacityMb)) {
                return;
            }
            // Stale disk left over from an older default (e.g. the old 512 MB test disk).
            // Discard it so a fresh image at the configured capacity is created.
            try {
                Files.delete(disk);
            } catch (IOException error) {
                throw new DataStoreException("Failed to replace stale disk image " + disk + " for pc_" + pcId, error);
            }
        }
        try {
            Files.createDirectories(disk.getParent());
            long bytes = capacityMb * 1024L * 1024L;
            if (disk.getFileName().toString().endsWith(".qcow2")) {
                createQcow2Disk(disk, bytes);
            } else {
                createSparseRawDisk(disk, bytes);
            }
        } catch (IOException error) {
            throw new DataStoreException("Failed to create disk image for pc_" + pcId + " with " + capacityMb + " MB", error);
        }
    }

    public static void deleteDiskFile(long pcId, int slot) {
        try {
            Files.deleteIfExists(rawDiskFile(pcId, slot));
            Files.deleteIfExists(qcow2DiskFile(pcId, slot));
        } catch (IOException error) {
            LOGGER.warn("Failed to delete disk{} for pc_{}", slot, pcId, error);
        }
    }

    private static boolean diskMatchesCapacity(Path disk, long capacityMb) {
        long required = capacityMb * 1024L * 1024L;
        String name = disk.getFileName() == null ? "" : disk.getFileName().toString();
        if (name.endsWith(".qcow2")) {
            long virtual = qcow2VirtualSize(disk);
            return virtual > 0 && virtual == required;
        }
        try {
            return Files.size(disk) == required;
        } catch (IOException error) {
            return false;
        }
    }

    private static long qcow2VirtualSize(Path disk) {
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(disk, StandardOpenOption.READ)) {
            java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(32);
            if (channel.read(header) < 32) {
                return -1;
            }
            header.flip();
            if (header.getInt() != 0x514649fb) {
                return -1;
            }
            // qcow2 header: magic(4) version(4) backing_file_offset(8) backing_file_size(4)
            //               cluster_bits(1) l2_bits(1) padding(2) virtual_size(8)
            return header.getLong(24);
        } catch (IOException error) {
            return -1;
        }
    }

    private static boolean qemuImgAvailable() {
        Path qemuImg = dev.redstone.openpc.client.QemuEnvironment.qemuImgPath();
        return qemuImg != null && Files.isRegularFile(qemuImg);
    }

    private static void createQcow2Disk(Path disk, long bytes) throws IOException {
        Path qemuImg = dev.redstone.openpc.client.QemuEnvironment.qemuImgPath();
        if (qemuImg != null && Files.isRegularFile(qemuImg)) {
            ProcessBuilder builder = new ProcessBuilder(qemuImg.toAbsolutePath().toString(), "create", "-f", "qcow2",
                    disk.toAbsolutePath().toString(), Long.toString(bytes));
            builder.redirectErrorStream(true);
            Path bundledQemu = biosDirectory().toAbsolutePath().normalize();
            if (Files.isDirectory(bundledQemu) && qemuImg.toAbsolutePath().normalize().startsWith(bundledQemu)) {
                builder.environment().put("LD_LIBRARY_PATH", bundledQemu.toString());
            }
            try {
                Process process = builder.start();
                String output = new String(process.getInputStream().readAllBytes());
                boolean exited = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
                if (!exited) {
                    process.destroyForcibly();
                    throw new IOException("qemu-img timed out while creating " + disk);
                }
                if (process.exitValue() != 0) {
                    throw new IOException("qemu-img exited with code " + process.exitValue() + ": " + output.trim());
                }
                return;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while creating qcow2 image", error);
            }
        }
        throw new IOException("qemu-img was not found; cannot create qcow2 disk image.");
    }

    private static void createSparseRawDisk(Path disk, long bytes) throws IOException {
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(disk,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SPARSE)) {
            channel.position(bytes - 1);
            channel.write(java.nio.ByteBuffer.wrap(new byte[]{0}));
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

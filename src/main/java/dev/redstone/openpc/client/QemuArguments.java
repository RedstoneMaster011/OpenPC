package dev.redstone.openpc.client;

import dev.redstone.openpc.data.PcConfig;
import dev.redstone.openpc.data.PcDataStore;
import dev.redstone.openpc.hardware.HardwareDefinition;
import dev.redstone.openpc.hardware.HardwareDefinitions;
import dev.redstone.openpc.hardware.HardwareRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class QemuArguments {

    private QemuArguments() {
    }

    public static List<String> build(PcConfig config, int vncDisplay) {
        List<String> args = new ArrayList<>();
        args.add(QemuEnvironment.binaryPath().toAbsolutePath().toString());

        if (QemuEnvironment.detectOs() == QemuEnvironment.Os.WINDOWS) {
            args.add("-L");
            args.add(QemuSetup.requireGameQemuDirectory().toAbsolutePath().toString());
        }

        List<String> cpuIds = config.installedCpuIds();
        HardwareDefinition cpu = cpuIds.isEmpty() ? null : HardwareRegistry.find(cpuIds.get(0)).orElse(null);
        int cores = 0;
        int threads = 0;
        for (String id : cpuIds) {
            HardwareDefinition definition = HardwareRegistry.find(id).orElse(null);
            if (definition == null) {
                continue;
            }
            cores += Math.max(1, definition.getInt(HardwareDefinitions.PROP_CORES, 1));
            threads += Math.min(2, Math.max(1, definition.getInt(HardwareDefinitions.PROP_THREADS, 1)));
        }
        if (cores == 0) {
            cores = 1;
        }
        // threads is the Hyper-Threading / SMT multiplier per core (QEMU allows 1 or 2)
        threads = Math.min(2, Math.max(1, threads));
        String cpuModel = cpuModel(config, cpu);
        long ramMb = Math.max(128, config.installedRamMegabytes());

        args.add("-machine");
        args.add(QemuEnvironment.configuredMachine(true));
        args.add("-cpu");
        args.add(cpuModel);
        args.add("-smp");
        args.add("cores=" + cores + ",threads=" + threads);
        args.add("-m");
        args.add(Long.toString(ramMb));
        if (QemuEnvironment.detectOs() == QemuEnvironment.Os.LINUX) {
            args.add("-accel");
            args.add("kvm");
        } else if (QemuEnvironment.detectOs() == QemuEnvironment.Os.WINDOWS) {
            args.add("-accel");
            args.add("whpx");
        }
        boolean hasIso = false;
        Path iso = PcDataStore.isoFile(config.isoFileName());
        if (iso != null && Files.isRegularFile(iso)) {
            hasIso = true;
        }
        args.add("-boot");
        args.add("order=" + (hasIso ? "dc" : "c") + ",menu=off");
        args.add("-no-reboot");

        int nextIndex = 0;
        for (int slot = 0; slot < config.storageSlots().size(); slot++) {
            if (config.storageAt(slot) == null) {
                continue;
            }
            long capacityMb = config.storageMegabytesAt(slot);
            PcDataStore.ensureDiskImage(config.pcId(), slot, capacityMb);
            args.add("-drive");
            args.add("file=" + PcDataStore.diskFile(config.pcId(), slot).toAbsolutePath()
                    + ",format=" + PcDataStore.diskFormat(config.pcId(), slot)
                    + ",media=disk,index=" + nextIndex + ",cache=writeback");
            nextIndex++;
        }

        if (hasIso) {
            args.add("-drive");
            args.add("file=" + iso.toAbsolutePath() + ",format=raw,media=cdrom,index=" + nextIndex + ",readonly=on");
            nextIndex++;
        }

        if (config.opticalId() != null) {
            Path cdrom = PcDataStore.isoFile(config.cdromFileName());
            String media = cdrom != null && Files.isRegularFile(cdrom)
                    ? cdrom.toAbsolutePath().toString()
                    : PcDataStore.opticalFile(config.pcId()).toAbsolutePath().toString();
            if (cdrom == null || !Files.isRegularFile(cdrom)) {
                PcDataStore.ensureOpticalImage(config.pcId());
            }
            args.add("-drive");
            args.add("file=" + media + ",format=raw,media=cdrom,index=" + nextIndex + ",readonly=on");
            nextIndex++;
        }

        if (config.floppyId() != null) {
            Path floppy = PcDataStore.floppyMediaFile(config.floppyFileName());
            String floppyMedia = floppy != null && Files.isRegularFile(floppy)
                    ? floppy.toAbsolutePath().toString()
                    : PcDataStore.floppyFile(config.pcId()).toAbsolutePath().toString();
            if (floppy == null || !Files.isRegularFile(floppy)) {
                PcDataStore.ensureFloppyImage(config.pcId());
            }
            args.add("-drive");
            args.add("file=" + floppyMedia + ",format=raw,if=floppy,index=0,media=disk,readonly="
                    + (config.floppyLocked() ? "on" : "off"));
        }

        boolean hasNetwork = config.networkId() != null || hasIntegrated(config, HardwareDefinitions.PROP_INTEGRATED_NETWORK);
        if (hasNetwork) {
            args.add("-netdev");
            args.add("user,id=net0");
            args.add("-device");
            args.add("e1000e,netdev=net0");
        }

        boolean hasAudio = config.audioId() != null || hasIntegrated(config, HardwareDefinitions.PROP_INTEGRATED_AUDIO);
        if (hasAudio) {
            String backend = QemuEnvironment.pickSoundBackend();
            args.add("-audiodev");
            args.add(backend + ",id=snd0");
            args.add("-device");
            args.add("intel-hda");
            args.add("-device");
            args.add("hda-output,audiodev=snd0");
        }

        String vga = vgaDevice(config);
        if (vga != null) {
            if (vga.equals("std")) {
                args.add("-vga");
                args.add("std");
            } else {
                args.add("-device");
                args.add(vga);
            }
        }

        args.add("-usb");
        args.add("-device");
        args.add("usb-tablet");

        args.add("-display");
        args.add("none");
        args.add("-vnc");
        args.add("127.0.0.1:" + vncDisplay);
        args.add("-qmp");
        args.add("tcp:127.0.0.1:" + qmpPortFor(config.pcId()) + ",server=on,wait=off");

        for (String expansion : config.installedExpansionIds()) {
            HardwareDefinition definition = HardwareRegistry.find(expansion).orElse(null);
            if (definition == null) {
                continue;
            }
            String device = definition.qemuDevice();
            if (device == null || device.isEmpty() || device.equals("e1000") || device.equals("e1000e") || device.equals("AC97")) {
                continue;
            }
            if (device.equals("pci-serial")) {
                args.add("-chardev");
                args.add("null,id=serial" + expansion);
                args.add("-device");
                args.add("pci-serial,chardev=serial" + expansion);
            } else if (device.equals("pci-parallel")) {
                args.add("-chardev");
                args.add("null,id=parallel" + expansion);
                args.add("-device");
                args.add("isa-parallel,chardev=parallel" + expansion);
            } else {
                args.add("-device");
                args.add(device);
            }
        }

        return args;
    }

    private static String cpuModel(PcConfig config, HardwareDefinition cpu) {
        String type = config.cpuType();
        if (type.equals("amd")) {
            // Safe, optimized AMD baseline setup
            return "EPYC,vendor=AuthenticAMD,+kvm_pv_unhalt,+kvm_pv_eoi,+hypervisor";
        }
        if (type.equals("intel")) {
            // Strict Intel Skylake layout, vendor masked Intel, contradicting AMD page structures explicitly disabled
            return "Skylake-Client,vendor=GenuineIntel,+hypervisor,+invtsc,-vme,-pdpe1gb,check";
        }
        String model = cpu == null ? "host" : cpu.getProperty(HardwareDefinitions.PROP_QEMU_MODEL, "host");
        if (QemuEnvironment.detectOs() == QemuEnvironment.Os.WINDOWS && model.equals("host")) {
            return "max";
        }
        return model;
    }

    public static int vncDisplayFor(long pcId) {
        return (int) ((pcId % 100) + 1);
    }

    public static int vncPortFor(long pcId) {
        return 5900 + vncDisplayFor(pcId);
    }

    public static int qmpPortFor(long pcId) {
        return 44000 + vncDisplayFor(pcId);
    }

    private static boolean hasIntegrated(PcConfig config, String property) {
        HardwareDefinition motherboard = HardwareRegistry.find(config.motherboardId()).orElse(null);
        return motherboard != null && motherboard.getBoolean(property, false);
    }

    private static String vgaDevice(PcConfig config) {
        HardwareDefinition gpu = config.gpuId() == null ? null : HardwareRegistry.find(config.gpuId()).orElse(null);
        if (gpu != null) {
            return gpu.getProperty(HardwareDefinitions.PROP_VGA, "std");
        }
        boolean integrated = hasIntegrated(config, HardwareDefinitions.PROP_INTEGRATED_GRAPHICS);
        return integrated ? "std" : null;
    }
}

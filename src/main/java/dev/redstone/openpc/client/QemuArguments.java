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

        HardwareDefinition cpu = HardwareRegistry.find(config.cpuId()).orElse(null);
        int cores = cpu == null ? 1 : Math.max(1, cpu.getInt(HardwareDefinitions.PROP_CORES, 1));
        String cpuModel = cpu == null ? "qemu64" : cpu.getProperty(HardwareDefinitions.PROP_QEMU_MODEL, "qemu64");
        long ramMb = Math.max(128, config.installedRamMegabytes());

        args.add("-machine");
        args.add(QemuEnvironment.configuredMachine(true));
        args.add("-cpu");
        args.add(cpuModel);
        args.add("-smp");
        args.add("cores=" + cores);
        args.add("-m");
        args.add(Long.toString(ramMb));
        boolean hasIso = false;
        Path iso = PcDataStore.isoFile(config.isoFileName());
        if (iso != null && Files.isRegularFile(iso)) {
            hasIso = true;
        }
        args.add("-boot");
        args.add("order=" + (hasIso ? "dc" : "c") + ",menu=off");
        args.add("-no-reboot");

        PcDataStore.ensureDiskImage(config.pcId(), config.installedStorageMegabytes());
        args.add("-drive");
        args.add("file=" + PcDataStore.diskFile(config.pcId()).toAbsolutePath()
                + ",format=" + PcDataStore.diskFormat(config.pcId()) + ",media=disk,index=0,cache=writeback");

        if (hasIso) {
            args.add("-drive");
            args.add("file=" + iso.toAbsolutePath() + ",format=raw,media=cdrom,index=1,readonly=on");
        } else if (config.opticalId() != null) {
            PcDataStore.ensureOpticalImage(config.pcId());
            args.add("-drive");
            args.add("file=" + PcDataStore.opticalFile(config.pcId()).toAbsolutePath()
                    + ",format=raw,media=cdrom,index=1,readonly=on");
        }

        if (config.floppyId() != null) {
            PcDataStore.ensureFloppyImage(config.pcId());
            args.add("-fda");
            args.add(PcDataStore.floppyFile(config.pcId()).toAbsolutePath().toString());
        }

        boolean hasNetwork = config.networkId() != null || hasIntegrated(config, HardwareDefinitions.PROP_INTEGRATED_NETWORK);
        if (hasNetwork) {
            args.add("-netdev");
            args.add("user,id=net0");
            args.add("-device");
            args.add("e1000,netdev=net0");
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

        args.add("-display");
        args.add("none");
        args.add("-vnc");
        args.add("127.0.0.1:" + vncDisplay);

        for (String expansion : config.installedExpansionIds()) {
            HardwareDefinition definition = HardwareRegistry.find(expansion).orElse(null);
            if (definition == null) {
                continue;
            }
            String device = definition.qemuDevice();
            if (device == null || device.isEmpty() || device.equals("e1000") || device.equals("AC97")) {
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
                args.add("pci-parallel,chardev=parallel" + expansion);
            } else {
                args.add("-device");
                args.add(device);
            }
        }

        return args;
    }

    public static int vncDisplayFor(long pcId) {
        return (int) ((pcId % 100) + 1);
    }

    public static int vncPortFor(long pcId) {
        return 5900 + vncDisplayFor(pcId);
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

package dev.redstone.openpc.hardware;

import static dev.redstone.openpc.hardware.HardwareCategory.AUDIO;
import static dev.redstone.openpc.hardware.HardwareCategory.CPU;
import static dev.redstone.openpc.hardware.HardwareCategory.EXPANSION;
import static dev.redstone.openpc.hardware.HardwareCategory.FLOPPY;
import static dev.redstone.openpc.hardware.HardwareCategory.GPU;
import static dev.redstone.openpc.hardware.HardwareCategory.MOTHERBOARD;
import static dev.redstone.openpc.hardware.HardwareCategory.NETWORK;
import static dev.redstone.openpc.hardware.HardwareCategory.OPTICAL;
import static dev.redstone.openpc.hardware.HardwareCategory.RAM;
import static dev.redstone.openpc.hardware.HardwareCategory.STORAGE;

public final class HardwareDefinitions {

    public static final String PROP_RAM_SLOTS = "ram_slots";
    public static final String PROP_MAX_RAM_MB = "max_ram_mb";
    public static final String PROP_EXPANSION_SLOTS = "expansion_slots";
    public static final String PROP_STORAGE_BAYS = "storage_bays";
    public static final String PROP_USB_PORTS = "usb_ports";
    public static final String PROP_INTEGRATED_AUDIO = "integrated_audio";
    public static final String PROP_INTEGRATED_NETWORK = "integrated_network";
    public static final String PROP_INTEGRATED_GRAPHICS = "integrated_graphics";
    public static final String PROP_MACHINE = "machine";
    public static final String PROP_CORES = "cores";
    public static final String PROP_THREADS = "threads";
    public static final String PROP_ARCHITECTURE = "architecture";
    public static final String PROP_QEMU_MODEL = "qemu_model";
    public static final String PROP_CAPACITY_MB = "capacity_mb";
    public static final String PROP_DISK_FORMAT = "disk_format";
    public static final String PROP_DISK_INTERFACE = "disk_interface";
    public static final String PROP_VGA = "vga";
    public static final String PROP_SLOT_USAGE = "slot_usage";

    private HardwareDefinitions() {
    }

    public static void registerAll() {
        registerMotherboards();
        registerCpus();
        registerRam();
        registerStorage();
        registerGpus();
        registerAudio();
        registerNetwork();
        registerOptical();
        registerFloppy();
        registerExpansion();
    }

    private static void registerMotherboards() {
        HardwareRegistry.register(HardwareDefinition.of("motherboard_1", MOTHERBOARD, "", "motherboard")
                .property(PROP_RAM_SLOTS, 2)
                .property(PROP_MAX_RAM_MB, 4096)
                .property(PROP_EXPANSION_SLOTS, 4)
                .property(PROP_STORAGE_BAYS, 2)
                .property(PROP_USB_PORTS, 2)
                .property(PROP_INTEGRATED_AUDIO, false)
                .property(PROP_INTEGRATED_NETWORK, false)
                .property(PROP_INTEGRATED_GRAPHICS, false)
                .property(PROP_MACHINE, "pc"));

        HardwareRegistry.register(HardwareDefinition.of("motherboard_2", MOTHERBOARD, "", "motherboard")
                .property(PROP_RAM_SLOTS, 4)
                .property(PROP_MAX_RAM_MB, 16384)
                .property(PROP_EXPANSION_SLOTS, 6)
                .property(PROP_STORAGE_BAYS, 4)
                .property(PROP_USB_PORTS, 4)
                .property(PROP_INTEGRATED_AUDIO, true)
                .property(PROP_INTEGRATED_NETWORK, false)
                .property(PROP_INTEGRATED_GRAPHICS, false)
                .property(PROP_MACHINE, "pc"));

        HardwareRegistry.register(HardwareDefinition.of("motherboard_3", MOTHERBOARD, "", "motherboard")
                .property(PROP_RAM_SLOTS, 4)
                .property(PROP_MAX_RAM_MB, 65536)
                .property(PROP_EXPANSION_SLOTS, 6)
                .property(PROP_STORAGE_BAYS, 6)
                .property(PROP_USB_PORTS, 6)
                .property(PROP_INTEGRATED_AUDIO, true)
                .property(PROP_INTEGRATED_NETWORK, true)
                .property(PROP_INTEGRATED_GRAPHICS, true)
                .property(PROP_MACHINE, "pc"));
    }

    private static void registerCpus() {
        HardwareRegistry.register(HardwareDefinition.of("cpu_2core", CPU, "", "cpu")
                .property(PROP_CORES, 2)
                .property(PROP_THREADS, 2)
                .property(PROP_ARCHITECTURE, "x86_64")
                .property(PROP_QEMU_MODEL, "host"));

        HardwareRegistry.register(HardwareDefinition.of("cpu_4core", CPU, "", "cpu")
                .property(PROP_CORES, 4)
                .property(PROP_THREADS, 4)
                .property(PROP_ARCHITECTURE, "x86_64")
                .property(PROP_QEMU_MODEL, "host"));

        HardwareRegistry.register(HardwareDefinition.of("cpu_pro_2core", CPU, "", "cpu")
                .property(PROP_CORES, 2)
                .property(PROP_THREADS, 4)
                .property(PROP_ARCHITECTURE, "x86_64")
                .property(PROP_QEMU_MODEL, "host"));

        HardwareRegistry.register(HardwareDefinition.of("cpu_pro_4core", CPU, "", "cpu")
                .property(PROP_CORES, 4)
                .property(PROP_THREADS, 8)
                .property(PROP_ARCHITECTURE, "x86_64")
                .property(PROP_QEMU_MODEL, "host"));

        HardwareRegistry.register(HardwareDefinition.of("cpu_8core", CPU, "", "cpu")
                .property(PROP_CORES, 8)
                .property(PROP_THREADS, 8)
                .property(PROP_ARCHITECTURE, "x86_64")
                .property(PROP_QEMU_MODEL, "host"));
    }

    private static void registerRam() {
        registerRamSize("ram_256mb", 256);
        registerRamSize("ram_512mb", 512);
        registerRamSize("ram_1gb", 1024);
        registerRamSize("ram_2gb", 2048);
        registerRamSize("ram_4gb", 4096);
        registerRamSize("ram_8gb", 8192);
        registerRamSize("ram_16gb", 16384);
    }

    private static void registerRamSize(String id, int megabytes) {
        HardwareRegistry.register(HardwareDefinition.of(id, RAM, "", "ram")
                .property(PROP_CAPACITY_MB, megabytes));
    }

    private static void registerStorage() {
        registerStorageSize("storage_1gb", 1024);
        registerStorageSize("storage_5gb", 5120);
        registerStorageSize("storage_10gb", 10240);
        registerStorageSize("storage_20gb", 20480);
        registerStorageSize("storage_50gb", 51200);
        registerStorageSize("storage_100gb", 102400);
        registerStorageSize("storage_500gb", 512000);
    }

    private static void registerStorageSize(String id, long megabytes) {
        HardwareRegistry.register(HardwareDefinition.of(id, STORAGE, "", "storage")
                .property(PROP_CAPACITY_MB, megabytes)
                .property(PROP_DISK_FORMAT, "raw")
                .property(PROP_DISK_INTERFACE, "ide"));
    }

    private static void registerGpus() {
        HardwareRegistry.register(HardwareDefinition.of("gpu_basic", GPU, "", "gpu")
                .property(PROP_VGA, "std"));

        HardwareRegistry.register(HardwareDefinition.of("gpu_advanced", GPU, "", "gpu")
                .property(PROP_VGA, "virtio-vga"));
    }

    private static void registerAudio() {
        HardwareRegistry.register(HardwareDefinition.of("sound_card", AUDIO, "AC97", "pci_card"));
    }

    private static void registerNetwork() {
        HardwareRegistry.register(HardwareDefinition.of("network_card", NETWORK, "e1000e", "pci_card"));
    }

    private static void registerOptical() {
        HardwareRegistry.register(HardwareDefinition.of("optical_drive", OPTICAL, "", "optical"));
    }

    private static void registerFloppy() {
        HardwareRegistry.register(HardwareDefinition.of("floppy_drive", FLOPPY, "", "floppy"));
    }

    private static void registerExpansion() {
        HardwareRegistry.register(HardwareDefinition.of("scsi_card", EXPANSION, "lsi53c895a", "pci_card")
                .property(PROP_SLOT_USAGE, 1));

        HardwareRegistry.register(HardwareDefinition.of("serial_card", EXPANSION, "pci-serial", "pci_card")
                .property(PROP_SLOT_USAGE, 1));

        HardwareRegistry.register(HardwareDefinition.of("parallel_card", EXPANSION, "pci-parallel", "pci_card")
                .property(PROP_SLOT_USAGE, 1));
    }
}
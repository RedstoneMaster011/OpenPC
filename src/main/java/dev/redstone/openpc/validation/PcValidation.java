package dev.redstone.openpc.validation;

import dev.redstone.openpc.data.PcConfig;
import dev.redstone.openpc.hardware.HardwareCategory;
import dev.redstone.openpc.hardware.HardwareDefinition;
import dev.redstone.openpc.hardware.HardwareDefinitions;
import dev.redstone.openpc.hardware.HardwareRegistry;
import net.minecraft.text.Text;

public final class PcValidation {

    private PcValidation() {
    }

    public static PcValidationResult validate(PcConfig config) {
        PcValidationResult.Builder result = PcValidationResult.builder();

        HardwareDefinition motherboard = present(config.motherboardId());
        HardwareDefinition cpu = present(config.cpuId());

        if (motherboard == null) {
            result.error(Text.translatable("openpc.error.missing_motherboard"));
        }
        if (cpu == null) {
            result.error(Text.translatable("openpc.error.missing_cpu"));
        }

        if (motherboard != null && cpu != null) {
            validateCpuCompatibility(result, motherboard, cpu);
        }

        for (String id : config.ramSlots()) {
            if (id != null && !HardwareRegistry.find(id).isPresent()) {
                result.error(Text.translatable("openpc.error.unknown_ram", id));
            }
        }
        if (motherboard != null) {
            validateRam(result, config, motherboard);
        }

        if (config.storageId() == null) {
            result.error(Text.translatable("openpc.error.missing_storage"));
        } else {
            HardwareDefinition storage = HardwareRegistry.find(config.storageId()).orElse(null);
            if (storage == null) {
                result.error(Text.translatable("openpc.error.unknown_storage", config.storageId()));
            } else if (storage.category() != HardwareCategory.STORAGE) {
                result.error(Text.translatable("openpc.error.wrong_storage_category"));
            }
        }

        validateVideo(result, config, motherboard);

        for (String id : config.expansionSlots()) {
            validateExpansion(result, id);
        }
        if (motherboard != null) {
            validateExpansionCapacity(result, config, motherboard);
        }

        validateKnownComponent(result, config.gpuId());
        validateKnownComponent(result, config.audioId());
        validateKnownComponent(result, config.networkId());
        validateKnownComponent(result, config.opticalId());
        validateKnownComponent(result, config.floppyId());

        return result.build();
    }

    public static PcValidationResult validateEditable(PcConfig config) {
        PcValidationResult.Builder result = PcValidationResult.builder();

        HardwareDefinition motherboard = present(config.motherboardId());
        HardwareDefinition cpu = present(config.cpuId());

        if (config.motherboardId() != null && motherboard == null) {
            result.error(Text.translatable("openpc.error.unknown_component", config.motherboardId()));
        }
        if (config.cpuId() != null && cpu == null) {
            result.error(Text.translatable("openpc.error.unknown_component", config.cpuId()));
        }
        if (motherboard != null && cpu != null) {
            validateCpuCompatibility(result, motherboard, cpu);
        }

        for (String id : config.ramSlots()) {
            if (id != null && !HardwareRegistry.find(id).isPresent()) {
                result.error(Text.translatable("openpc.error.unknown_ram", id));
            }
        }
        if (motherboard != null) {
            validateRamSlots(result, config, motherboard);
        }

        if (config.storageId() != null) {
            HardwareDefinition storage = HardwareRegistry.find(config.storageId()).orElse(null);
            if (storage == null) {
                result.error(Text.translatable("openpc.error.unknown_storage", config.storageId()));
            } else if (storage.category() != HardwareCategory.STORAGE) {
                result.error(Text.translatable("openpc.error.wrong_storage_category"));
            }
        }

        for (String id : config.expansionSlots()) {
            validateExpansion(result, id);
        }
        if (motherboard != null) {
            validateExpansionCapacity(result, config, motherboard);
        }

        validateKnownComponent(result, config.gpuId());
        validateKnownComponent(result, config.audioId());
        validateKnownComponent(result, config.networkId());
        validateKnownComponent(result, config.opticalId());
        validateKnownComponent(result, config.floppyId());

        return result.build();
    }

    private static HardwareDefinition present(String id) {
        if (id == null) {
            return null;
        }
        return HardwareRegistry.find(id).orElse(null);
    }

    private static void validateCpuCompatibility(PcValidationResult.Builder result, HardwareDefinition motherboard, HardwareDefinition cpu) {
        if (cpu.category() != HardwareCategory.CPU) {
            result.error(Text.translatable("openpc.error.wrong_cpu_category"));
            return;
        }
        if (cpu.getInt(HardwareDefinitions.PROP_CORES, 0) < 1) {
            result.error(Text.translatable("openpc.error.cpu_no_cores", cpu.displayName()));
        }
        if (!cpu.getProperty(HardwareDefinitions.PROP_QEMU_MODEL, "").isBlank()) {
            String architecture = cpu.getProperty(HardwareDefinitions.PROP_ARCHITECTURE, "");
            String machine = motherboard.getProperty(HardwareDefinitions.PROP_MACHINE, "pc");
            if (!architecture.equals("x86_64") || (!machine.equals("pc") && !machine.equals("q35"))) {
                result.error(Text.translatable("openpc.error.cpu_incompatible", cpu.displayName(), motherboard.displayName()));
            }
        }
    }

    private static void validateRam(PcValidationResult.Builder result, PcConfig config, HardwareDefinition motherboard) {
        if (config.installedRamCount() == 0) {
            result.error(Text.translatable("openpc.error.missing_ram"));
            return;
        }
        validateRamSlots(result, config, motherboard);
    }

    private static void validateRamSlots(PcValidationResult.Builder result, PcConfig config, HardwareDefinition motherboard) {
        int slots = motherboard.getInt(HardwareDefinitions.PROP_RAM_SLOTS, 0);
        long maxRamMb = motherboard.getLong(HardwareDefinitions.PROP_MAX_RAM_MB, 0);

        for (int i = 0; i < config.ramSlots().size(); i++) {
            String id = config.ramSlots().get(i);
            if (id == null) {
                continue;
            }
            if (i >= slots) {
                result.error(Text.translatable("openpc.error.ram_slot_overflow", i + 1, slots));
            }
            HardwareDefinition module = HardwareRegistry.find(id).orElse(null);
            if (module == null || module.category() != HardwareCategory.RAM) {
                result.error(Text.translatable("openpc.error.ram_invalid_slot", i + 1));
                continue;
            }
            long capacityMb = module.getLong(HardwareDefinitions.PROP_CAPACITY_MB, 0);
            if (maxRamMb > 0 && capacityMb > maxRamMb) {
                result.error(Text.translatable("openpc.error.ram_over_max", module.displayName(), motherboard.displayName()));
            }
        }
        if (maxRamMb > 0 && config.installedRamMegabytes() > maxRamMb) {
            result.error(Text.translatable("openpc.error.ram_total_over_max", config.installedRamMegabytes(), maxRamMb));
        }
    }

    private static void validateVideo(PcValidationResult.Builder result, PcConfig config, HardwareDefinition motherboard) {
        boolean hasGpu = config.gpuId() != null;
        boolean integrated = motherboard != null && motherboard.getBoolean(HardwareDefinitions.PROP_INTEGRATED_GRAPHICS, false);
        if (!hasGpu && !integrated) {
            result.error(Text.translatable("openpc.error.missing_gpu"));
        }
    }

    private static void validateExpansion(PcValidationResult.Builder result, String id) {
        if (id == null) {
            return;
        }
        HardwareDefinition definition = HardwareRegistry.find(id).orElse(null);
        if (definition == null) {
            result.error(Text.translatable("openpc.error.unknown_expansion", id));
        } else if (definition.category() != HardwareCategory.EXPANSION
                && definition.category() != HardwareCategory.GPU
                && definition.category() != HardwareCategory.AUDIO
                && definition.category() != HardwareCategory.NETWORK) {
            result.error(Text.translatable("openpc.error.not_an_expansion_card", definition.displayName()));
        }
    }

    private static void validateExpansionCapacity(PcValidationResult.Builder result, PcConfig config, HardwareDefinition motherboard) {
        int slots = motherboard.getInt(HardwareDefinitions.PROP_EXPANSION_SLOTS, 0);
        int used = 0;
        for (String id : config.expansionSlots()) {
            if (id != null) {
                used++;
            }
        }
        if (used > slots) {
            result.error(Text.translatable("openpc.error.too_many_expansion", used, slots));
        }
    }

    private static void validateKnownComponent(PcValidationResult.Builder result, String id) {
        if (id == null) {
            return;
        }
        if (!HardwareRegistry.find(id).isPresent()) {
            result.error(Text.translatable("openpc.error.unknown_component", id));
        }
    }
}

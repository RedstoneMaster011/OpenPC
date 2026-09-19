package dev.redstone.openpc.hardware;

public final class HardwareCapacities {

    private HardwareCapacities() {
    }

    public static long ramMegabytes(String definitionId) {
        if (definitionId == null) {
            return 0;
        }
        return HardwareRegistry.find(definitionId)
                .map(definition -> definition.getLong(HardwareDefinitions.PROP_CAPACITY_MB, 0))
                .orElse(0L);
    }

    public static long storageMegabytes(String definitionId) {
        if (definitionId == null) {
            return 512;
        }
        return HardwareRegistry.find(definitionId)
                .map(definition -> definition.getLong(HardwareDefinitions.PROP_CAPACITY_MB, 512))
                .orElse(512L);
    }
}
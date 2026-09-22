package dev.redstone.openpc.data;

import dev.redstone.openpc.hardware.HardwareCapacities;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PcConfig {

    public static final int MAX_STORAGE_SLOTS = 5;
    public static final int MAX_CPU_SLOTS = 4;

    private final long pcId;
    private String name;
    private String motherboardId;
    private final List<String> cpuSlots;
    private final List<String> ramSlots;
    private final List<String> storageSlots;
    private String gpuId;
    private String audioId;
    private String networkId;
    private String opticalId;
    private String floppyId;
    private boolean floppyLocked;
    private final List<String> expansionSlots;
    private String isoFileName;
    private String cdromFileName;
    private String floppyFileName;
    private String cpuType;
    private PcPowerState powerState;

    public PcConfig(long pcId) {
        this.pcId = pcId;
        this.cpuSlots = new ArrayList<>();
        this.ramSlots = new ArrayList<>();
        this.storageSlots = new ArrayList<>();
        this.expansionSlots = new ArrayList<>();
        this.powerState = PcPowerState.OFF;
        this.cpuType = "host";
    }

    public PcConfig copy() {
        return copyWithPcId(pcId);
    }

    public PcConfig copyWithPcId(long newPcId) {
        PcConfig copy = new PcConfig(newPcId);
        copy.motherboardId = motherboardId;
        copy.cpuSlots.addAll(cpuSlots);
        copy.ramSlots.addAll(ramSlots);
        copy.storageSlots.addAll(storageSlots);
        copy.gpuId = gpuId;
        copy.audioId = audioId;
        copy.networkId = networkId;
        copy.opticalId = opticalId;
        copy.floppyId = floppyId;
        copy.floppyLocked = floppyLocked;
        copy.expansionSlots.addAll(expansionSlots);
        copy.isoFileName = isoFileName;
        copy.cdromFileName = cdromFileName;
        copy.floppyFileName = floppyFileName;
        copy.cpuType = cpuType;
        copy.name = name;
        copy.powerState = powerState;
        return copy;
    }

    public long pcId() {
        return pcId;
    }

    public String motherboardId() {
        return motherboardId;
    }

    public void setMotherboardId(String motherboardId) {
        this.motherboardId = motherboardId;
    }

    public List<String> cpuSlots() {
        return cpuSlots;
    }

    public String cpuAt(int index) {
        if (index < 0 || index >= cpuSlots.size()) {
            return null;
        }
        return cpuSlots.get(index);
    }

    public void setCpuAt(int index, String definitionId) {
        while (cpuSlots.size() <= index) {
            cpuSlots.add(null);
        }
        if (cpuSlots.size() > MAX_CPU_SLOTS) {
            cpuSlots.subList(MAX_CPU_SLOTS, cpuSlots.size()).clear();
        }
        cpuSlots.set(index, definitionId);
    }

    public void removeCpuAt(int index) {
        if (index >= 0 && index < cpuSlots.size()) {
            cpuSlots.set(index, null);
        }
    }

    public int installedCpuCount() {
        int count = 0;
        for (String id : cpuSlots) {
            if (id != null) {
                count++;
            }
        }
        return count;
    }

    public List<String> installedCpuIds() {
        List<String> result = new ArrayList<>();
        for (String id : cpuSlots) {
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    public List<String> ramSlots() {
        return ramSlots;
    }

    public String ramAt(int index) {
        if (index < 0 || index >= ramSlots.size()) {
            return null;
        }
        return ramSlots.get(index);
    }

    public void setRamAt(int index, String definitionId) {
        while (ramSlots.size() <= index) {
            ramSlots.add(null);
        }
        ramSlots.set(index, definitionId);
    }

    public void removeRamAt(int index) {
        if (index >= 0 && index < ramSlots.size()) {
            ramSlots.set(index, null);
        }
    }

    public int installedRamCount() {
        int count = 0;
        for (String slot : ramSlots) {
            if (slot != null) {
                count++;
            }
        }
        return count;
    }

    public long installedRamMegabytes() {
        long total = 0;
        for (String slot : ramSlots) {
            if (slot != null) {
                total += HardwareCapacities.ramMegabytes(slot);
            }
        }
        return total;
    }

    public long installedStorageMegabytes() {
        long total = 0;
        for (String id : installedStorageIds()) {
            total += HardwareCapacities.storageMegabytes(id);
        }
        return total;
    }

    public List<String> storageSlots() {
        return storageSlots;
    }

    public String storageAt(int index) {
        if (index < 0 || index >= storageSlots.size()) {
            return null;
        }
        return storageSlots.get(index);
    }

    public void setStorageAt(int index, String definitionId) {
        while (storageSlots.size() <= index) {
            storageSlots.add(null);
        }
        storageSlots.set(index, definitionId);
    }

    public void removeStorageAt(int index) {
        if (index >= 0 && index < storageSlots.size()) {
            storageSlots.set(index, null);
        }
    }

    public int installedStorageCount() {
        int count = 0;
        for (String id : storageSlots) {
            if (id != null) {
                count++;
            }
        }
        return count;
    }

    public List<String> installedStorageIds() {
        List<String> result = new ArrayList<>();
        for (String id : storageSlots) {
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    public long storageMegabytesAt(int index) {
        String id = storageAt(index);
        return id == null ? 0 : HardwareCapacities.storageMegabytes(id);
    }

    public String gpuId() {
        return gpuId;
    }

    public void setGpuId(String gpuId) {
        this.gpuId = gpuId;
    }

    public String audioId() {
        return audioId;
    }

    public void setAudioId(String audioId) {
        this.audioId = audioId;
    }

    public String networkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }

    public String opticalId() {
        return opticalId;
    }

    public void setOpticalId(String opticalId) {
        this.opticalId = opticalId;
    }

    public String floppyId() {
        return floppyId;
    }

    public void setFloppyId(String floppyId) {
        this.floppyId = floppyId;
    }

    public boolean floppyLocked() {
        return floppyLocked;
    }

    public void setFloppyLocked(boolean floppyLocked) {
        this.floppyLocked = floppyLocked;
    }

    public List<String> expansionSlots() {
        return expansionSlots;
    }

    public String expansionAt(int index) {
        if (index < 0 || index >= expansionSlots.size()) {
            return null;
        }
        return expansionSlots.get(index);
    }

    public void setExpansionAt(int index, String definitionId) {
        while (expansionSlots.size() <= index) {
            expansionSlots.add(null);
        }
        expansionSlots.set(index, definitionId);
    }

    public void removeExpansionAt(int index) {
        if (index >= 0 && index < expansionSlots.size()) {
            expansionSlots.set(index, null);
        }
    }

    public String isoFileName() {
        return isoFileName;
    }

    public void setIsoFileName(String isoFileName) {
        this.isoFileName = mediaFileName(isoFileName, ".iso");
    }

    public String cdromFileName() {
        return cdromFileName;
    }

    public void setCdromFileName(String cdromFileName) {
        this.cdromFileName = mediaFileName(cdromFileName, ".iso");
    }

    public String floppyFileName() {
        return floppyFileName;
    }

    public void setFloppyFileName(String floppyFileName) {
        this.floppyFileName = mediaFileName(floppyFileName, ".img");
    }

    public String cpuType() {
        return cpuType == null ? "host" : cpuType;
    }

    public void setCpuType(String cpuType) {
        if (cpuType == null || cpuType.isBlank()) {
            this.cpuType = "host";
            return;
        }
        String type = cpuType.trim().toLowerCase(java.util.Locale.ROOT);
        this.cpuType = switch (type) {
            case "amd", "intel" -> type;
            default -> "host";
        };
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        if (name == null) {
            this.name = null;
            return;
        }
        String trimmed = name.trim();
        this.name = trimmed.isBlank() ? null : trimmed.substring(0, Math.min(trimmed.length(), 32));
    }

    private static String mediaFileName(String value, String extension) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Path path;
        try {
            path = Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        if (!fileName.toLowerCase(java.util.Locale.ROOT).endsWith(extension)) {
            return null;
        }
        return path.toString();
    }

    public List<String> installedExpansionIds() {
        List<String> result = new ArrayList<>();
        for (String id : expansionSlots) {
            if (id != null) {
                result.add(id);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public PcPowerState powerState() {
        return powerState;
    }

    public void setPowerState(PcPowerState powerState) {
        this.powerState = powerState;
    }

    public boolean isPowerOn() {
        return powerState == PcPowerState.STARTING
                || powerState == PcPowerState.RUNNING
                || powerState == PcPowerState.STOPPING;
    }

    public boolean isEmpty() {
        return motherboardId == null;
    }
}

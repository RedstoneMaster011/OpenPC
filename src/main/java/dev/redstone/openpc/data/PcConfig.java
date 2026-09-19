package dev.redstone.openpc.data;

import dev.redstone.openpc.hardware.HardwareCapacities;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PcConfig {

    private final long pcId;
    private String motherboardId;
    private String cpuId;
    private final List<String> ramSlots;
    private String storageId;
    private String gpuId;
    private String audioId;
    private String networkId;
    private String opticalId;
    private String floppyId;
    private final List<String> expansionSlots;
    private String isoFileName;
    private PcPowerState powerState;

    public PcConfig(long pcId) {
        this.pcId = pcId;
        this.ramSlots = new ArrayList<>();
        this.expansionSlots = new ArrayList<>();
        this.powerState = PcPowerState.OFF;
    }

    public PcConfig copy() {
        return copyWithPcId(pcId);
    }

    public PcConfig copyWithPcId(long newPcId) {
        PcConfig copy = new PcConfig(newPcId);
        copy.motherboardId = motherboardId;
        copy.cpuId = cpuId;
        copy.ramSlots.addAll(ramSlots);
        copy.storageId = storageId;
        copy.gpuId = gpuId;
        copy.audioId = audioId;
        copy.networkId = networkId;
        copy.opticalId = opticalId;
        copy.floppyId = floppyId;
        copy.expansionSlots.addAll(expansionSlots);
        copy.isoFileName = isoFileName;
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

    public String cpuId() {
        return cpuId;
    }

    public void setCpuId(String cpuId) {
        this.cpuId = cpuId;
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
        if (storageId == null) {
            return 512;
        }
        return HardwareCapacities.storageMegabytes(storageId);
    }

    public String storageId() {
        return storageId;
    }

    public void setStorageId(String storageId) {
        this.storageId = storageId;
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
        if (isoFileName == null || isoFileName.isBlank()) {
            this.isoFileName = null;
            return;
        }
        String base = Path.of(isoFileName).getFileName().toString();
        if (!base.toLowerCase(java.util.Locale.ROOT).endsWith(".iso") || !base.equals(isoFileName)) {
            this.isoFileName = null;
            return;
        }
        this.isoFileName = base;
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
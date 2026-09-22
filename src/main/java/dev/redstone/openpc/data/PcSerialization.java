package dev.redstone.openpc.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public final class PcSerialization {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private static final String KEY_PC_ID = "pc_id";
    private static final String KEY_NAME = "name";
    private static final String KEY_MOTHERBOARD = "motherboard";
    private static final String KEY_CPU = "cpu";
    private static final String KEY_RAM = "ram";
    private static final String KEY_STORAGE = "storage";
    private static final String KEY_GPU = "gpu";
    private static final String KEY_AUDIO = "audio";
    private static final String KEY_NETWORK = "network";
    private static final String KEY_OPTICAL = "optical";
    private static final String KEY_FLOPPY = "floppy";
    private static final String KEY_FLOPPY_LOCKED = "floppy_locked";
    private static final String KEY_EXPANSION = "expansion";
    private static final String KEY_ISO = "iso";
    private static final String KEY_CDROM_FILE = "cdrom_file";
    private static final String KEY_FLOPPY_FILE = "floppy_file";
    private static final String KEY_CPU_TYPE = "cpu_type";
    private static final String KEY_POWER = "power";

    private PcSerialization() {
    }

    public static String encode(PcConfig config) {
        return GSON.toJson(toJson(config));
    }

    public static String encodePretty(PcConfig config) {
        return PRETTY.toJson(toJson(config));
    }

    public static PcConfig decode(String data) {
        if (data == null || data.isBlank()) {
            return new PcConfig(0);
        }
        try {
            return fromJson(JsonParser.parseString(data).getAsJsonObject());
        } catch (JsonParseException | IllegalStateException error) {
            return new PcConfig(0);
        }
    }

    private static JsonObject toJson(PcConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty(KEY_PC_ID, config.pcId());
        root.addProperty(KEY_NAME, nullToBlank(config.name()));
        root.addProperty(KEY_MOTHERBOARD, nullToBlank(config.motherboardId()));
        root.add(KEY_CPU, stringsArray(config.cpuSlots()));
        root.add(KEY_RAM, stringsArray(config.ramSlots()));
        root.add(KEY_STORAGE, stringsArray(config.storageSlots()));
        root.addProperty(KEY_GPU, nullToBlank(config.gpuId()));
        root.addProperty(KEY_AUDIO, nullToBlank(config.audioId()));
        root.addProperty(KEY_NETWORK, nullToBlank(config.networkId()));
        root.addProperty(KEY_OPTICAL, nullToBlank(config.opticalId()));
        root.addProperty(KEY_FLOPPY, nullToBlank(config.floppyId()));
        root.addProperty(KEY_FLOPPY_LOCKED, config.floppyLocked());
        root.add(KEY_EXPANSION, stringsArray(config.expansionSlots()));
        root.addProperty(KEY_ISO, nullToBlank(config.isoFileName()));
        root.addProperty(KEY_CDROM_FILE, nullToBlank(config.cdromFileName()));
        root.addProperty(KEY_FLOPPY_FILE, nullToBlank(config.floppyFileName()));
        root.addProperty(KEY_CPU_TYPE, nullToBlank(config.cpuType()));
        root.addProperty(KEY_POWER, config.powerState().name());
        return root;
    }

    private static JsonArray stringsArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(nullToBlank(value));
        }
        return array;
    }

    private static PcConfig fromJson(JsonObject root) {
        PcConfig config = new PcConfig(root.has(KEY_PC_ID) ? root.get(KEY_PC_ID).getAsLong() : 0);
        config.setName(blankToNull(optionalString(root, KEY_NAME)));
        config.setMotherboardId(blankToNull(optionalString(root, KEY_MOTHERBOARD)));

        config.cpuSlots().clear();
        if (root.has(KEY_CPU)) {
            JsonElement cpu = root.get(KEY_CPU);
            if (cpu.isJsonArray()) {
                config.cpuSlots().addAll(collectStrings(cpu));
            } else if (cpu.isJsonPrimitive() && !cpu.getAsString().isBlank()) {
                config.cpuSlots().add(cpu.getAsString());
            }
        }

        config.setGpuId(blankToNull(optionalString(root, KEY_GPU)));
        config.setAudioId(blankToNull(optionalString(root, KEY_AUDIO)));
        config.setNetworkId(blankToNull(optionalString(root, KEY_NETWORK)));
        config.setOpticalId(blankToNull(optionalString(root, KEY_OPTICAL)));
        config.setFloppyId(blankToNull(optionalString(root, KEY_FLOPPY)));
        config.setFloppyLocked(root.has(KEY_FLOPPY_LOCKED) && root.get(KEY_FLOPPY_LOCKED).getAsBoolean());
        config.setIsoFileName(blankToNull(optionalString(root, KEY_ISO)));
        config.setCdromFileName(blankToNull(optionalString(root, KEY_CDROM_FILE)));
        config.setFloppyFileName(blankToNull(optionalString(root, KEY_FLOPPY_FILE)));
        config.setCpuType(blankToNull(optionalString(root, KEY_CPU_TYPE)));

        config.ramSlots().clear();
        if (root.has(KEY_RAM)) {
            config.ramSlots().addAll(collectStrings(root.get(KEY_RAM)));
        }

        config.storageSlots().clear();
        if (root.has(KEY_STORAGE)) {
            JsonElement storage = root.get(KEY_STORAGE);
            if (storage.isJsonArray()) {
                config.storageSlots().addAll(collectStrings(storage));
            } else if (storage.isJsonPrimitive() && !storage.getAsString().isBlank()) {
                config.storageSlots().add(storage.getAsString());
            }
        }

        config.expansionSlots().clear();
        if (root.has(KEY_EXPANSION)) {
            config.expansionSlots().addAll(collectStrings(root.get(KEY_EXPANSION)));
        }

        if (root.has(KEY_POWER)) {
            try {
                config.setPowerState(PcPowerState.valueOf(root.get(KEY_POWER).getAsString()));
            } catch (IllegalArgumentException ignored) {
                config.setPowerState(PcPowerState.OFF);
            }
        }
        return config;
    }

    private static List<String> collectStrings(JsonElement element) {
        List<String> result = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                result.add(blankToNull(item.isJsonNull() ? null : item.getAsString()));
            }
        }
        return result;
    }

    private static String optionalString(JsonObject root, String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = root.get(key);
        if (!element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
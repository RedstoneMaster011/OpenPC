package dev.redstone.openpc.hardware;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HardwareDefinition {

    private final String id;
    private final HardwareCategory category;
    private final Map<String, String> properties;
    private final String qemuDevice;
    private final String modelKey;

    private HardwareDefinition(String id, HardwareCategory category, Map<String, String> properties,
                               String qemuDevice, String modelKey) {
        this.id = id;
        this.category = category;
        this.properties = new LinkedHashMap<>(properties);
        this.qemuDevice = qemuDevice;
        this.modelKey = modelKey;
    }

    public static HardwareDefinition of(String id, HardwareCategory category, String qemuDevice, String modelKey) {
        return new HardwareDefinition(id, category, new LinkedHashMap<>(), qemuDevice, modelKey);
    }

    public String id() {
        return id;
    }

    public HardwareCategory category() {
        return category;
    }

    public Identifier identifier() {
        return Identifier.of("openpc", id);
    }

    public String translationKey() {
        return "item.openpc." + id;
    }

    public Text displayName() {
        return Text.translatable(translationKey());
    }

    public Text shortName() {
        return Text.translatable(translationKey());
    }

    public String qemuDevice() {
        return qemuDevice;
    }

    public String modelKey() {
        return modelKey;
    }

    public HardwareDefinition property(String key, String value) {
        this.properties.put(key, value);
        return this;
    }

    public HardwareDefinition property(String key, int value) {
        return property(key, Integer.toString(value));
    }

    public HardwareDefinition property(String key, long value) {
        return property(key, Long.toString(value));
    }

    public HardwareDefinition property(String key, boolean value) {
        return property(key, Boolean.toString(value));
    }

    public String getProperty(String key) {
        return properties.get(key);
    }

    public String getProperty(String key, String fallback) {
        return properties.getOrDefault(key, fallback);
    }

    public int getInt(String key, int fallback) {
        String value = properties.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public long getLong(String key, long fallback) {
        String value = properties.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = properties.get(key);
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(value);
    }

    public Map<String, String> properties() {
        return Collections.unmodifiableMap(properties);
    }
}
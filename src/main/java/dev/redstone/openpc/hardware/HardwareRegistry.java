package dev.redstone.openpc.hardware;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class HardwareRegistry {

    private static final Map<String, HardwareDefinition> DEFINITIONS = new LinkedHashMap<>();

    private HardwareRegistry() {
    }

    public static void register(HardwareDefinition definition) {
        if (DEFINITIONS.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate hardware definition: " + definition.id());
        }
        DEFINITIONS.put(definition.id(), definition);
    }

    public static Optional<HardwareDefinition> find(String id) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static HardwareDefinition require(String id) {
        HardwareDefinition definition = DEFINITIONS.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown hardware definition: " + id);
        }
        return definition;
    }

    public static HardwareDefinition require(Identifier identifier) {
        return require(identifier.getPath());
    }

    public static List<HardwareDefinition> all() {
        return Collections.unmodifiableList(new ArrayList<>(DEFINITIONS.values()));
    }

    public static List<HardwareDefinition> ofCategory(HardwareCategory category) {
        List<HardwareDefinition> matches = new ArrayList<>();
        for (HardwareDefinition definition : DEFINITIONS.values()) {
            if (definition.category() == category) {
                matches.add(definition);
            }
        }
        return matches;
    }

    public static Optional<HardwareDefinition> findByItemIdentifier(Identifier itemIdentifier) {
        return DEFINITIONS.values().stream()
                .filter(definition -> definition.identifier().equals(itemIdentifier))
                .findFirst();
    }
}
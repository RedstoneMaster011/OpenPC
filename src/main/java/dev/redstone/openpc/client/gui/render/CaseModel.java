package dev.redstone.openpc.client.gui.render;

import dev.redstone.openpc.data.PcConfig;
import dev.redstone.openpc.hardware.HardwareDefinition;
import dev.redstone.openpc.hardware.HardwareDefinitions;
import dev.redstone.openpc.hardware.HardwareRegistry;

import java.util.ArrayList;
import java.util.List;

public final class CaseModel {

    public static final float HALF = 0.5f;

    private CaseModel() {
    }

    public static List<Face> build(PcConfig config) {
        return build(config, true);
    }

    public static List<Face> build(PcConfig config, boolean caseOpen) {
        List<Face> faces = new ArrayList<>();

        addInnerShell(faces);
        addOuterShell(faces);
        addFrontFrame(faces);

        if (config.motherboardId() != null) {
            addMotherboard(faces);
            addCpu(faces, config);
            addRam(faces, config);
            addExpansionCards(faces, config);
        }
        addStorage(faces, config);
        addPsu(faces);
        addWire(faces);
        addOptical(faces, config);
        addFloppy(faces, config);
        if (!caseOpen) {
            addClosedSidePanel(faces);
        }

        return faces;
    }

    private static void addClosedSidePanel(List<Face> faces) {
        addBox(faces, Vec3.of(-HALF + 0.01f, -HALF + 0.01f, HALF - 0.04f),
                Vec3.of(HALF - 0.01f, HALF - 0.01f, HALF + 0.02f),
                ComponentTextures.Key.CASE_OUTER, 0.95f);
    }

    private static void addInnerShell(List<Face> faces) {
        addOpenBox(faces, Vec3.of(-HALF + 0.02f, -HALF + 0.02f, -HALF + 0.02f),
                Vec3.of(HALF - 0.02f, HALF - 0.02f, HALF - 0.02f), ComponentTextures.Key.CASE_INNER, 1.0f);
    }

    private static void addOuterShell(List<Face> faces) {
        float o = HALF;
        float i = HALF - 0.05f;
        float back = -o;
        float front = +o;

        backFace(faces, back, i, o, ComponentTextures.Key.CASE_OUTER);
        topFace(faces, -o, i, o, -o, i, ComponentTextures.Key.CASE_OUTER);
        bottomFace(faces, -o, i, o, -o, i, ComponentTextures.Key.CASE_OUTER);
        rightFace(faces, -o, i, -o, i, front, ComponentTextures.Key.CASE_OUTER);
        leftFace(faces, -o, i, -o, i, front, ComponentTextures.Key.CASE_OUTER);
    }

    private static void backFace(List<Face> faces, float z, float y0, float y1, ComponentTextures.Key key) {
        addBox(faces, Vec3.of(-HALF, y0, z), Vec3.of(HALF, y1, z + 0.02f), key, 0.82f);
    }

    private static void topFace(List<Face> faces, float top, float x0, float x1, float z0, float z1, ComponentTextures.Key key) {
        addBox(faces, Vec3.of(x0, top - 0.02f, z0), Vec3.of(x1, top, z1), key, 1.0f);
    }

    private static void bottomFace(List<Face> faces, float bottom, float x0, float x1, float z0, float z1, ComponentTextures.Key key) {
        addBox(faces, Vec3.of(x0, bottom, z0), Vec3.of(x1, bottom + 0.02f, z1), key, 0.58f);
    }

    private static void rightFace(List<Face> faces, float x0, float x1, float z0, float z1, float front, ComponentTextures.Key key) {
        addBox(faces, Vec3.of(x1 - 0.02f, -HALF, z0), Vec3.of(x1, HALF, z1), key, 0.78f);
    }

    private static void leftFace(List<Face> faces, float x0, float x1, float z0, float z1, float front, ComponentTextures.Key key) {
        addBox(faces, Vec3.of(x0, -HALF, z0), Vec3.of(x0 + 0.02f, HALF, z1), key, 0.78f);
    }

    private static void addFrontFrame(List<Face> faces) {
        float z = HALF - 0.01f;
        float thickness = HALF - z;
        float windowMinX = -0.26f;
        float windowMaxX = 0.26f;
        float windowMinY = -0.42f;
        float windowMaxY = 0.34f;
        ComponentTextures.Key frame = ComponentTextures.Key.CASE_FRAME;

        addBox(faces, Vec3.of(-HALF, -HALF, z), Vec3.of(HALF, windowMinY, z + 0.02f), frame, 0.9f);
        addBox(faces, Vec3.of(-HALF, windowMaxY, z), Vec3.of(HALF, HALF, z + 0.02f), frame, 0.9f);
        addBox(faces, Vec3.of(-HALF, windowMinY, z), Vec3.of(windowMinX, windowMaxY, z + 0.02f), frame, 0.9f);
        addBox(faces, Vec3.of(windowMaxX, windowMinY, z), Vec3.of(HALF, windowMaxY, z + 0.02f), frame, 0.9f);
    }

    private static void addMotherboard(List<Face> faces) {
        addBox(faces, Vec3.of(-0.44f, -0.44f, -0.37f), Vec3.of(0.22f, 0.44f, -0.27f),
                ComponentTextures.Key.MOTHERBOARD, 0.92f);
    }

    private static void addCpu(List<Face> faces, PcConfig config) {
        if (config.cpuId() == null) {
            addBox(faces, Vec3.of(0.03f, -0.13f, -0.27f), Vec3.of(0.17f, 0.01f, -0.26f),
                    ComponentTextures.Key.CASE_INNER, 0.6f);
            return;
        }
        addBox(faces, Vec3.of(0.03f, -0.13f, -0.27f), Vec3.of(0.17f, 0.01f, -0.12f),
                ComponentTextures.Key.CPU, 1.0f);
    }

    private static void addRam(List<Face> faces, PcConfig config) {
        int slots = ramSlotCount(config);
        for (int i = 0; i < slots; i++) {
            float x = 0.22f + i * 0.055f;
            boolean filled = config.ramAt(i) != null;
            float z0 = -0.27f;
            float z1 = filled ? -0.12f : -0.24f;
            ComponentTextures.Key key = filled ? ComponentTextures.Key.RAM : ComponentTextures.Key.CASE_INNER;
            addBox(faces, Vec3.of(x, 0.12f, z0), Vec3.of(x + 0.045f, 0.44f, z1), key, filled ? 1.0f : 0.55f);
        }
    }

    private static void addExpansionCards(List<Face> faces, PcConfig config) {
        List<String> cards = expansionOrder(config);
        float z0 = -0.28f;
        float z1 = -0.16f;
        for (int i = 0; i < cards.size(); i++) {
            float yBottom = -0.44f + i * 0.2f;
            float yTop = yBottom + 0.18f;
            ComponentTextures.Key key = cardTexture(i, config);
            addBox(faces, Vec3.of(0.24f, yBottom, z0), Vec3.of(0.44f, yTop, z1), key, 0.95f);
        }
    }

    private static ComponentTextures.Key cardTexture(int index, PcConfig config) {
        if (!expansionOrder(config).isEmpty() && index == 0 && config.gpuId() != null) {
            return ComponentTextures.Key.GPU;
        }
        return ComponentTextures.Key.CARD;
    }

    private static void addStorage(List<Face> faces, PcConfig config) {
        for (int i = 0; i < PcConfig.MAX_STORAGE_SLOTS; i++) {
            boolean filled = config.storageAt(i) != null;
            float x0 = -0.36f + i * 0.13f;
            if (x0 + 0.11f > 0.5f) {
                break;
            }
            ComponentTextures.Key key = filled ? ComponentTextures.Key.STORAGE : ComponentTextures.Key.CASE_INNER;
            addBox(faces, Vec3.of(x0, -0.38f, 0.10f), Vec3.of(x0 + 0.11f, -0.28f, 0.44f), key, filled ? 0.9f : 0.5f);
        }
    }

    private static int ramSlotCount(PcConfig config) {
        HardwareDefinition motherboard = HardwareRegistry.find(config.motherboardId()).orElse(null);
        return motherboard == null ? 4 : Math.max(1, motherboard.getInt(HardwareDefinitions.PROP_RAM_SLOTS, 4));
    }

    private static void addPsu(List<Face> faces) {
        addBox(faces, Vec3.of(-0.44f, 0.28f, 0.10f), Vec3.of(0.10f, 0.46f, 0.44f),
                ComponentTextures.Key.PSU, 0.92f);
    }

    private static void addWire(List<Face> faces) {
        addBox(faces, Vec3.of(-0.06f, 0.12f, 0.02f), Vec3.of(-0.02f, 0.28f, 0.08f),
                ComponentTextures.Key.WIRE, 0.6f);
        addBox(faces, Vec3.of(-0.06f, 0.18f, 0.00f), Vec3.of(-0.02f, 0.24f, 0.30f),
                ComponentTextures.Key.WIRE, 0.6f);
    }

    private static void addOptical(List<Face> faces, PcConfig config) {
        if (config.opticalId() == null) {
            return;
        }
        addBox(faces, Vec3.of(-0.34f, 0.02f, 0.12f), Vec3.of(0.34f, 0.20f, 0.44f),
                ComponentTextures.Key.OPTICAL, 0.9f);
    }

    private static void addFloppy(List<Face> faces, PcConfig config) {
        if (config.floppyId() == null) {
            return;
        }
        addBox(faces, Vec3.of(0.34f, -0.20f, 0.12f), Vec3.of(0.46f, -0.04f, 0.44f),
                ComponentTextures.Key.FLOPPY, 0.9f);
    }

    private static List<String> expansionOrder(PcConfig config) {
        List<String> order = new ArrayList<>();
        if (config.gpuId() != null) {
            order.add(config.gpuId());
        }
        if (config.audioId() != null) {
            order.add(config.audioId());
        }
        if (config.networkId() != null) {
            order.add(config.networkId());
        }
        for (String id : config.expansionSlots()) {
            if (id != null && !order.contains(id)) {
                order.add(id);
            }
        }
        return order;
    }

    private static void addOpenBox(List<Face> faces, Vec3 min, Vec3 max, ComponentTextures.Key key, float brightness) {
        float x0 = min.x, y0 = min.y, z0 = min.z;
        float x1 = max.x, y1 = max.y, z1 = max.z;

        faces.add(quad(
                Vec3.of(x1, y0, z0), Vec3.of(x0, y0, z0), Vec3.of(x0, y1, z0), Vec3.of(x1, y1, z0),
                Vec3.of(0, 0, -1), key, brightness));
        faces.add(quad(
                Vec3.of(x0, y1, z1), Vec3.of(x1, y1, z1), Vec3.of(x1, y1, z0), Vec3.of(x0, y1, z0),
                Vec3.of(0, 1, 0), key, brightness));
        faces.add(quad(
                Vec3.of(x1, y0, z1), Vec3.of(x0, y0, z1), Vec3.of(x0, y0, z0), Vec3.of(x1, y0, z0),
                Vec3.of(0, -1, 0), key, brightness));
        faces.add(quad(
                Vec3.of(x1, y0, z0), Vec3.of(x1, y1, z0), Vec3.of(x1, y1, z1), Vec3.of(x1, y0, z1),
                Vec3.of(1, 0, 0), key, brightness));
        faces.add(quad(
                Vec3.of(x0, y0, z1), Vec3.of(x0, y1, z1), Vec3.of(x0, y1, z0), Vec3.of(x0, y0, z0),
                Vec3.of(-1, 0, 0), key, brightness));
    }

    private static void addBox(List<Face> faces, Vec3 min, Vec3 max, ComponentTextures.Key key, float brightness) {
        float x0 = min.x, y0 = min.y, z0 = min.z;
        float x1 = max.x, y1 = max.y, z1 = max.z;

        faces.add(quad(
                Vec3.of(x0, y0, z1), Vec3.of(x1, y0, z1), Vec3.of(x1, y1, z1), Vec3.of(x0, y1, z1),
                Vec3.of(0, 0, 1), key, brightness));
        faces.add(quad(
                Vec3.of(x1, y0, z0), Vec3.of(x0, y0, z0), Vec3.of(x0, y1, z0), Vec3.of(x1, y1, z0),
                Vec3.of(0, 0, -1), key, brightness));
        faces.add(quad(
                Vec3.of(x0, y1, z1), Vec3.of(x1, y1, z1), Vec3.of(x1, y1, z0), Vec3.of(x0, y1, z0),
                Vec3.of(0, 1, 0), key, brightness));
        faces.add(quad(
                Vec3.of(x1, y0, z1), Vec3.of(x0, y0, z1), Vec3.of(x0, y0, z0), Vec3.of(x1, y0, z0),
                Vec3.of(0, -1, 0), key, brightness));
        faces.add(quad(
                Vec3.of(x1, y0, z0), Vec3.of(x1, y1, z0), Vec3.of(x1, y1, z1), Vec3.of(x1, y0, z1),
                Vec3.of(1, 0, 0), key, brightness));
        faces.add(quad(
                Vec3.of(x0, y0, z1), Vec3.of(x0, y1, z1), Vec3.of(x0, y1, z0), Vec3.of(x0, y0, z0),
                Vec3.of(-1, 0, 0), key, brightness));
    }

    private static Face quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, ComponentTextures.Key key, float brightness) {
        float[][] uvs = {
                {0, 0}, {1, 0}, {1, 1}, {0, 1}
        };
        return new Face(new Vec3[]{a, b, c, d}, uvs, normal, key, brightness);
    }

    public enum HitKind {
        MOTHERBOARD,
        CPU,
        RAM,
        EXPANSION,
        STORAGE,
        OPTICAL
    }

    public record ComponentBox(Vec3 min, Vec3 max, HitKind kind, int index) {

        public float intersectRay(Vec3 origin, Vec3 dir) {
            final float eps = 1.0E-5f;
            float tmin = -Float.MAX_VALUE;
            float tmax = Float.MAX_VALUE;

            if (Math.abs(dir.x) < eps) {
                if (origin.x < min.x || origin.x > max.x) {
                    return -1;
                }
            } else {
                float tx1 = (min.x - origin.x) / dir.x;
                float tx2 = (max.x - origin.x) / dir.x;
                tmin = Math.max(tmin, Math.min(tx1, tx2));
                tmax = Math.min(tmax, Math.max(tx1, tx2));
            }

            if (Math.abs(dir.y) < eps) {
                if (origin.y < min.y || origin.y > max.y) {
                    return -1;
                }
            } else {
                float ty1 = (min.y - origin.y) / dir.y;
                float ty2 = (max.y - origin.y) / dir.y;
                tmin = Math.max(tmin, Math.min(ty1, ty2));
                tmax = Math.min(tmax, Math.max(ty1, ty2));
            }

            if (Math.abs(dir.z) < eps) {
                if (origin.z < min.z || origin.z > max.z) {
                    return -1;
                }
            } else {
                float tz1 = (min.z - origin.z) / dir.z;
                float tz2 = (max.z - origin.z) / dir.z;
                tmin = Math.max(tmin, Math.min(tz1, tz2));
                tmax = Math.min(tmax, Math.max(tz1, tz2));
            }

            if (tmax < Math.max(0, tmin)) {
                return -1;
            }
            return tmin >= 0 ? tmin : tmax;
        }
    }

    public record PickResult(HitKind kind, int index) {
    }

    public static List<ComponentBox> hitboxes(PcConfig config) {
        List<ComponentBox> boxes = new ArrayList<>();

        boxes.add(new ComponentBox(Vec3.of(-0.44f, -0.44f, -0.37f), Vec3.of(0.22f, 0.44f, -0.26f),
                HitKind.MOTHERBOARD, 0));

        boxes.add(new ComponentBox(Vec3.of(0.03f, -0.13f, -0.27f), Vec3.of(0.17f, 0.01f, -0.12f),
                HitKind.CPU, 0));

        for (int i = 0; i < ramSlotCount(config); i++) {
            float x = 0.22f + i * 0.055f;
            boxes.add(new ComponentBox(Vec3.of(x, 0.12f, -0.27f), Vec3.of(x + 0.045f, 0.44f, -0.12f),
                    HitKind.RAM, i));
        }

        List<String> order = expansionOrder(config);
        for (int i = 0; i < order.size(); i++) {
            float yBottom = -0.44f + i * 0.2f;
            boxes.add(new ComponentBox(Vec3.of(0.24f, yBottom, -0.28f), Vec3.of(0.44f, yBottom + 0.18f, -0.16f),
                    HitKind.EXPANSION, i));
        }

        for (int bay = 0; bay < PcConfig.MAX_STORAGE_SLOTS; bay++) {
            float x0 = -0.36f + bay * 0.13f;
            boxes.add(new ComponentBox(Vec3.of(x0, -0.38f, 0.10f), Vec3.of(x0 + 0.11f, -0.28f, 0.44f),
                    HitKind.STORAGE, bay));
        }

        boxes.add(new ComponentBox(Vec3.of(-0.34f, 0.02f, 0.12f), Vec3.of(0.34f, 0.20f, 0.44f),
                HitKind.OPTICAL, 0));

        return boxes;
    }

    public static PickResult pick(PcConfig config, Vec3 origin, Vec3 dir) {
        float nearest = Float.MAX_VALUE;
        ComponentBox nearestBox = null;
        for (ComponentBox box : hitboxes(config)) {
            float t = box.intersectRay(origin, dir);
            if (t >= 0 && t < nearest) {
                nearest = t;
                nearestBox = box;
            }
        }
        if (nearestBox == null) {
            return null;
        }
        return new PickResult(nearestBox.kind(), nearestBox.index());
    }
}
package dev.redstone.openpc.client.gui.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

public final class ComponentTextures {

    public static final int TILE = 16;

    private static NativeImage atlas;
    private static boolean atlasLoaded;
    private static int tileCount;

    private ComponentTextures() {
    }

    public enum Key {
        CASE_OUTER(0x3a3f44),
        CASE_INNER(0x1c1e20),
        CASE_FRAME(0x2d3136),
        MOTHERBOARD(0x1e6b40),
        CPU(0xb8a23a),
        RAM(0x2a6d9f),
        STORAGE(0x55606b),
        GPU(0x7a3b8f),
        CARD(0x3f6f8f),
        PSU(0x8a8f96),
        OPTICAL(0x6d747b),
        FLOPPY(0x9a6d2f),
        WIRE(0x1c1c1c),
        SCREEN(0x111927),
        FAN(0x2f3a4a);

        public final int baseColor;

        Key(int baseColor) {
            this.baseColor = 0xff000000 | baseColor;
        }
    }

    public static boolean isLoaded() {
        return atlasLoaded && atlas != null;
    }

    public static synchronized void ensureLoaded(MinecraftClient client) {
        if (atlasLoaded) {
            return;
        }
        atlasLoaded = true;
        tileCount = Key.values().length;
        NativeImage resourceAtlas = null;
        try {
            Identifier id = Identifier.of("openpc", "textures/gui/hardware_atlas.png");
            InputStream stream = client.getResourceManager().getResource(id)
                    .map(resource -> {
                        try {
                            return resource.getInputStream();
                        } catch (IOException error) {
                            return null;
                        }
                    })
                    .orElse(null);
            if (stream != null) {
                resourceAtlas = NativeImage.read(stream);
            }
        } catch (Exception error) {
            resourceAtlas = null;
        }
        if (resourceAtlas != null && resourceAtlas.getWidth() == TILE * tileCount && resourceAtlas.getHeight() == TILE) {
            atlas = resourceAtlas;
            return;
        }
        if (resourceAtlas != null) {
            resourceAtlas.close();
        }
        atlas = generateProcedurally();
    }

    public static int sample(Key key, float u, float v) {
        if (!isLoaded()) {
            return key.baseColor;
        }
        int index = key.ordinal();
        u = wrap(u);
        v = wrap(v);
        float uf = u * TILE - 0.5f;
        float vf = v * TILE - 0.5f;
        int x0 = floorMod(uf);
        int y0 = floorMod(vf);
        int x1 = floorMod(uf + 1);
        int y1 = floorMod(vf + 1);
        float fx = uf - (int) Math.floor(uf);
        float fy = vf - (int) Math.floor(vf);
        int baseX = index * TILE;
        int c00 = atlas.getColorArgb(baseX + x0, y0);
        int c10 = atlas.getColorArgb(baseX + x1, y0);
        int c01 = atlas.getColorArgb(baseX + x0, y1);
        int c11 = atlas.getColorArgb(baseX + x1, y1);
        int top = blend(c00, c10, fx);
        int bottom = blend(c01, c11, fx);
        return blend(top, bottom, fy);
    }

    private static int blend(int a, int b, float t) {
        float t0 = 1.0f - t;
        int ar = (a >> 16) & 0xff;
        int ag = (a >> 8) & 0xff;
        int ab = a & 0xff;
        int br = (b >> 16) & 0xff;
        int bg = (b >> 8) & 0xff;
        int bb = b & 0xff;
        int r = (int) (ar * t0 + br * t);
        int g = (int) (ag * t0 + bg * t);
        int bl = (int) (ab * t0 + bb * t);
        return 0xff000000 | (r << 16) | (g << 8) | bl;
    }

    private static int floorMod(float value) {
        int base = (int) Math.floor(value);
        return ((base % TILE) + TILE) % TILE;
    }

    private static float wrap(float value) {
        float result = value - (float) Math.floor(value);
        return result < 0 ? result + 1 : result;
    }

    private static NativeImage generateProcedurally() {
        NativeImage image = new NativeImage(TILE * tileCount, TILE, true);
        Random random = new Random(0xC0FFEE);
        Key[] keys = Key.values();
        for (int t = 0; t < keys.length; t++) {
            Key key = keys[t];
            int baseR = (key.baseColor >> 16) & 0xff;
            int baseG = (key.baseColor >> 8) & 0xff;
            int baseB = key.baseColor & 0xff;
            for (int y = 0; y < TILE; y++) {
                for (int x = 0; x < TILE; x++) {
                    int r = baseR;
                    int g = baseG;
                    int b = baseB;
                    int variation = (random.nextInt(18) - 9);
                    boolean stripe = (x + y * 3) % 5 == 0;
                    if (stripe) {
                        variation += 14;
                    }
                    r = clamp255(r + variation);
                    g = clamp255(g + variation);
                    b = clamp255(b + variation);
                    image.setColorArgb(t * TILE + x, y, 0xff000000 | (r << 16) | (g << 8) | b);
                }
            }
        }
        return image;
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
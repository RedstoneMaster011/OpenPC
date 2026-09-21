package dev.redstone.openpc.client.gui.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;

import java.util.ArrayList;
import java.util.List;

public final class SoftwareRenderer {

    public static final int Z_NEAR = 100;

    private SoftwareRenderer() {
    }

    public static void render(List<Face> faces, CaseCamera camera, int width, int height, int[] outArgb) {
        ComponentTextures.ensureLoaded(MinecraftClient.getInstance());
        Vec3 eye = camera.eye();
        Vec3 forward = Vec3.of(0, 0, 0).sub(eye).normalized();
        Vec3 right = camera.right();
        Vec3 up = camera.up();

        float focal = (height * 0.5f) / (float) Math.tan(Math.toRadians(38));
        float centerX = width * 0.5f;
        float centerY = height * 0.5f;

        float[] depthBuffer = new float[width * height];
        for (int i = 0; i < depthBuffer.length; i++) {
            depthBuffer[i] = -1.0f;
        }
        fill(outArgb, 0xff141a22, width, height);

        List<ProjectedFace> projected = new ArrayList<>();
        for (Face face : faces) {
            ClipPoint[] nearClipped = clipToNear(face, eye, right, up, forward);
            if (nearClipped == null) {
                continue;
            }
            projected.add(new ProjectedFace(nearClipped, face));
        }

        projected.sort((a, b) -> Float.compare(b.viewDepth(), a.viewDepth()));

        for (ProjectedFace face : projected) {
            drawFace(face, focal, centerX, centerY, depthBuffer, outArgb, width, height);
        }
    }

    private static void drawFace(ProjectedFace projected, float focal, float centerX, float centerY,
                                 float[] depthBuffer, int[] outArgb, int width, int height) {
        ClipPoint[] points = projected.points;
        if (points.length < 3) {
            return;
        }
        float[] sx = new float[points.length];
        float[] sy = new float[points.length];
        for (int i = 0; i < points.length; i++) {
            ClipPoint p = points[i];
            float invZ = Z_NEAR / p.cz;
            sx[i] = centerX + focal * p.cx * invZ / Z_NEAR;
            sy[i] = centerY - focal * p.cy * invZ / Z_NEAR;
        }
        for (int tri = 1; tri < points.length - 1; tri++) {
            drawTriangle(sx[0], sy[0], points[0],
                    sx[tri], sy[tri], points[tri],
                    sx[tri + 1], sy[tri + 1], points[tri + 1],
                    projected.face, depthBuffer, outArgb, width, height);
        }
    }

    private static void drawTriangle(float ax, float ay, ClipPoint a,
                                     float bx, float by, ClipPoint b,
                                     float cx, float cy, ClipPoint c,
                                     Face face, float[] depthBuffer, int[] outArgb, int width, int height) {
        float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 1.0E-6f) {
            return;
        }
        if (area < 0) {
            float tmp;
            tmp = bx; bx = cx; cx = tmp;
            tmp = by; by = cy; cy = tmp;
            ClipPoint tp = b; b = c; c = tp;
        }
        float invArea = 1.0f / Math.abs(area);

        float aInvZ = Z_NEAR / a.cz;
        float bInvZ = Z_NEAR / b.cz;
        float cInvZ = Z_NEAR / c.cz;
        float aUZ = a.u * aInvZ;
        float aVZ = a.v * aInvZ;
        float bUZ = b.u * bInvZ;
        float bVZ = b.v * bInvZ;
        float cUZ = c.u * cInvZ;
        float cVZ = c.v * cInvZ;

        int minX = clampToRange((int) Math.floor(Math.min(ax, Math.min(bx, cx))), 0, width - 1);
        int maxX = clampToRange((int) Math.ceil(Math.max(ax, Math.max(bx, cx))), 0, width - 1);
        int minY = clampToRange((int) Math.floor(Math.min(ay, Math.min(by, cy))), 0, height - 1);
        int maxY = clampToRange((int) Math.ceil(Math.max(ay, Math.max(by, cy))), 0, height - 1);

        float brightness = face.brightness;
        ComponentTextures.Key texture = face.texture;

        for (int y = minY; y <= maxY; y++) {
            float py = y + 0.5f;
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                float w0 = (bx - ax) * (py - ay) - (by - ay) * (px - ax);
                float w1 = (cx - bx) * (py - by) - (cy - by) * (px - bx);
                float w2 = (ax - cx) * (py - cy) - (ay - cy) * (px - cx);
                if (w0 < 0 || w1 < 0 || w2 < 0) {
                    continue;
                }
                float l0 = w0 * invArea;
                float l1 = w1 * invArea;
                float l2 = w2 * invArea;
                float invZ = l0 * aInvZ + l1 * bInvZ + l2 * cInvZ;
                int index = y * width + x;
                if (invZ <= depthBuffer[index]) {
                    continue;
                }
                depthBuffer[index] = invZ;
                float u = (l0 * aUZ + l1 * bUZ + l2 * cUZ) / invZ;
                float v = (l0 * aVZ + l1 * bVZ + l2 * cVZ) / invZ;
                int color = ComponentTextures.sample(texture, u, v);
                int r = (int) (((color >> 16) & 0xff) * brightness);
                int g = (int) (((color >> 8) & 0xff) * brightness);
                int blue = (int) ((color & 0xff) * brightness);
                outArgb[index] = 0xff000000 | (r << 16) | (g << 8) | blue;
            }
        }
    }

    private static void fill(int[] buffer, int argb, int width, int height) {
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = argb;
        }
    }

    private static int clampToRange(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static ClipPoint[] clipToNear(Face face, Vec3 eye, Vec3 right, Vec3 up, Vec3 forward) {
        ClipPoint[] cameraPoints = new ClipPoint[face.verts.length];
        for (int i = 0; i < face.verts.length; i++) {
            Vec3 p = face.verts[i].sub(eye);
            cameraPoints[i] = new ClipPoint(p.dot(right), p.dot(up), p.dot(forward), face.uvs[i][0], face.uvs[i][1]);
        }
        List<ClipPoint> inside = new ArrayList<>();
        List<ClipPoint> outside = new ArrayList<>();
        for (ClipPoint cameraPoint : cameraPoints) {
            if (cameraPoint.cz >= 0.12f) {
                inside.add(cameraPoint);
            } else {
                outside.add(cameraPoint);
            }
        }
        if (inside.isEmpty()) {
            return null;
        }
        if (outside.isEmpty()) {
            return cameraPoints;
        }
        List<ClipPoint> clipped = new ArrayList<>(inside);
        for (int i = 0; i < cameraPoints.length; i++) {
            ClipPoint a = cameraPoints[i];
            ClipPoint b = cameraPoints[(i + 1) % cameraPoints.length];
            boolean aIn = a.cz >= 0.12f;
            boolean bIn = b.cz >= 0.12f;
            if (aIn != bIn) {
                float t = (0.12f - a.cz) / (b.cz - a.cz);
                clipped.add(new ClipPoint(
                        a.cx + (b.cx - a.cx) * t,
                        a.cy + (b.cy - a.cy) * t,
                        0.12f,
                        a.u + (b.u - a.u) * t,
                        a.v + (b.v - a.v) * t));
            }
        }
        if (clipped.isEmpty()) {
            return null;
        }
        return clipped.toArray(new ClipPoint[0]);
    }

    public static NativeImage toImage(int[] argb, int width, int height) {
        NativeImage image = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setColorArgb(x, y, argb[y * width + x]);
            }
        }
        return image;
    }

    private record ClipPoint(float cx, float cy, float cz, float u, float v) {
    }

    private record ProjectedFace(ClipPoint[] points, Face face) {
        private float viewDepth() {
            float sum = 0;
            for (ClipPoint point : points) {
                sum += point.cz;
            }
            return sum / points.length;
        }
    }
}
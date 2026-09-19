package dev.redstone.openpc.client.gui.render;

public final class Face {

    public final Vec3[] verts;
    public final float[][] uvs;
    public final Vec3 normal;
    public final ComponentTextures.Key texture;
    public final float brightness;

    public Face(Vec3[] verts, float[][] uvs, Vec3 normal, ComponentTextures.Key texture, float brightness) {
        this.verts = verts;
        this.uvs = uvs;
        this.normal = normal;
        this.texture = texture;
        this.brightness = brightness;
    }

    public Vec3 center() {
        Vec3 sum = Vec3.of(0, 0, 0);
        for (Vec3 v : verts) {
            sum = sum.add(v);
        }
        return sum.scale(1.0f / verts.length);
    }
}
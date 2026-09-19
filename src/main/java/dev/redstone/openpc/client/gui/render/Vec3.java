package dev.redstone.openpc.client.gui.render;

public final class Vec3 {

    public final float x;
    public final float y;
    public final float z;

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Vec3 of(float x, float y, float z) {
        return new Vec3(x, y, z);
    }

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 sub(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 scale(float factor) {
        return new Vec3(x * factor, y * factor, z * factor);
    }

    public float dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    public Vec3 normalized() {
        float len = length();
        if (len < 1.0E-6f) {
            return Vec3.of(0, 0, 0);
        }
        return scale(1.0f / len);
    }

    public float distanceTo(Vec3 other) {
        return sub(other).length();
    }
}
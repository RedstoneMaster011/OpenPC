package dev.redstone.openpc.client.gui.render;

public final class CaseCamera {

    private float yaw;
    private float pitch;
    private float distance;

    public CaseCamera() {
        this.yaw = -0.6f;
        this.pitch = 0.35f;
        this.distance = 3.4f;
    }

    public void rotate(float yawDelta, float pitchDelta) {
        yaw += yawDelta;
        pitch += pitchDelta;
        if (pitch > 1.5f) {
            pitch = 1.5f;
        }
        if (pitch < -1.5f) {
            pitch = -1.5f;
        }
    }

    public void zoom(float amount) {
        distance = clamp(distance + amount, 2.0f, 7.0f);
    }

    public Vec3 eye() {
        float cp = (float) Math.cos(pitch);
        return Vec3.of(cp * (float) Math.sin(yaw), (float) Math.sin(pitch), cp * (float) Math.cos(yaw))
                .scale(distance);
    }

    public Vec3 right() {
        Vec3 eye = eye();
        Vec3 forward = Vec3.of(0, 0, 0).sub(eye).normalized();
        Vec3 up = Vec3.of(0, 1, 0);
        return forward.cross(up).normalized();
    }

    public Vec3 up() {
        Vec3 right = right();
        Vec3 eye = eye();
        Vec3 forward = Vec3.of(0, 0, 0).sub(eye).normalized();
        return right.cross(forward).normalized();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
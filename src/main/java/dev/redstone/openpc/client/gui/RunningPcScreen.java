package dev.redstone.openpc.client.gui;

import dev.redstone.openpc.client.OpenpcQemuRuntime;
import dev.redstone.openpc.client.PcClientController;
import dev.redstone.openpc.client.QemuArguments;
import dev.redstone.openpc.client.vnc.GlfwKeyMapping;
import dev.redstone.openpc.client.vnc.VncClient;
import dev.redstone.openpc.client.net.OpenpcClientNetworking;
import dev.redstone.openpc.data.PcConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

public class RunningPcScreen extends Screen {

    private static final long CONNECT_TIMEOUT_MS = 20_000;
    private static final Identifier DISPLAY_TEXTURE_ID = Identifier.of("openpc", "dynamic/running_pc");

    private final BlockPos pos;
    private final long pcId;
    private volatile PcConfig config;
    private VncClient vnc;
    private NativeImage displayImage;
    private NativeImageBackedTexture displayTexture;
    private Identifier displayTextureId;
    private ButtonWidget powerOffButton;
    private long connectionStartedAt;
    private String connectionError;

    public RunningPcScreen(BlockPos pos, PcConfig config) {
        super(Text.translatable("openpc.screen.running"));
        this.pos = pos;
        this.pcId = config.pcId();
        this.config = config;
    }

    public boolean posEquals(BlockPos other) {
        return pos.equals(other);
    }

    public boolean pcIdEquals(long other) {
        return pcId == other;
    }

    public void refreshConfig(PcConfig config) {
        this.config = config;
    }

    public void notifyActionResult(boolean success) {
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        if (displayTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(DISPLAY_TEXTURE_ID);
            displayTexture.close();
            displayTexture = null;
        }
        if (displayImage != null) {
            displayImage.close();
            displayImage = null;
        }
        this.displayImage = new NativeImage(1, 1, true);
        this.displayTexture = new NativeImageBackedTexture(() -> "openpc running pc", displayImage);
        this.displayTextureId = DISPLAY_TEXTURE_ID;
        MinecraftClient.getInstance().getTextureManager().registerTexture(displayTextureId, displayTexture);
        this.powerOffButton = ButtonWidget.builder(Text.translatable("openpc.ui.power_off"), b -> requestPowerOff())
                .dimensions(width - 108, 8, 100, 20)
                .build();
        addDrawableChild(powerOffButton);
        this.connectionStartedAt = System.currentTimeMillis();
        connectVnc();
    }

    private void connectVnc() {
        if (vnc != null) {
            return;
        }
        int port = QemuArguments.vncPortFor(pcId);
        VncClient client = new VncClient("127.0.0.1", port);
        client.setFrameListener(ignored -> frameArrived());
        Path marker = OpenpcQemuRuntime.dataRoot().getParent().resolve("debug-frames.txt");
        boolean capture = System.getProperty("openpc.debugFrames") != null
                || Files.isRegularFile(marker)
                || net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();
        if (capture) {
            client.enableDebugCapture();
        }
        client.start();
        this.vnc = client;
    }

    private void frameArrived() {
    }

    private void ensureDisplayTextureSize(int requiredWidth, int requiredHeight) {
        if (requiredWidth <= 0 || requiredHeight <= 0 || displayImage == null || displayTexture == null) {
            return;
        }
        if (displayImage.getWidth() == requiredWidth && displayImage.getHeight() == requiredHeight) {
            return;
        }
        MinecraftClient.getInstance().getTextureManager().destroyTexture(DISPLAY_TEXTURE_ID);
        displayTexture.close();
        displayImage.close();
        displayImage = new NativeImage(requiredWidth, requiredHeight, true);
        displayTexture = new NativeImageBackedTexture(() -> "openpc running pc", displayImage);
        displayTextureId = DISPLAY_TEXTURE_ID;
        MinecraftClient.getInstance().getTextureManager().registerTexture(displayTextureId, displayTexture);
    }

    @Override
    public void tick() {
        super.tick();
        if (vnc == null) {
            connectVnc();
        }
        if (vnc != null && vnc.consumePendingFrame() && displayImage != null && displayTexture != null) {
            ensureDisplayTextureSize(vnc.width(), vnc.height());
            vnc.snapshotInto(displayImage);
            displayTexture.upload();
        }
        if (vnc != null && !vnc.hasFrame() && System.currentTimeMillis() - connectionStartedAt > CONNECT_TIMEOUT_MS) {
            connectionError = "openpc.msg.vnc_timeout";
        }
    }

    private void requestPowerOff() {
        if (config != null) {
            OpenpcClientNetworking.sendPower(pos, false);
            beforeDispose();
            close();
        }
    }

    public void beforeDispose() {
        if (vnc != null) {
            vnc.close();
            vnc = null;
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(input);
        }
        if (vnc == null || !vnc.hasFrame()) {
            return super.keyPressed(input);
        }
        int keysym = GlfwKeyMapping.keysymWithShift(input.getKeycode(), input.modifiers());
        if (keysym != 0) {
            vnc.sendKey(keysym, true);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        if (vnc == null) {
            return super.keyReleased(input);
        }
        int keysym = GlfwKeyMapping.keysymWithShift(input.getKeycode(), input.modifiers());
        if (keysym != 0) {
            vnc.sendKey(keysym, false);
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (vnc == null || !vnc.hasFrame()) {
            return super.charTyped(input);
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) {
            return true;
        }
        if (vnc == null || !vnc.hasFrame()) {
            return false;
        }
        int mask = switch (click.button()) {
            case 0 -> 1;
            case 1 -> 4;
            case 2 -> 2;
            default -> 0;
        };
        if (mask != 0) {
            sendPointer(click.x(), click.y(), mask);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (super.mouseReleased(click)) {
            return true;
        }
        if (vnc != null && vnc.hasFrame()) {
            sendPointer(click.x(), click.y(), 0);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (click.button() == 0 && vnc != null && vnc.hasFrame()) {
            sendPointer(click.x(), click.y(), 1);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (vnc != null && vnc.hasFrame()) {
            int mask = verticalAmount > 0 ? 8 : 16;
            sendPointer(mouseX, mouseY, mask);
            sendPointer(mouseX, mouseY, 0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void sendPointer(double mouseX, double mouseY, int buttonMask) {
        int[] canvas = canvasSize();
        if (canvas == null) {
            return;
        }
        int w = canvas[0];
        int h = canvas[1];
        if (canvas[2] <= 0 || canvas[3] <= 0) {
            return;
        }
        int px = (int) Math.round(((mouseX - canvasX()) / (canvas[2])) * w);
        int py = (int) Math.round(((mouseY - canvasY()) / (canvas[3])) * h);
        if (px < 0) px = 0;
        if (py < 0) py = 0;
        if (px >= w) px = w - 1;
        if (py >= h) py = h - 1;
        vnc.sendPointer(px, py, buttonMask);
    }

    private int[] canvasSize() {
        if (vnc == null) {
            return null;
        }
        int w = vnc.width();
        int h = vnc.height();
        if (w <= 0 || h <= 0) {
            return null;
        }
        double scale = Math.min((width - 24) / (double) w, (height - 24) / (double) h);
        int cw = (int) (w * scale);
        int ch = (int) (h * scale);
        return new int[]{w, h, cw, ch};
    }

    private int canvasX() {
        int[] c = canvasSize();
        if (c == null) {
            return 0;
        }
        return (width - c[2]) / 2;
    }

    private int canvasY() {
        int[] c = canvasSize();
        if (c == null) {
            return 0;
        }
        return (height - c[3]) / 2;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        if (vnc == null || !vnc.hasFrame()) {
            Text message = Text.translatable(connectionError != null ? connectionError : "openpc.msg.starting");
            context.drawCenteredTextWithShadow(textRenderer, message, width / 2, height / 2, 0xFFFFFF);
        } else {
            int[] canvas = canvasSize();
            if (canvas != null) {
                int x = canvasX();
                int y = canvasY();
                context.drawTexturedQuad(displayTextureId, x, y, x + canvas[2], y + canvas[3], 0.0F, 1.0F, 0.0F, 1.0F);
            }
        }
        super.render(context, mouseX, mouseY, tickDelta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        beforeDispose();
        super.close();
    }

    @Override
    public void removed() {
        beforeDispose();
        if (displayTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(DISPLAY_TEXTURE_ID);
            displayTexture.close();
            displayTexture = null;
        }
        if (displayImage != null) {
            displayImage.close();
            displayImage = null;
        }
        PcClientController.onRunningClosed(this);
        super.removed();
    }
}

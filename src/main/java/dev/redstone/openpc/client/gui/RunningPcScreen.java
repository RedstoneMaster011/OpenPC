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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    private ButtonWidget pasteVmButton;
    private final List<ButtonWidget> shortcutButtons = new ArrayList<>();
    private boolean leftDown;
    private int lastPointerX = -1;
    private int lastPointerY = -1;
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
        this.pasteVmButton = ButtonWidget.builder(Text.translatable("openpc.ui.paste_vm"), b -> pasteVmClipboard())
                .dimensions(width - 108, 32, 100, 20)
                .build();
        addDrawableChild(pasteVmButton);
        addShortcutButton("Start", 0xFFEB);
        addShortcutButton("Alt+F4", 0xFFE9, 0xFF14);
        addShortcutButton("Ctrl+Alt+Del", 0xFFE3, 0xFFE9, 0xFFFF);
        addShortcutButton("Alt+Tab", 0xFFE9, 0xFF09);
        addShortcutButton("Ctrl+C", 0xFFE3, 0x63);
        addShortcutButton("Ctrl+V", 0xFFE3, 0x76);
        addShortcutButton("Ctrl+X", 0xFFE3, 0x78);
        addShortcutButton("Ctrl+Z", 0xFFE3, 0x7A);
        addShortcutButton("Ctrl+A", 0xFFE3, 0x61);
        addShortcutButton("Ctrl+S", 0xFFE3, 0x73);
        addShortcutButton("PrtScr", 0xFF61);
        addShortcutButton("Esc", 0xFF1B);
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
        if (vnc != null && vnc.hasFrame() && !leftDown) {
            int[] p = currentPointerPos();
            if (p != null && (p[0] != lastPointerX || p[1] != lastPointerY)) {
                lastPointerX = p[0];
                lastPointerY = p[1];
                vnc.sendPointer(p[0], p[1], 0);
            }
        }
        if (vnc != null && !vnc.hasFrame() && System.currentTimeMillis() - connectionStartedAt > CONNECT_TIMEOUT_MS) {
            connectionError = "openpc.msg.vnc_timeout";
        }
        refreshShortcutPositions();
    }

    private void requestPowerOff() {
        if (config != null) {
            OpenpcClientNetworking.sendPower(pos, false);
            beforeDispose();
            close();
        }
    }

    private void addShortcutButton(String label, int... keysyms) {
        ButtonWidget button = ButtonWidget.builder(Text.literal(label), b -> sendChord(keysyms))
                .dimensions(0, 0, 60, 18)
                .build();
        shortcutButtons.add(button);
        addDrawableChild(button);
    }

    private void refreshShortcutPositions() {
        int[] canvas = canvasSize();
        if (canvas == null || shortcutButtons.isEmpty()) {
            return;
        }
        int cw = canvas[2];
        int ch = canvas[3];
        int columns = 6;
        int gap = 4;
        int buttonWidth = Math.max(36, (cw - (columns - 1) * gap) / columns);
        int x0 = (width - (columns * buttonWidth + (columns - 1) * gap)) / 2;
        int y0 = canvasY() + ch + 6;
        for (int i = 0; i < shortcutButtons.size(); i++) {
            int row = i / columns;
            int col = i % columns;
            ButtonWidget button = shortcutButtons.get(i);
            button.setX(x0 + col * (buttonWidth + gap));
            button.setY(y0 + row * 20);
            button.setWidth(buttonWidth);
        }
    }

    private void sendChord(int... keysyms) {
        if (vnc == null) {
            return;
        }
        for (int keysym : keysyms) {
            vnc.sendKey(keysym, true);
        }
        for (int i = keysyms.length - 1; i >= 0; i--) {
            vnc.sendKey(keysyms[i], false);
        }
    }

    private void pasteVmClipboard() {
        if (vnc == null) {
            return;
        }
        String text = MinecraftClient.getInstance().keyboard.getClipboard();
        if (text == null || text.isEmpty()) {
            return;
        }
        vnc.sendClientCutText(text);
        typeText(text);
    }

    private void typeText(String text) {
        int[] keysyms = new int[text.length()];
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            int keysym = keysymFor(text.charAt(i));
            if (keysym != 0) {
                keysyms[count++] = keysym;
            }
        }
        if (count > 0) {
            vnc.sendKeySequence(Arrays.copyOf(keysyms, count));
        }
    }

    private static int keysymFor(char c) {
        if (c == '\n' || c == '\r') {
            return 0xFF0D;
        }
        if (c == '\t') {
            return 0xFF09;
        }
        if (c >= 0x20 && c <= 0x7E) {
            return c;
        }
        return 0;
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
            if (click.button() == 0) {
                leftDown = true;
            }
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
            if (click.button() == 0) {
                leftDown = false;
            }
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
        int[] p = clampToCanvas(mouseX, mouseY);
        if (p == null) {
            return;
        }
        vnc.sendPointer(p[0], p[1], buttonMask);
        lastPointerX = p[0];
        lastPointerY = p[1];
    }

    private int[] clampToCanvas(double mouseX, double mouseY) {
        int[] canvas = canvasSize();
        if (canvas == null || canvas[2] <= 0 || canvas[3] <= 0) {
            return null;
        }
        int w = canvas[0];
        int h = canvas[1];
        int px = (int) Math.round(((mouseX - canvasX()) / (canvas[2])) * w);
        int py = (int) Math.round(((mouseY - canvasY()) / (canvas[3])) * h);
        if (px < 0) px = 0;
        if (py < 0) py = 0;
        if (px >= w) px = w - 1;
        if (py >= h) py = h - 1;
        return new int[]{px, py};
    }

    private int[] currentPointerPos() {
        MinecraftClient client = MinecraftClient.getInstance();
        double scaledX = client.mouse.getX() * client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth();
        double scaledY = client.mouse.getY() * client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight();
        return clampToCanvas(scaledX, scaledY);
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
        double scale = Math.min((width - 24) / (double) w, (height - 24) / (double) h) * 0.85;
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

package dev.redstone.openpc.client.vnc;

import dev.redstone.openpc.client.OpenpcQemuRuntime;
import net.minecraft.client.texture.NativeImage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class VncClient implements AutoCloseable {

    private final String host;
    private final int port;
    private final AtomicBoolean run = new AtomicBoolean(true);
    private final Object frameLock = new Object();
    private final AtomicInteger width = new AtomicInteger(0);
    private final AtomicInteger height = new AtomicInteger(0);
    private volatile int[] pixels = new int[0];
    private volatile boolean firstFrame;
    private final AtomicBoolean framePending = new AtomicBoolean(false);
    private volatile Consumer<VncClient> frameListener;
    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private Thread reader;

    public VncClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public int width() {
        return width.get();
    }

    public int height() {
        return height.get();
    }

    public boolean hasFrame() {
        return firstFrame;
    }

    public void setFrameListener(Consumer<VncClient> listener) {
        this.frameListener = listener;
    }

    public String describe() {
        return host + ":" + port;
    }

    public void snapshotInto(NativeImage target) {
        synchronized (frameLock) {
            int w = width.get();
            int h = height.get();
            int[] local = this.pixels;
            if (w <= 0 || h <= 0 || local.length < w * h || target.getWidth() < w || target.getHeight() < h) {
                return;
            }
            try {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        target.setColorArgb(x, y, local[y * w + x]);
                    }
                }
            } catch (IllegalStateException e) {
                // Image was closed, ignore
            }
        }
    }

    public void start() {
        reader = new Thread(this::run, "openpc-vnc-" + port);
        reader.setDaemon(true);
        reader.start();
    }

    public void sendKey(int keysym, boolean down) {
        synchronized (outLock()) {
            try {
                if (out == null) {
                    return;
                }
                out.write(4);
                out.write(down ? 1 : 0);
                out.write(new byte[]{0, 0});
                writeInt(keysym);
                out.flush();
            } catch (IOException ignored) {
            }
        }
    }

    public void sendPointer(int x, int y, int buttonMask) {
        synchronized (outLock()) {
            try {
                if (out == null) {
                    return;
                }
                out.write(5);
                out.write(buttonMask);
                out.write((x >> 8) & 0xff);
                out.write(x & 0xff);
                out.write((y >> 8) & 0xff);
                out.write(y & 0xff);
                out.flush();
            } catch (IOException ignored) {
            }
        }
    }

    private Object outLock() {
        return this;
    }

    @Override
    public void close() {
        run.set(false);
        if (reader != null) {
            reader.interrupt();
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void run() {
        Socket connection = null;
        IOException lastError = null;
        int attempts = 0;
        final int maxAttempts = 30;
        
        OpenpcQemuRuntime.logInfo("VNC connecting to " + describe() + "...");
        
        while (attempts < maxAttempts && run.get()) {
            try {
                connection = connect();
                OpenpcQemuRuntime.logInfo("VNC connected to " + describe() + " on attempt " + (attempts + 1));
                break;
            } catch (IOException error) {
                lastError = error;
                attempts++;
                OpenpcQemuRuntime.logInfo("VNC connection attempt " + attempts + "/" + maxAttempts + " failed: " + error.getMessage());
                if (attempts < maxAttempts && run.get()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        
        if (connection == null && run.get()) {
            OpenpcQemuRuntime.logWarn("VNC connection to " + describe() + " failed after " + maxAttempts + " attempts: " + (lastError != null ? lastError.getMessage() : "unknown"));
            return;
        }
        
        try (Socket conn = connection) {
            this.socket = conn;
            this.in = new DataInputStream(new BufferedInputStream(conn.getInputStream()));
            this.out = new BufferedOutputStream(conn.getOutputStream());
            handshake();
            out.write(1); // shared desktop flag
            out.flush();
            readServerInit();
            sendPixelFormat();
            sendEncodings();
            requestUpdate(true);
            messageLoop();
        } catch (IOException | RuntimeException error) {
            if (run.get()) {
                OpenpcQemuRuntime.logWarn("VNC connection to " + describe() + " lost: " + error.getMessage());
            }
        } finally {
            notifyFrame();
        }
    }

    private Socket connect() throws IOException {
        return new Socket(host, port);
    }

    private void handshake() throws IOException {
        byte[] version = new byte[12];
        in.readFully(version);
        String serverVersion = new String(version, StandardCharsets.US_ASCII);
        if (!serverVersion.startsWith("RFB ")) {
            throw new IOException("Not a VNC server: " + serverVersion);
        }
        out.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();

        int securityType = readSecurityType();
        out.write(securityType);
        out.flush();
        int securityResult = readInt();
        if (securityResult != 0) {
            throw new IOException("VNC security negotiation failed.");
        }
    }

    private int readSecurityType() throws IOException {
        int count = in.readUnsignedByte();
        if (count == 0) {
            int reasonLength = readInt();
            byte[] reason = new byte[Math.max(0, reasonLength)];
            in.readFully(reason);
            throw new IOException(new String(reason, StandardCharsets.US_ASCII));
        }
        int selected = 0;
        for (int i = 0; i < count; i++) {
            int type = in.readUnsignedByte();
            if (type == 1) {
                selected = 1;
            }
        }
        if (selected == 1) {
            return selected;
        }
        throw new IOException("VNC server offers no anonymous access.");
    }

    private void readServerInit() throws IOException {
        width.set(in.readUnsignedShort());
        height.set(in.readUnsignedShort());
        synchronized (frameLock) {
            pixels = new int[width.get() * height.get()];
            // Initialize to black
            Arrays.fill(pixels, 0xff000000);
        }
        readPixelFormat();
        int nameLength = readInt();
        if (nameLength > 0) {
            byte[] name = new byte[nameLength];
            in.readFully(name);
        }
    }

    private void readPixelFormat() throws IOException {
        in.readUnsignedByte();
        in.readUnsignedByte();
        in.readUnsignedByte();
        in.readUnsignedByte();
        in.readUnsignedShort();
        in.readUnsignedShort();
        in.readUnsignedShort();
        in.readUnsignedByte();
        in.readUnsignedByte();
        in.readUnsignedByte();
        in.skipNBytes(3);
    }

    private void messageLoop() throws IOException {
        while (run.get()) {
            int messageType = in.readUnsignedByte();
            switch (messageType) {
                case 0 -> readFramebufferUpdate();
                case 1 -> {
                    in.skipNBytes(1);
                    int count = in.readUnsignedShort();
                    in.skipNBytes(count * 6L);
                }
                case 2 -> readBell();
                case 3 -> readServerCutText();
                default -> skipUnknown(messageType);
            }
        }
    }

    private void readFramebufferUpdate() throws IOException {
        in.skipNBytes(1);
        int rectangleCount = in.readUnsignedShort();
        for (int i = 0; i < rectangleCount; i++) {
            int x = in.readUnsignedShort();
            int y = in.readUnsignedShort();
            int w = in.readUnsignedShort();
            int h = in.readUnsignedShort();
            int encoding = readInt();
            readRectangle(x, y, w, h, encoding);
        }
        this.firstFrame = true;
        requestUpdate(true);
        notifyFrame();
    }

    private void readRectangle(int x, int y, int w, int h, int encoding) throws IOException {
        if (encoding == 0) {
            readRawRectangle(x, y, w, h);
        } else if (encoding == -239) {
            readCopyRectangle(x, y, w, h);
        } else {
            skipRectangle(x, y, w, h, encoding);
        }
    }

    private void readRawRectangle(int x, int y, int w, int h) throws IOException {
        if (x < 0 || y < 0 || w <= 0 || h <= 0) {
            readFramebufferRawIgnoring(x, y, w, h);
            return;
        }
        int fbWidth = width.get();
        int fbHeight = height.get();
        if (fbWidth <= 0 || fbHeight <= 0) {
            readFramebufferRawIgnoring(x, y, w, h);
            return;
        }
        byte[] row = new byte[w * 4];
        synchronized (frameLock) {
            int[] local = pixels;
            if (local.length != fbWidth * fbHeight) {
                readFramebufferRawIgnoring(x, y, w, h);
                return;
            }
            for (int rowIndex = 0; rowIndex < h; rowIndex++) {
                in.readFully(row);
                int targetY = y + rowIndex;
                if (targetY < 0 || targetY >= fbHeight) {
                    continue;
                }
                int targetBase = targetY * fbWidth;
                for (int col = 0; col < w; col++) {
                    int targetX = x + col;
                    if (targetX >= 0 && targetX < fbWidth) {
                        int offset = col * 4;
                        int r = row[offset + 2] & 0xff;
                        int g = row[offset + 1] & 0xff;
                        int b = row[offset] & 0xff;
                        local[targetBase + targetX] = 0xff000000 | (r << 16) | (g << 8) | b;
                    }
                }
            }
        }
    }

    private void readFramebufferRawIgnoring(int x, int y, int w, int h) throws IOException {
        in.skipNBytes((long) w * h * 4);
    }

    private void readCopyRectangle(int x, int y, int w, int h) throws IOException {
        int sourceX = in.readUnsignedShort();
        int sourceY = in.readUnsignedShort();
        int fbWidth = width.get();
        int fbHeight = height.get();
        if (sourceX < 0 || sourceY < 0 || sourceX + w > fbWidth || sourceY + h > fbHeight) {
            readRawRectangle(x, y, w, h);
            return;
        }
        synchronized (frameLock) {
            int[] local = pixels;
            if (local.length != fbWidth * fbHeight) {
                readRawRectangle(x, y, w, h);
                return;
            }
            for (int row = 0; row < h; row++) {
                System.arraycopy(local, (sourceY + row) * fbWidth + sourceX, local, (y + row) * fbWidth + x, w);
            }
        }
    }

    private void skipRectangle(int x, int y, int w, int h, int encoding) throws IOException {
        if (encoding == -223) {
            return;
        }
        if (encoding == 1) {
            int[] paletted = new int[in.readUnsignedByte() + 1];
            for (int i = 0; i < paletted.length; i++) {
                int r = in.readUnsignedByte();
                int g = in.readUnsignedByte();
                int b = in.readUnsignedByte();
                paletted[i] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
            byte[] bytes = new byte[w * h];
            in.readFully(bytes);
            synchronized (frameLock) {
                int[] local = pixels;
                int fbWidth = width.get();
                int fbHeight = height.get();
                if (local.length == fbWidth * fbHeight) {
                    for (int row = 0; row < h; row++) {
                        for (int col = 0; col < w; col++) {
                            int index = bytes[row * w + col] & 0xff;
                            if (index < paletted.length) {
                                int targetY = y + row;
                                int targetX = x + col;
                                if (targetX >= 0 && targetX < fbWidth && targetY >= 0 && targetY < fbHeight) {
                                    local[targetY * fbWidth + targetX] = paletted[index];
                                }
                            }
                        }
                    }
                }
            }
            return;
        }
        int bpp = 4;
        in.skipNBytes((long) w * h * bpp);
    }

    private void readBell() throws IOException {
    }

    private void readServerCutText() throws IOException {
        in.skipNBytes(3);
        int length = readInt();
        in.skipNBytes(length);
    }

    private void skipUnknown(int messageType) throws IOException {
        int length = readInt();
        in.skipNBytes(length);
    }

    private int readInt() throws IOException {
        return in.readInt();
    }

    private void writeInt(int value) throws IOException {
        out.write((value >>> 24) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write(value & 0xff);
    }

    private void sendPixelFormat() throws IOException {
        out.write(0);
        out.write(new byte[]{0, 0, 0});
        out.write(32);
        out.write(24);
        out.write(0);
        out.write(1);
        writeShort(255);
        writeShort(255);
        writeShort(255);
        out.write(16);
        out.write(8);
        out.write(0);
        out.write(new byte[]{0, 0, 0});
        out.flush();
    }

    private void sendEncodings() throws IOException {
        out.write(2);
        out.write(0);
        writeShort(1);
        writeInt(0);
        out.flush();
    }

    private void requestUpdate(boolean incremental) throws IOException {
        out.write(3);
        out.write(incremental ? 1 : 0);
        writeShort(0);
        writeShort(0);
        writeShort(width.get());
        writeShort(height.get());
        out.flush();
    }

    private void writeShort(int value) throws IOException {
        out.write((value >>> 8) & 0xff);
        out.write(value & 0xff);
    }

    private void notifyFrame() {
        framePending.set(true);
        Consumer<VncClient> listener = frameListener;
        if (listener != null) {
            listener.accept(this);
        }
    }

    public boolean consumePendingFrame() {
        return framePending.getAndSet(false);
    }
}

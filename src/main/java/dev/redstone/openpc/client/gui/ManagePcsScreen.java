package dev.redstone.openpc.client.gui;

import dev.redstone.openpc.client.PcClientController;
import dev.redstone.openpc.client.QemuProcessManager;
import dev.redstone.openpc.data.PcConfig;
import dev.redstone.openpc.data.PcDataStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManagePcsScreen extends Screen {

    private static final int ROW_HEIGHT = 18;

    private final PcBuilderScreen owner;
    private final List<Long> pcIds = new ArrayList<>();
    private final Map<Long, String> pcLabels = new HashMap<>();
    private long pendingDelete = -1;
    private boolean deleting;
    private String deleteError;

    public ManagePcsScreen(PcBuilderScreen owner) {
        super(Text.translatable("openpc.screen.manage_pcs"));
        this.owner = owner;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        rebuildList();
    }

    private void rebuildList() {
        clearChildren();
        pcIds.clear();
        pcLabels.clear();
        pcIds.addAll(PcDataStore.listPcIds());
        for (long pcId : pcIds) {
            pcLabels.put(pcId, resolveLabel(pcId));
        }
        int top = 28;
        int nameW = Math.max(80, width - 108);
        for (int i = 0; i < pcIds.size(); i++) {
            long pcId = pcIds.get(i);
            int y = top + i * ROW_HEIGHT;
            String label = truncate(pcLabels.getOrDefault(pcId, "PC-" + pcId), nameW);
            if (pendingDelete == pcId) {
                ButtonWidget prompt = ButtonWidget.builder(Text.translatable("openpc.ui.confirm_delete_pc", label), b -> {
                }).dimensions(8, y, nameW, 12).build();
                prompt.active = false;
                addDrawableChild(prompt);
                addDrawableChild(ButtonWidget.builder(Text.translatable("openpc.ui.cancel"), b -> {
                    pendingDelete = -1;
                    rebuildList();
                }).dimensions(width - 96, y, 40, 12).build());
                ButtonWidget confirm = ButtonWidget.builder(Text.translatable("openpc.ui.confirm_delete"), b -> deletePc(pcId))
                        .dimensions(width - 52, y, 48, 12)
                        .build();
                confirm.active = !deleting;
                addDrawableChild(confirm);
            } else {
                ButtonWidget name = ButtonWidget.builder(Text.literal(label), b -> {
                    pendingDelete = pcId;
                    rebuildList();
                }).dimensions(8, y, nameW, 12).build();
                name.active = false;
                addDrawableChild(name);
                ButtonWidget del = ButtonWidget.builder(Text.translatable("openpc.ui.delete_pc"), b -> {
                    pendingDelete = pcId;
                    rebuildList();
                }).dimensions(width - 52, y, 48, 12).build();
                del.active = !deleting;
                addDrawableChild(del);
            }
        }
    }

    private String truncate(String label, int maxWidth) {
        if (textRenderer.getWidth(label) <= maxWidth - 8) {
            return label;
        }
        String ellipsis = "...";
        String tail = label;
        while (tail.length() > 1 && textRenderer.getWidth(ellipsis + tail) > maxWidth - 8) {
            tail = tail.substring(0, tail.length() - 1);
        }
        return textRenderer.getWidth(ellipsis + tail) <= maxWidth - 8 ? ellipsis + tail : tail;
    }

    private String resolveLabel(long pcId) {
        PcConfig config = PcDataStore.readConfigMirror(pcId);
        String name = config == null ? null : config.name();
        String fallback = "PC-" + pcId;
        if (name == null || name.isBlank()) {
            return fallback;
        }
        return name + " (" + fallback + ")";
    }

    private void deletePc(long pcId) {
        if (deleting) {
            return;
        }
        deleting = true;
        deleteError = null;
        Thread deletion = new Thread(() -> {
            QemuProcessManager.requestStop(pcId);
            long deadline = System.currentTimeMillis() + 6000;
            while (QemuProcessManager.isRunning(pcId) && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(80);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            try {
                PcDataStore.deletePcDirectory(pcId);
            } catch (Throwable error) {
                deleteError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (deleteError != null) {
                    deleting = false;
                    pendingDelete = -1;
                    rebuildList();
                    return;
                }
                long activeId = PcClientController.activeConfig() == null ? -1 : PcClientController.activeConfig().pcId();
                PcClientController.onPcDeleted(pcId);
                if (activeId == pcId) {
                    client.setScreen(null);
                } else if (owner != null) {
                    client.setScreen(owner);
                } else {
                    client.setScreen(null);
                }
            });
        }, "openpc-pc-delete-" + pcId);
        deletion.setDaemon(true);
        deletion.start();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        super.render(context, mouseX, mouseY, tickDelta);
        context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.manage_pcs_header"), 8, 10, 0xFFFFFF);
        if (pcIds.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.no_pcs"), 8, 30, 0xAAAAAA);
        }
        if (deleteError != null) {
            context.drawTextWithShadow(textRenderer, Text.translatable("openpc.error.delete_failed", deleteError), 8, height - 20, 0xFF7777);
        }
        context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.close_hint"), 8, height - 10, 0xAAAAAA);
    }
}
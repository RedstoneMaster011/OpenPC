package dev.redstone.openpc.client.gui;

import dev.redstone.openpc.OpenpcItems;
import dev.redstone.openpc.client.PcClientController;
import dev.redstone.openpc.client.QemuEnvironment;
import dev.redstone.openpc.client.gui.render.CaseCamera;
import dev.redstone.openpc.client.gui.render.CaseModel;
import dev.redstone.openpc.client.gui.render.ComponentTextures;
import dev.redstone.openpc.client.gui.render.Face;
import dev.redstone.openpc.client.gui.render.SoftwareRenderer;
import dev.redstone.openpc.client.net.OpenpcClientNetworking;
import dev.redstone.openpc.data.PcConfig;
import dev.redstone.openpc.hardware.HardwareCategory;
import dev.redstone.openpc.hardware.HardwareDefinition;
import dev.redstone.openpc.hardware.HardwareRegistry;
import dev.redstone.openpc.validation.PcValidation;
import dev.redstone.openpc.validation.PcValidationResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.util.ArrayList;
import java.util.List;

public class PcBuilderScreen extends Screen {

    private static final Identifier SCENE_TEXTURE_ID = Identifier.of("openpc", "dynamic/builder_scene");

    private final BlockPos pos;
    private PcConfig lastServerConfig;
    private PcConfig working;
    private boolean sending;
    private boolean lastSendFailed;
    private boolean openCase;
    private boolean dragging;
    private TextFieldWidget nameField;
    private boolean nameFieldWasFocused;

    private final CaseCamera camera = new CaseCamera();
    private int viewX;
    private int viewY;
    private int viewW;
    private int viewH;
    private int[] scenePixels;
    private NativeImageBackedTexture sceneTexture;
    private Identifier sceneTextureId;

    private float introScale;
    private int buttonSignature = Integer.MIN_VALUE;
    private PcValidationResult currentValidation;
    private final List<OverlayIcon> overlayIcons = new ArrayList<>();

    public PcBuilderScreen(BlockPos pos, PcConfig config) {
        super(Text.translatable("openpc.screen.builder"));
        this.pos = pos;
        this.lastServerConfig = config;
        this.working = config.copy();
        this.introScale = 0.2f;
    }

    public boolean posEquals(BlockPos other) {
        return pos.equals(other);
    }

    public void applySnapshot(PcConfig config) {
        this.lastServerConfig = config;
        this.working = config.copy();
        this.sending = false;
        this.lastSendFailed = false;
        if (config.isPowerOn()) {
            this.openCase = false;
        }
        revalidate();
        this.buttonSignature = Integer.MIN_VALUE;
    }

    public void notifyActionResult(boolean success) {
        this.sending = false;
        if (!success) {
            this.lastSendFailed = true;
            this.working = lastServerConfig.copy();
            this.buttonSignature = Integer.MIN_VALUE;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        this.viewW = Math.min(420, Math.max(240, width - 220));
        this.viewH = Math.min(300, Math.max(180, height - 90));
        this.viewX = (width - viewW) / 2 - 20;
        this.viewY = (height - viewH) / 2 - 16;
        recreateSceneBuffers();
        revalidate();
        this.buttonSignature = Integer.MIN_VALUE;
        rebuildButtons();
    }

    private void recreateSceneBuffers() {
        if (sceneTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(SCENE_TEXTURE_ID);
            sceneTexture.close();
            sceneTexture = null;
        }
        this.scenePixels = new int[viewW * viewH];
        this.sceneTexture = new NativeImageBackedTexture(() -> "openpc builder scene", viewW, viewH, true);
        this.sceneTextureId = SCENE_TEXTURE_ID;
        MinecraftClient.getInstance().getTextureManager().registerTexture(sceneTextureId, sceneTexture);
    }

    @Override
    public void tick() {
        super.tick();
        introScale = MathHelper.lerp(0.25f, introScale, 1.0f);
        int signature = computeButtonSignature();
        if (signature != buttonSignature) {
            rebuildButtons();
            nameFieldWasFocused = false;
        }
        if (nameField != null) {
            boolean focused = nameField.isFocused();
            if (nameFieldWasFocused && !focused) {
                commitPcName();
            }
            nameFieldWasFocused = focused;
        } else {
            nameFieldWasFocused = false;
        }
    }

    private int computeButtonSignature() {
        int hash = openCase ? 1 : 0;
        hash = 31 * hash + (working.motherboardId() == null ? 0 : working.motherboardId().hashCode());
        hash = 31 * hash + (working.cpuId() == null ? 0 : working.cpuId().hashCode());
        hash = 31 * hash + working.ramSlots().hashCode();
        hash = 31 * hash + (working.storageId() == null ? 0 : working.storageId().hashCode());
        hash = 31 * hash + (working.gpuId() == null ? 0 : working.gpuId().hashCode());
        hash = 31 * hash + (working.audioId() == null ? 0 : working.audioId().hashCode());
        hash = 31 * hash + (working.networkId() == null ? 0 : working.networkId().hashCode());
        hash = 31 * hash + (working.opticalId() == null ? 0 : working.opticalId().hashCode());
        hash = 31 * hash + (working.floppyId() == null ? 0 : working.floppyId().hashCode());
        hash = 31 * hash + working.expansionSlots().hashCode();
        hash = 31 * hash + (working.isoFileName() == null ? 0 : working.isoFileName().hashCode());
        hash = 31 * hash + (working.cdromFileName() == null ? 0 : working.cdromFileName().hashCode());
        hash = 31 * hash + (working.floppyFileName() == null ? 0 : working.floppyFileName().hashCode());
        hash = 31 * hash + (working.cpuType() == null ? 0 : working.cpuType().hashCode());
        hash = 31 * hash + inventoryFingerprint();
        hash = 31 * hash + (lastSendFailed ? 7 : 0);
        hash = 31 * hash + (sending ? 11 : 0);
        hash = 31 * hash + (currentValidation != null && currentValidation.isValid() ? 3 : 9);
        return hash;
    }

    private int inventoryFingerprint() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return 0;
        }
        int hash = client.player.isCreative() ? 1 : 0;
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (!stack.isEmpty() && OpenpcItems.isHardwareItem(stack.getItem())) {
                hash = 31 * hash + net.minecraft.registry.Registries.ITEM.getRawId(stack.getItem());
                hash = 31 * hash + stack.getCount();
            }
        }
        return hash;
    }

    private void rebuildButtons() {
        clearChildren();
        overlayIcons.clear();
        buttonSignature = computeButtonSignature();
        revalidate();

        if (openCase) {
            buildOpenCaseButtons();
        } else {
            buildClosedCaseButtons();
        }
    }

    private void buildClosedCaseButtons() {
        boolean valid = currentValidation != null && currentValidation.isValid();
        int powerW = Math.max(72, textRenderer.getWidth(Text.translatable("openpc.ui.power_on")) + 8);
        ButtonWidget power = ButtonWidget.builder(Text.translatable("openpc.ui.power_on"), b -> powerOn())
                .dimensions(viewX + viewW - powerW, viewY - 2, powerW, 12)
                .build();
        power.active = valid && !sending;
        addDrawableChild(power);

        int mediaW = Math.min(220, Math.max(120, viewW / 2));
        int mediaX = viewX + viewW - mediaW;
        int mediaY = viewY + 14;
        mediaY = addMediaButton(mediaX, mediaY, mediaW, "openpc.ui.select_iso", "openpc.ui.eject_iso",
                working.isoFileName(),
                () -> openMediaPicker("openpc.ui.select_iso", "*.iso", "ISO images (*.iso)",
                        path -> working.setIsoFileName(path)),
                () -> {
                    working.setIsoFileName(null);
                    commitDraft();
                });
        if (working.opticalId() != null) {
            mediaY = addMediaButton(mediaX, mediaY, mediaW, "openpc.ui.select_cdrom", "openpc.ui.eject_cdrom",
                    working.cdromFileName(),
                    () -> openMediaPicker("openpc.ui.select_cdrom", "*.iso", "ISO images (*.iso)",
                            path -> working.setCdromFileName(path)),
                    () -> {
                        working.setCdromFileName(null);
                        commitDraft();
                    });
        }
        if (working.floppyId() != null) {
            addMediaButton(mediaX, mediaY, mediaW, "openpc.ui.select_floppy", "openpc.ui.eject_floppy",
                    working.floppyFileName(),
                    () -> openMediaPicker("openpc.ui.select_floppy", "*.img", "Floppy images (*.img)",
                            path -> working.setFloppyFileName(path)),
                    () -> {
                        working.setFloppyFileName(null);
                        commitDraft();
                    });
        }

        addCpuTypeButtons();

        int nameW = Math.min(160, Math.max(110, viewW / 4));
        nameField = new TextFieldWidget(textRenderer, viewX + 4, viewY + viewH - 34, nameW, 12, Text.translatable("openpc.ui.pc_name"));
        nameField.setMaxLength(32);
        nameField.setText(working.name() == null ? "" : working.name());
        nameField.setPlaceholder(Text.literal("PC-" + working.pcId()));
        addDrawableChild(nameField);

        int openW = Math.max(80, textRenderer.getWidth(Text.translatable("openpc.ui.open_case")) + 8);
        ButtonWidget open = ButtonWidget.builder(Text.translatable("openpc.ui.open_case"), b -> {
            openCase = true;
            buttonSignature = Integer.MIN_VALUE;
        }).dimensions(viewX + (viewW - openW) / 2, viewY + viewH + 6, openW, 12).build();
        addDrawableChild(open);

        int manageW = Math.min(96, Math.max(88, textRenderer.getWidth(Text.translatable("openpc.ui.manage_pcs")) + 8));
        ButtonWidget manage = ButtonWidget.builder(Text.translatable("openpc.ui.manage_pcs"), b -> {
            commitPcName();
            MinecraftClient.getInstance().setScreen(new ManagePcsScreen(this));
        })
                .dimensions(width - 96, height - 20, manageW, 12)
                .build();
        addDrawableChild(manage);
    }

    private int addMediaButton(int x, int y, int width, String selectKey, String ejectKey,
                               String fileName, Runnable select, Runnable eject) {
        Text label = fileName == null
                ? Text.translatable(selectKey)
                : Text.literal(shortenPath(fileName, Math.max(64, width - 8)));
        ButtonWidget selectButton = ButtonWidget.builder(label, b -> select.run())
                .dimensions(x, y, width, 12)
                .build();
        selectButton.active = !sending;
        addDrawableChild(selectButton);

        if (fileName != null) {
            int ejectW = Math.max(48, textRenderer.getWidth(Text.translatable(ejectKey)) + 8);
            addDrawableChild(ButtonWidget.builder(Text.translatable(ejectKey), b -> eject.run())
                    .dimensions(x + width - ejectW, y + 14, ejectW, 12)
                    .build());
        }
        return y + 28;
    }

    private void addCpuTypeButtons() {
        int labelW = textRenderer.getWidth(Text.translatable("openpc.ui.cpu_type"));
        int y = viewY + viewH - 16;
        int x = viewX + 4 + labelW + 4;
        int hostW = Math.max(44, textRenderer.getWidth(Text.translatable("openpc.ui.cpu_type_host")) + 8);
        int amdW = Math.max(44, textRenderer.getWidth(Text.translatable("openpc.ui.cpu_type_amd")) + 8);
        int intelW = Math.max(48, textRenderer.getWidth(Text.translatable("openpc.ui.cpu_type_intel")) + 8);
        ButtonWidget host = ButtonWidget.builder(cpuButtonText("host", "openpc.ui.cpu_type_host"), b -> {
            working.setCpuType("host");
            commitDraft();
        }).dimensions(x, y, hostW, 12).build();
        host.active = !sending;
        addDrawableChild(host);
        ButtonWidget amd = ButtonWidget.builder(cpuButtonText("amd", "openpc.ui.cpu_type_amd"), b -> {
            working.setCpuType("amd");
            commitDraft();
        }).dimensions(x + hostW + 4, y, amdW, 12).build();
        amd.active = !sending;
        addDrawableChild(amd);
        ButtonWidget intel = ButtonWidget.builder(cpuButtonText("intel", "openpc.ui.cpu_type_intel"), b -> {
            working.setCpuType("intel");
            commitDraft();
        }).dimensions(x + hostW + amdW + 8, y, intelW, 12).build();
        intel.active = !sending;
        addDrawableChild(intel);
    }

    private Text cpuButtonText(String type, String key) {
        return working.cpuType().equals(type)
                ? Text.literal("> ").append(Text.translatable(key))
                : Text.translatable(key);
    }

    private String shortenPath(String path, int maxWidth) {
        if (textRenderer.getWidth(path) <= maxWidth) {
            return path;
        }
        String ellipsis = "...";
        int start = Math.max(0, path.length() - 1);
        while (start > 0 && textRenderer.getWidth(ellipsis + path.substring(start)) < maxWidth) {
            start--;
        }
        return ellipsis + path.substring(Math.min(path.length(), start + 1));
    }

    private void openMediaPicker(String titleKey, String filter, String description,
                                 java.util.function.Consumer<String> applyPath) {
        MinecraftClient client = MinecraftClient.getInstance();
        Thread picker = new Thread(() -> {
            String selected;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(0, stack.UTF8(filter));
                selected = TinyFileDialogs.tinyfd_openFileDialog(
                        Text.translatable(titleKey).getString(),
                        null,
                        filters,
                        description,
                        false);
            }
            if (selected != null && !selected.isBlank()) {
                String normalized = java.nio.file.Path.of(selected).toAbsolutePath().normalize().toString();
                client.execute(() -> {
                    applyPath.accept(normalized);
                    commitDraft();
                });
            }
        }, "openpc-media-picker");
        picker.setDaemon(true);
        picker.start();
    }

    private void buildOpenCaseButtons() {
        nameField = null;
        int cx = viewX + viewW / 2;
        int cy = viewY + viewH / 2;

        if (working.motherboardId() == null) {
            int y = cy - 20;
            for (HardwareDefinition definition : HardwareRegistry.ofCategory(HardwareCategory.MOTHERBOARD)) {
                addInstallButton(cx - 70, y, 140, definition, () -> installMotherboard(definition));
                y += 14;
            }
            return;
        }

        addRemoveButton(cx - 80, cy - 78, working.motherboardId(), this::removeMotherboard);

        if (working.cpuId() == null) {
            int y = cy - 40;
            for (HardwareDefinition definition : HardwareRegistry.ofCategory(HardwareCategory.CPU)) {
                addInstallButton(cx - 150, y, 120, definition, () -> installSingle(HardwareCategory.CPU, definition));
                y += 14;
            }
        } else {
            addRemoveButton(cx - 48, cy - 20, working.cpuId(), () -> clearSlot(HardwareCategory.CPU));
        }

        if (working.gpuId() == null) {
            int y = cy + 28;
            for (HardwareDefinition definition : HardwareRegistry.ofCategory(HardwareCategory.GPU)) {
                addInstallButton(cx - 80, y, 110, definition, () -> installSingle(HardwareCategory.GPU, definition));
                y += 14;
            }
        } else {
            addRemoveButton(cx - 70, cy + 40, working.gpuId(), () -> clearSlot(HardwareCategory.GPU));
        }

        if (working.storageId() == null) {
            int y = cy + 36;
            int x = cx + 24;
            int col = 0;
            for (HardwareDefinition definition : HardwareRegistry.ofCategory(HardwareCategory.STORAGE)) {
                addInstallButton(x + col * 92, y, 88, definition, () -> installSingle(HardwareCategory.STORAGE, definition));
                col++;
                if (col >= 2) {
                    col = 0;
                    y += 14;
                }
            }
        } else {
            addRemoveButton(cx + 36, cy + 52, working.storageId(), () -> clearSlot(HardwareCategory.STORAGE));
        }

        int ramSlot = firstEmptyRamSlot();
        if (ramSlot >= 0) {
            int y = cy - 70;
            for (HardwareDefinition definition : HardwareRegistry.ofCategory(HardwareCategory.RAM)) {
                int slot = ramSlot;
                addInstallButton(cx + 56, y, 110, definition, () -> installRam(slot, definition));
                y += 14;
            }
        }
        for (int i = 0; i < working.ramSlots().size(); i++) {
            String id = working.ramAt(i);
            if (id != null) {
                int slot = i;
                addRemoveButton(cx + 24 + i * 16, cy - 78, id, () -> {
                    working.removeRamAt(slot);
                    commitDraft();
                });
            }
        }

        int extraX = Math.min(width - 132, viewX + viewW + 8);
        int extraY = viewY + 8;
        extraY = addOptionalColumn(extraX, extraY, HardwareCategory.AUDIO, working.audioId(),
                () -> clearSlot(HardwareCategory.AUDIO));
        extraY = addOptionalColumn(extraX, extraY, HardwareCategory.NETWORK, working.networkId(),
                () -> clearSlot(HardwareCategory.NETWORK));
        extraY = addOptionalColumn(extraX, extraY, HardwareCategory.OPTICAL, working.opticalId(),
                () -> clearSlot(HardwareCategory.OPTICAL));
        extraY = addOptionalColumn(extraX, extraY, HardwareCategory.FLOPPY, working.floppyId(),
                () -> clearSlot(HardwareCategory.FLOPPY));
        extraY = addOptionalColumn(extraX, extraY, HardwareCategory.EXPANSION, null, null);
        int expansionSlot = firstEmptyExpansionSlot();
        if (expansionSlot >= 0) {
            for (HardwareDefinition definition : HardwareRegistry.ofCategory(HardwareCategory.EXPANSION)) {
                int slot = expansionSlot;
                extraY = addInstallButton(extraX, extraY, 120, definition, () -> installExpansion(slot, definition)) ? extraY + 14 : extraY;
            }
        }
        for (int i = 0; i < working.expansionSlots().size(); i++) {
            String id = working.expansionAt(i);
            if (id != null) {
                int slot = i;
                addRemoveButton(extraX, extraY, id, () -> {
                    working.removeExpansionAt(slot);
                    commitDraft();
                });
                extraY += 14;
            }
        }
    }

    private int addOptionalColumn(int x, int y, HardwareCategory category, String installedId, Runnable remove) {
        if (installedId != null && remove != null) {
            addRemoveButton(x, y, installedId, remove);
            return y + 14;
        }
        if (installedId != null) {
            return y;
        }
        for (HardwareDefinition definition : HardwareRegistry.ofCategory(category)) {
            if (category == HardwareCategory.EXPANSION) {
                continue;
            }
            if (addInstallButton(x, y, 120, definition, () -> installSingle(category, definition))) {
                y += 14;
            }
        }
        return y;
    }

    private boolean addInstallButton(int x, int y, int width, HardwareDefinition definition, Runnable action) {
        if (!hasAvailableItem(definition)) {
            return false;
        }
        ButtonWidget button = ButtonWidget.builder(Text.translatable(definition.translationKey()), b -> action.run())
                .dimensions(x, y, width, 12)
                .build();
        addDrawableChild(button);
        Item item = OpenpcItems.forHardware(definition.id());
        if (item != null) {
            overlayIcons.add(new OverlayIcon(x - 18, y - 2, new ItemStack(item)));
        }
        return true;
    }

    private void addRemoveButton(int x, int y, String definitionId, Runnable action) {
        ButtonWidget button = ButtonWidget.builder(Text.literal("x"), b -> action.run())
                .dimensions(x, y, 10, 10)
                .build();
        addDrawableChild(button);
        Item item = OpenpcItems.forHardware(definitionId);
        if (item != null) {
            overlayIcons.add(new OverlayIcon(x + 12, y - 3, new ItemStack(item)));
        }
    }

    private void installMotherboard(HardwareDefinition definition) {
        working.setMotherboardId(definition.id());
        resizeSlotLists();
        commitDraft();
    }

    private void removeMotherboard() {
        working.setMotherboardId(null);
        commitDraft();
    }

    private void installSingle(HardwareCategory category, HardwareDefinition definition) {
        switch (category) {
            case CPU -> working.setCpuId(definition.id());
            case STORAGE -> working.setStorageId(definition.id());
            case GPU -> working.setGpuId(definition.id());
            case AUDIO -> working.setAudioId(definition.id());
            case NETWORK -> working.setNetworkId(definition.id());
            case OPTICAL -> working.setOpticalId(definition.id());
            case FLOPPY -> working.setFloppyId(definition.id());
            default -> {
            }
        }
        commitDraft();
    }

    private void clearSlot(HardwareCategory category) {
        switch (category) {
            case CPU -> working.setCpuId(null);
            case STORAGE -> working.setStorageId(null);
            case GPU -> working.setGpuId(null);
            case AUDIO -> working.setAudioId(null);
            case NETWORK -> working.setNetworkId(null);
            case OPTICAL -> working.setOpticalId(null);
            case FLOPPY -> working.setFloppyId(null);
            default -> {
            }
        }
        commitDraft();
    }

    private void installRam(int slot, HardwareDefinition definition) {
        working.setRamAt(slot, definition.id());
        commitDraft();
    }

    private void installExpansion(int slot, HardwareDefinition definition) {
        working.setExpansionAt(slot, definition.id());
        commitDraft();
    }

    private int firstEmptyRamSlot() {
        HardwareDefinition motherboard = HardwareRegistry.find(working.motherboardId()).orElse(null);
        int max = motherboard == null ? 0 : motherboard.getInt(dev.redstone.openpc.hardware.HardwareDefinitions.PROP_RAM_SLOTS, 0);
        resizeSlotLists();
        for (int i = 0; i < max; i++) {
            if (working.ramAt(i) == null) {
                return i;
            }
        }
        return -1;
    }

    private int firstEmptyExpansionSlot() {
        HardwareDefinition motherboard = HardwareRegistry.find(working.motherboardId()).orElse(null);
        int max = motherboard == null ? 0 : motherboard.getInt(dev.redstone.openpc.hardware.HardwareDefinitions.PROP_EXPANSION_SLOTS, 0);
        resizeSlotLists();
        for (int i = 0; i < max; i++) {
            if (working.expansionAt(i) == null) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasAvailableItem(HardwareDefinition definition) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && (client.player.isCreative() || inventoryCount(definition) > 0);
    }

    private int inventoryCount(HardwareDefinition definition) {
        MinecraftClient client = MinecraftClient.getInstance();
        Item item = OpenpcItems.forHardware(definition.id());
        if (client.player == null || item == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void commitDraft() {
        currentValidation = PcValidation.validateEditable(working);
        sending = true;
        lastSendFailed = false;
        OpenpcClientNetworking.sendModify(pos, working);
        buttonSignature = Integer.MIN_VALUE;
    }

    private void commitPcName() {
        if (nameField == null) {
            return;
        }
        String text = nameField.getText().trim();
        String current = working.name();
        if (text.equals(current == null ? "" : current)) {
            return;
        }
        working.setName(text.isEmpty() ? null : text);
        commitDraft();
    }

    private void powerOn() {
        commitPcName();
        PcValidationResult validation = PcValidation.validate(working);
        currentValidation = validation;
        if (!validation.isValid()) {
            buttonSignature = Integer.MIN_VALUE;
            return;
        }
        if (!QemuEnvironment.isUsable()) {
            lastSendFailed = true;
            return;
        }
        sending = true;
        OpenpcClientNetworking.sendPower(pos, true);
    }

    private void revalidate() {
        currentValidation = openCase ? PcValidation.validateEditable(working) : PcValidation.validate(working);
    }

    private void resizeSlotLists() {
        HardwareDefinition motherboard = HardwareRegistry.find(working.motherboardId()).orElse(null);
        if (motherboard == null) {
            return;
        }
        int ramSlots = motherboard.getInt(dev.redstone.openpc.hardware.HardwareDefinitions.PROP_RAM_SLOTS, 0);
        int expansionSlots = motherboard.getInt(dev.redstone.openpc.hardware.HardwareDefinitions.PROP_EXPANSION_SLOTS, 0);
        while (working.ramSlots().size() < ramSlots) {
            working.ramSlots().add(null);
        }
        while (working.expansionSlots().size() < expansionSlots) {
            working.expansionSlots().add(null);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) {
            dragging = false;
            return true;
        }
        if (click.button() == 0 && insideViewport(click.x(), click.y())) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging && click.button() == 0) {
            camera.rotate((float) (deltaX * 0.01f), (float) (deltaY * 0.01f));
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (insideViewport(mouseX, mouseY)) {
            camera.zoom((float) (-verticalAmount * 0.4f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean insideViewport(double mouseX, double mouseY) {
        return mouseX >= viewX && mouseX <= viewX + viewW && mouseY >= viewY && mouseY <= viewY + viewH;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        int top = colorWithAlpha(0x000000, Math.max(0.35f, 0.65f * introScale));
        int bottom = colorWithAlpha(0x000000, 0.55f * introScale);
        context.fillGradient(0, 0, width, height, top, bottom);
        renderScene(context);
        super.render(context, mouseX, mouseY, tickDelta);
        drawHud(context);
        for (OverlayIcon icon : overlayIcons) {
            context.drawItem(icon.stack(), icon.x(), icon.y());
        }
        drawInstalledLabels(context);
    }

    private void renderScene(DrawContext context) {
        if (sceneTexture == null) {
            return;
        }
        NativeImage image = sceneTexture.getImage();
        if (image == null || image.getWidth() != viewW || image.getHeight() != viewH) {
            return;
        }
        ComponentTextures.ensureLoaded(MinecraftClient.getInstance());
        List<Face> faces = CaseModel.build(working, openCase);
        SoftwareRenderer.render(faces, camera, viewW, viewH, scenePixels);
        for (int y = 0; y < viewH; y++) {
            for (int x = 0; x < viewW; x++) {
                image.setColorArgb(x, y, scenePixels[y * viewW + x]);
            }
        }
        sceneTexture.upload();
        context.drawTexture(RenderPipelines.GUI, sceneTextureId, viewX, viewY, 0, 0, viewW, viewH, viewW, viewH);
    }

    private void drawHud(DrawContext context) {
        context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.close_hint"), 4, 4, 0xFFFFFF);
        if (openCase) {
            context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.put_panel_back"), 4, 16, 0xFFFFFF);
        } else {
            if (working.isoFileName() == null) {
                context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.select_iso"), viewX, viewY - 12, 0xFFFFFF);
            } else {
                context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.inserted_iso", working.isoFileName()), viewX, viewY - 12, 0xAAAAAA);
            }
            context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.cpu_type"), viewX + 4, viewY + viewH - 14, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.pc_name"), viewX + 4, viewY + viewH - 46, 0xFFFFFF);
        }

        if (lastSendFailed) {
            context.drawTextWithShadow(textRenderer, Text.translatable("openpc.error.edit_rejected"), 4, height - 24, 0xFF7777);
        }
        List<Text> errors = currentValidation == null ? List.of() : currentValidation.errors();
        int errY = height - 36 - Math.min(4, errors.size()) * 10;
        for (int i = 0; i < Math.min(4, errors.size()); i++) {
            context.drawTextWithShadow(textRenderer, errors.get(i), 4, errY + i * 10, 0xFF8888);
        }
    }

    private void drawInstalledLabels(DrawContext context) {
        if (!openCase || working.motherboardId() == null) {
            return;
        }
        int cx = viewX + viewW / 2;
        int cy = viewY + viewH / 2;
        drawPartName(context, working.motherboardId(), cx - 66, cy - 58);
        drawPartName(context, working.cpuId(), cx - 24, cy);
        drawPartName(context, working.storageId(), cx + 48, cy + 64);
        for (int i = 0; i < working.ramSlots().size(); i++) {
            String id = working.ramAt(i);
            if (id != null) {
                HardwareDefinition ram = HardwareRegistry.find(id).orElse(null);
                if (ram != null) {
                    long mb = ram.getLong(dev.redstone.openpc.hardware.HardwareDefinitions.PROP_CAPACITY_MB, 0);
                    String label = mb >= 1024 ? (mb / 1024) + " GB" : mb + " MB";
                    context.drawTextWithShadow(textRenderer, Text.literal(label), cx + 28 + i * 28, cy, 0xFFFFFF);
                }
            }
        }
    }

    private void drawPartName(DrawContext context, String definitionId, int x, int y) {
        if (definitionId == null) {
            return;
        }
        HardwareRegistry.find(definitionId).ifPresent(definition ->
                context.drawTextWithShadow(textRenderer, Text.translatable(definition.translationKey()), x, y, 0xFFFFFF));
    }

    private static int colorWithAlpha(int rgb, float alpha) {
        int a = MathHelper.clamp((int) (alpha * 255.0f), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (openCase && (input.getKeycode() == GLFW.GLFW_KEY_RIGHT_CONTROL || input.getKeycode() == GLFW.GLFW_KEY_RIGHT_ALT)) {
            openCase = false;
            buttonSignature = Integer.MIN_VALUE;
            return true;
        }
        if (nameField != null && nameField.isFocused()
                && (input.getKeycode() == GLFW.GLFW_KEY_ENTER || input.getKeycode() == GLFW.GLFW_KEY_KP_ENTER)) {
            commitPcName();
            nameField.setFocused(false);
            return true;
        }
        return super.keyPressed(input);
    }

    private record OverlayIcon(int x, int y, ItemStack stack) {
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void removed() {
        if (sceneTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(SCENE_TEXTURE_ID);
            sceneTexture.close();
            sceneTexture = null;
        }
        PcClientController.onBuilderClosed(this);
        super.removed();
    }
}

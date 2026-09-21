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
    private final List<InstalledLabel> installedLabels = new ArrayList<>();
    private final List<OpenSection> openSections = new ArrayList<>();

    private int layoutSideX;
    private int layoutSideW;
    private int layoutFieldY;
    private int layoutPowerY;
    private int layoutPowerH;
    private int layoutOpenY;
    private int layoutCpuY;
    private int layoutMediaY;
    private int layoutMediaRows;
    private int layoutCompTextY;
    private int layoutCompLines;

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
        computeClosedLayout();
        recreateSceneBuffers();
        revalidate();
        this.buttonSignature = Integer.MIN_VALUE;
        rebuildButtons();
    }

    private void computeClosedLayout() {
        this.layoutSideW = MathHelper.clamp((int) (width * 0.22f), 190, 238);
        this.layoutSideX = width - layoutSideW - 12;
        this.viewW = Math.min(420, Math.max(240, layoutSideX - 48));
        this.viewH = Math.min(300, Math.max(180, height - 116));
        this.viewX = 16;
        this.viewY = 30;
        int xs = layoutSideX + 10;
        int iw = layoutSideW - 20;
        int y = viewY + 10;
        layoutFieldY = y + 14;
        y = layoutFieldY + 12 + 12;
        layoutPowerY = y + 14;
        layoutPowerH = 22;
        y = layoutPowerY + layoutPowerH + 16;
        layoutOpenY = y;
        y += 12 + 14;
        layoutCpuY = y + 14;
        y = layoutCpuY + 14 + 16;
        layoutMediaY = y + 14;
        layoutMediaRows = 1 + (working.opticalId() != null ? 1 : 0) + (working.floppyId() != null ? 1 : 0);
        y = layoutMediaY + layoutMediaRows * 18 + 16;
        layoutCompTextY = y + 14;
        layoutCompLines = Math.max(4, Math.min(14, (height - 12 - layoutCompTextY) / 9));
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
        installedLabels.clear();
        openSections.clear();
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
        computeClosedLayout();
        int xs = layoutSideX + 10;
        int iw = layoutSideW - 20;

        int manageW = Math.min(112, Math.max(96, textRenderer.getWidth(Text.translatable("openpc.ui.manage_pcs")) + 12));
        ButtonWidget manage = ButtonWidget.builder(Text.translatable("openpc.ui.manage_pcs"), b -> {
            commitPcName();
            MinecraftClient.getInstance().setScreen(new ManagePcsScreen(this));
        }).dimensions(width - manageW - 12, 8, manageW, 12).build();
        addDrawableChild(manage);

        ButtonWidget power = ButtonWidget.builder(Text.translatable("openpc.ui.power_on"), b -> powerOn())
                .dimensions(xs, layoutPowerY, iw, layoutPowerH)
                .build();
        power.active = valid && !sending;
        addDrawableChild(power);

        ButtonWidget open = ButtonWidget.builder(Text.translatable("openpc.ui.open_case"), b -> {
            openCase = true;
            buttonSignature = Integer.MIN_VALUE;
        }).dimensions(xs, layoutOpenY, iw, 12).build();
        addDrawableChild(open);

        addCpuTypeButtons();

        int y = layoutMediaY;
        y = addMediaButton(xs, y, iw, "openpc.ui.select_iso", "openpc.ui.eject_iso",
                working.isoFileName(),
                () -> openMediaPicker("openpc.ui.select_iso", "*.iso", "ISO images (*.iso)",
                        path -> working.setIsoFileName(path)),
                () -> {
                    working.setIsoFileName(null);
                    commitDraft();
                });
        if (working.opticalId() != null) {
            y = addMediaButton(xs, y, iw, "openpc.ui.select_cdrom", "openpc.ui.eject_cdrom",
                    working.cdromFileName(),
                    () -> openMediaPicker("openpc.ui.select_cdrom", "*.iso", "ISO images (*.iso)",
                            path -> working.setCdromFileName(path)),
                    () -> {
                        working.setCdromFileName(null);
                        commitDraft();
                    });
        }
        if (working.floppyId() != null) {
            addMediaButton(xs, y, iw, "openpc.ui.select_floppy", "openpc.ui.eject_floppy",
                    working.floppyFileName(),
                    () -> openMediaPicker("openpc.ui.select_floppy", "*.img", "Floppy images (*.img)",
                            path -> working.setFloppyFileName(path)),
                    () -> {
                        working.setFloppyFileName(null);
                        commitDraft();
                    });
        }

        nameField = new TextFieldWidget(textRenderer, xs, layoutFieldY, iw, 12, Text.translatable("openpc.ui.pc_name"));
        nameField.setMaxLength(32);
        nameField.setText(working.name() == null ? "" : working.name());
        nameField.setPlaceholder(Text.literal("PC-" + working.pcId()));
        addDrawableChild(nameField);
    }

    private int addMediaButton(int x, int y, int w, String selectKey, String ejectKey,
                               String fileName, Runnable select, Runnable eject) {
        boolean hasFile = fileName != null;
        int ejectW = 52;
        int selectW = hasFile ? w - ejectW - 6 : w;
        Text label = hasFile
                ? Text.literal(shortenPath(fileName, Math.max(64, selectW - 8)))
                : Text.translatable(selectKey);
        ButtonWidget selectButton = ButtonWidget.builder(label, b -> select.run())
                .dimensions(x, y, selectW, 12)
                .build();
        selectButton.active = !sending;
        addDrawableChild(selectButton);

        if (hasFile) {
            addDrawableChild(ButtonWidget.builder(Text.translatable(ejectKey), b -> eject.run())
                    .dimensions(x + selectW + 6, y, ejectW, 12)
                    .build());
        }
        return y + 18;
    }

    private void addCpuTypeButtons() {
        int xs = layoutSideX + 10;
        int iw = layoutSideW - 20;
        int bw = (iw - 8) / 3;
        ButtonWidget host = ButtonWidget.builder(cpuButtonText("host", "openpc.ui.cpu_type_host"), b -> {
            working.setCpuType("host");
            commitDraft();
        }).dimensions(xs, layoutCpuY, bw, 12).build();
        host.active = !sending;
        addDrawableChild(host);
        ButtonWidget amd = ButtonWidget.builder(cpuButtonText("amd", "openpc.ui.cpu_type_amd"), b -> {
            working.setCpuType("amd");
            commitDraft();
        }).dimensions(xs + bw + 4, layoutCpuY, bw, 12).build();
        amd.active = !sending;
        addDrawableChild(amd);
        ButtonWidget intel = ButtonWidget.builder(cpuButtonText("intel", "openpc.ui.cpu_type_intel"), b -> {
            working.setCpuType("intel");
            commitDraft();
        }).dimensions(xs + 2 * (bw + 4), layoutCpuY, iw - 2 * (bw + 4), 12).build();
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
        openSections.clear();
        int closeW = Math.min(112, Math.max(90, textRenderer.getWidth(Text.translatable("openpc.ui.close_case")) + 12));
        ButtonWidget close = ButtonWidget.builder(Text.translatable("openpc.ui.close_case"), b -> {
            openCase = false;
            buttonSignature = Integer.MIN_VALUE;
        }).dimensions(width - closeW - 12, 8, closeW, 12).build();
        addDrawableChild(close);
        int cx = viewX + viewW / 2;

        if (working.motherboardId() == null) {
            openSections.add(new OpenSection(cx - 70, viewY + 26, Text.translatable("openpc.ui.section_motherboard")));
            int y = viewY + 40;
            for (HardwareDefinition definition : HardwareRegistry.ofCategory(HardwareCategory.MOTHERBOARD)) {
                if (y > viewY + viewH - 16) {
                    break;
                }
                if (addInstallButton(cx - 100, y, 200, definition, () -> installMotherboard(definition))) {
                    y += 14;
                }
            }
            return;
        }

        int colW = Math.min(160, Math.max(130, (viewW - 56) / 2));
        int leftX = viewX + 12;
        int rightX = viewX + viewW - 12 - colW;
        int bottomLimit = viewY + viewH - 16;

        int leftY = viewY + 14;
        leftY = addPartRow(leftY, leftX, colW, bottomLimit,
                working.motherboardId(), this::removeMotherboard,
                HardwareCategory.MOTHERBOARD, def -> installMotherboard(def));
        leftY = addPartRow(leftY, leftX, colW, bottomLimit,
                working.cpuId(), () -> clearSlot(HardwareCategory.CPU),
                HardwareCategory.CPU, def -> installSingle(HardwareCategory.CPU, def));
        leftY = addRamRows(leftY, leftX, colW, bottomLimit);
        leftY = addPartRow(leftY, leftX, colW, bottomLimit,
                working.storageId(), () -> clearSlot(HardwareCategory.STORAGE),
                HardwareCategory.STORAGE, def -> installSingle(HardwareCategory.STORAGE, def));

        int rightY = viewY + 14;
        rightY = addPartRow(rightY, rightX, colW, bottomLimit,
                working.gpuId(), () -> clearSlot(HardwareCategory.GPU),
                HardwareCategory.GPU, def -> installSingle(HardwareCategory.GPU, def));
        rightY = addPartRow(rightY, rightX, colW, bottomLimit,
                working.audioId(), () -> clearSlot(HardwareCategory.AUDIO),
                HardwareCategory.AUDIO, def -> installSingle(HardwareCategory.AUDIO, def));
        rightY = addPartRow(rightY, rightX, colW, bottomLimit,
                working.networkId(), () -> clearSlot(HardwareCategory.NETWORK),
                HardwareCategory.NETWORK, def -> installSingle(HardwareCategory.NETWORK, def));
        rightY = addPartRow(rightY, rightX, colW, bottomLimit,
                working.opticalId(), () -> clearSlot(HardwareCategory.OPTICAL),
                HardwareCategory.OPTICAL, def -> installSingle(HardwareCategory.OPTICAL, def));
        rightY = addPartRow(rightY, rightX, colW, bottomLimit,
                working.floppyId(), () -> clearSlot(HardwareCategory.FLOPPY),
                HardwareCategory.FLOPPY, def -> installSingle(HardwareCategory.FLOPPY, def));
        rightY = addExpansionRows(rightY, rightX, colW, bottomLimit);
    }

    private int addPartRow(int y, int x, int w, int bottomLimit,
                           String installedId, Runnable remove,
                           HardwareCategory category, java.util.function.Consumer<HardwareDefinition> install) {
        addHeader(x, y, sectionHeaderKey(category));
        y += 11;
        if (installedId != null) {
            addRemoveChip(x, y, w, installedId, remove);
            return y + 16;
        }
        for (HardwareDefinition definition : HardwareRegistry.ofCategory(category)) {
            if (y > bottomLimit) {
                break;
            }
            if (addInstallButton(x, y, w, definition, () -> install.accept(definition))) {
                y += 14;
            }
        }
        return y + 6;
    }

    private int addRamRows(int y, int x, int w, int bottomLimit) {
        addHeader(x, y, "openpc.ui.section_ram");
        y += 11;
        boolean installOffered = false;
        for (int i = 0; i < working.ramSlots().size(); i++) {
            String id = working.ramAt(i);
            if (id != null) {
                if (y > bottomLimit) {
                    break;
                }
                int slot = i;
                addRemoveChip(x, y, w, id, () -> {
                    working.removeRamAt(slot);
                    commitDraft();
                });
                y += 14;
            } else if (!installOffered) {
                addHeader(x, y, Text.translatable("openpc.ui.ram_slot", i + 1));
                y += 11;
                int slot = firstEmptyRamSlot();
                for (HardwareDefinition definition : HardwareRegistry.ofCategory(HardwareCategory.RAM)) {
                    if (y > bottomLimit) {
                        break;
                    }
                    if (addInstallButton(x, y, w, definition, () -> installRam(slot, definition))) {
                        y += 14;
                    }
                }
                installOffered = true;
            }
        }
        return y + 6;
    }

    private int addExpansionRows(int y, int x, int w, int bottomLimit) {
        addHeader(x, y, "openpc.ui.section_expansion");
        y += 11;
        boolean installOffered = false;
        for (int i = 0; i < working.expansionSlots().size(); i++) {
            String id = working.expansionAt(i);
            if (id != null) {
                if (y > bottomLimit) {
                    break;
                }
                int slot = i;
                addRemoveChip(x, y, w, id, () -> {
                    working.removeExpansionAt(slot);
                    commitDraft();
                });
                y += 14;
            } else if (!installOffered) {
                addHeader(x, y, Text.translatable("openpc.ui.expansion_slot", i + 1));
                y += 11;
                int slot = firstEmptyExpansionSlot();
                for (HardwareDefinition definition : HardwareRegistry.ofCategory(HardwareCategory.EXPANSION)) {
                    if (y > bottomLimit) {
                        break;
                    }
                    if (addInstallButton(x, y, w, definition, () -> installExpansion(slot, definition))) {
                        y += 14;
                    }
                }
                installOffered = true;
            }
        }
        return y + 6;
    }

    private void addHeader(int x, int y, String key) {
        addHeader(x, y, Text.translatable(key));
    }

    private void addHeader(int x, int y, Text text) {
        openSections.add(new OpenSection(x, y, text));
    }

    private static String sectionHeaderKey(HardwareCategory category) {
        return switch (category) {
            case MOTHERBOARD -> "openpc.ui.section_motherboard";
            case CPU -> "openpc.ui.section_cpu";
            case STORAGE -> "openpc.ui.section_storage";
            case GPU -> "openpc.ui.section_gpu";
            case AUDIO -> "openpc.ui.section_audio";
            case NETWORK -> "openpc.ui.section_network";
            case OPTICAL -> "openpc.ui.section_optical";
            case FLOPPY -> "openpc.ui.section_floppy";
            case EXPANSION -> "openpc.ui.section_expansion";
            case RAM -> "openpc.ui.section_ram";
        };
    }

    private void addRemoveChip(int x, int y, int width, String definitionId, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal("x"), b -> action.run())
                .dimensions(x + width - 12, y, 12, 12)
                .build());
        installedLabels.add(new InstalledLabel(x, y + 3, definitionId));
    }

    private boolean addInstallButton(int x, int y, int width, HardwareDefinition definition, Runnable action) {
        if (!hasAvailableItem(definition)) {
            return false;
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable(definition.translationKey()), b -> action.run())
                .dimensions(x, y, width, 12)
                .build());
        return true;
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
        drawBuilderPanels(context);
        super.render(context, mouseX, mouseY, tickDelta);
        drawHud(context);
        for (OverlayIcon icon : overlayIcons) {
            context.drawItem(icon.stack(), icon.x(), icon.y());
        }
        drawInstalledLabels(context);
    }

    private void drawBuilderPanels(DrawContext context) {
        if (!openCase) {
            int top = viewY;
            int bot = height - 28;
            context.fill(layoutSideX, top, layoutSideX + layoutSideW, bot, colorWithAlpha(0x10151B, 0.95f));
            context.fill(layoutSideX, top, layoutSideX + 2, bot, 0xFF3A6EA5);
            int[] bounds = {layoutFieldY - 3, layoutPowerY - 3, layoutOpenY - 3, layoutCpuY - 3, layoutMediaY - 3, layoutCompTextY - 3};
            for (int yy : bounds) {
                context.fill(layoutSideX + 8, yy, layoutSideX + layoutSideW - 6, yy + 1, colorWithAlpha(0xFFFFFF, 0.06f));
            }
        }
        context.fill(0, height - 27, width, height, colorWithAlpha(0x0A0E13, 0.92f));
        context.fill(0, height - 28, width, height - 27, 0xFF3A6EA5);
        context.fill(viewX - 2, viewY - 2, viewX + viewW + 2, viewY - 1, 0xFF335577);
        context.fill(viewX - 2, viewY + viewH + 1, viewX + viewW + 2, viewY + viewH + 2, 0xFF335577);
        context.fill(viewX - 2, viewY - 2, viewX - 1, viewY + viewH + 2, 0xFF335577);
        context.fill(viewX + viewW + 1, viewY - 2, viewX + viewW + 2, viewY + viewH + 2, 0xFF335577);
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

    private List<Text> componentLabels() {
        List<Text> lines = new ArrayList<>();
        componentLine(lines, "openpc.category.motherboard", working.motherboardId());
        componentLine(lines, "openpc.category.cpu", working.cpuId());
        List<String> ram = new ArrayList<>();
        for (int i = 0; i < working.ramSlots().size(); i++) {
            String id = working.ramAt(i);
            if (id == null) {
                continue;
            }
            HardwareDefinition def = HardwareRegistry.find(id).orElse(null);
            if (def == null) {
                continue;
            }
            long mb = def.getLong(dev.redstone.openpc.hardware.HardwareDefinitions.PROP_CAPACITY_MB, 0);
            ram.add(Text.translatable(def.translationKey()).getString()
                    + " (" + (mb >= 1024 ? (mb / 1024) + " GB" : mb + " MB") + ")");
        }
        componentLine(lines, "openpc.category.ram", ram);
        componentLine(lines, "openpc.category.storage", storageDescription());
        componentLine(lines, "openpc.category.gpu", working.gpuId());
        componentLine(lines, "openpc.category.audio", working.audioId());
        componentLine(lines, "openpc.category.network", working.networkId());
        componentLine(lines, "openpc.category.optical", working.opticalId());
        componentLine(lines, "openpc.category.floppy", working.floppyId());
        List<String> expansion = new ArrayList<>();
        for (int i = 0; i < working.expansionSlots().size(); i++) {
            String id = working.expansionAt(i);
            if (id != null) {
                expansion.add(id);
            }
        }
        componentLine(lines, "openpc.category.expansion", expansion);
        return lines;
    }

    private String storageDescription() {
        if (working.storageId() == null) {
            return null;
        }
        HardwareDefinition def = HardwareRegistry.find(working.storageId()).orElse(null);
        if (def == null) {
            return null;
        }
        long mb = working.installedStorageMegabytes();
        return Text.translatable(def.translationKey()).getString()
                + " (" + (mb >= 1024 ? (mb / 1024) + " GB" : mb + " MB") + ")";
    }

    private void componentLine(List<Text> lines, String categoryKey, String definitionId) {
        if (definitionId == null) {
            return;
        }
        componentLine(lines, categoryKey, java.util.List.of(definitionId));
    }

    private void componentLine(List<Text> lines, String categoryKey, List<String> ids) {
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            if (id == null) {
                continue;
            }
            HardwareDefinition def = HardwareRegistry.find(id).orElse(null);
            if (def != null) {
                names.add(Text.translatable(def.translationKey()).getString());
            }
        }
        if (names.isEmpty()) {
            return;
        }
        lines.add(Text.translatable(categoryKey)
                .append(Text.literal(": "))
                .append(Text.literal(String.join(", ", names))));
    }

    private void drawHud(DrawContext context) {
        context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.builder_header"), 12, 10, 0xFFFFFF);
        if (openCase) {
            for (OpenSection section : openSections) {
                context.drawTextWithShadow(textRenderer, section.text(), section.x(), section.y(), 0xFF8FC7FF);
            }
            context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.put_panel_back"), 12, 24, 0x80909B);
        } else {
            int xs = layoutSideX + 10;
            int accent = 0xFF8FC7FF;
            context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.section_name"), xs, layoutFieldY - 11, accent);
            context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.section_power"), xs, layoutPowerY - 13, accent);
            context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.section_cpu"), xs, layoutCpuY - 13, accent);
            context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.section_media"), xs, layoutMediaY - 13, accent);
            context.drawTextWithShadow(textRenderer, Text.translatable("openpc.ui.section_installed"), xs, layoutCompTextY - 13, accent);
            context.drawTextWithShadow(textRenderer, Text.literal("PC-" + working.pcId()), 12, 24, 0x80909B);

            List<Text> labels = componentLabels();
            for (int i = 0; i < Math.min(labels.size(), layoutCompLines); i++) {
                context.drawTextWithShadow(textRenderer, labels.get(i), xs, layoutCompTextY + i * 9, 0xFFFFFF);
            }
        }

        if (lastSendFailed) {
            context.drawTextWithShadow(textRenderer, Text.translatable("openpc.error.edit_rejected"), 12, height - 20, 0xFF7777);
        }
        List<Text> errors = currentValidation == null ? List.of() : currentValidation.errors();
        int errY = height - 34 - Math.min(4, errors.size()) * 10;
        for (int i = 0; i < Math.min(4, errors.size()); i++) {
            context.drawTextWithShadow(textRenderer, errors.get(i), 12, errY + i * 10, 0xFF8888);
        }
        Text close = Text.translatable("openpc.ui.close_hint");
        context.drawTextWithShadow(textRenderer, close, width - textRenderer.getWidth(close) - 12, height - 20, 0x60707B);
    }

    private void drawInstalledLabels(DrawContext context) {
        for (InstalledLabel label : installedLabels) {
            HardwareRegistry.find(label.definitionId()).ifPresent(definition -> {
                String name = Text.translatable(definition.translationKey()).getString();
                context.drawTextWithShadow(textRenderer, Text.literal(fitLabel(name, width - label.x() - 4)), label.x(), label.y(), 0xFFFFFF);
            });
        }
    }

    private String fitLabel(String label, int maxWidth) {
        if (textRenderer.getWidth(label) <= maxWidth) {
            return label;
        }
        String ellipsis = "...";
        String tail = ellipsis;
        int index = label.length() - 1;
        while (index >= 0 && textRenderer.getWidth(ellipsis + label.substring(index)) > maxWidth) {
            index--;
        }
        return ellipsis + label.substring(Math.max(0, index + 1));
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

    private record InstalledLabel(int x, int y, String definitionId) {
    }

    private record OpenSection(int x, int y, Text text) {
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

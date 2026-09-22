package dev.redstone.openpc.service;

import dev.redstone.openpc.OpenpcItems;
import dev.redstone.openpc.OpenpcNetworking;
import dev.redstone.openpc.block.entity.PcCaseBlockEntity;
import dev.redstone.openpc.data.PcConfig;
import dev.redstone.openpc.data.PcDataStore;
import dev.redstone.openpc.data.PcPowerState;
import dev.redstone.openpc.validation.PcValidation;
import dev.redstone.openpc.validation.PcValidationResult;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PcService {

    private static final Map<Long, UUID> ACTIVE_OWNERS = new HashMap<>();
    private static final double USE_RANGE_SQUARED = 64.0;

    private PcService() {
    }

    public static boolean isWithinUseRange(ServerPlayerEntity player, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        return player.squaredDistanceTo(x, y, z) <= USE_RANGE_SQUARED;
    }

    public static PcConfig ensurePcExists(ServerWorld world, BlockPos pos) {
        PcCaseBlockEntity blockEntity = blockEntity(world, pos);
        if (blockEntity == null) {
            return null;
        }
        PcConfig config = blockEntity.getConfig();
        if (config == null || config.pcId() == 0) {
            long existingId = config == null ? 0 : config.pcId();
            PcConfig fresh = (config == null ? new PcConfig(0) : config).copyWithPcId(
                    existingId == 0 ? PcDataStore.allocateUniquePcId() : existingId);
            blockEntity.setConfig(fresh);
            config = fresh;
        }
        return config;
    }

    public static PcConfig currentConfig(ServerWorld world, BlockPos pos) {
        PcCaseBlockEntity blockEntity = blockEntity(world, pos);
        return blockEntity == null ? null : blockEntity.getConfig();
    }

    public static PcValidationResult applyModification(ServerPlayerEntity player, BlockPos pos, PcConfig proposed) {
        if (!isWithinUseRange(player, pos)) {
            return PcValidationResult.failed(List.of(Text.translatable("openpc.error.too_far")));
        }
        ServerWorld world = worldOf(player);
        PcCaseBlockEntity blockEntity = blockEntity(world, pos);
        if (blockEntity == null) {
            return PcValidationResult.failed(List.of(Text.translatable("openpc.error.no_block_entity")));
        }

        PcConfig current = ensurePcExists(world, pos);
        if (current == null) {
            return PcValidationResult.failed(List.of(Text.translatable("openpc.error.no_block_entity")));
        }
        if (current.isPowerOn()) {
            return PcValidationResult.failed(List.of(Text.translatable("openpc.error.edit_while_running")));
        }
        if (proposed.pcId() != 0 && proposed.pcId() != current.pcId()) {
            return PcValidationResult.failed(List.of(Text.translatable("openpc.error.no_block_entity")));
        }

        PcConfig next = proposed.copyWithPcId(current.pcId());
        next.setPowerState(PcPowerState.OFF);

        PcValidationResult validation = PcValidation.validateEditable(next);
        if (!validation.isValid()) {
            OpenpcNetworking.sendSnapshotTo(player, pos, current);
            return validation;
        }

        PcValidationResult inventoryResult = applyInventoryCost(player, current, next);
        if (!inventoryResult.isValid()) {
            OpenpcNetworking.sendSnapshotTo(player, pos, current);
            return inventoryResult;
        }

        applyStorageDiskChanges(current, next);
        blockEntity.setConfig(next);
        OpenpcNetworking.sendSnapshotTo(player, pos, next);
        return PcValidationResult.ok();
    }

    public static PcValidationResult applyPower(ServerPlayerEntity player, BlockPos pos, boolean start) {
        if (!isWithinUseRange(player, pos)) {
            return PcValidationResult.failed(List.of(Text.translatable("openpc.error.too_far")));
        }
        ServerWorld world = worldOf(player);
        PcCaseBlockEntity blockEntity = blockEntity(world, pos);
        if (blockEntity == null) {
            return PcValidationResult.failed(List.of(Text.translatable("openpc.error.no_block_entity")));
        }

        PcConfig config = ensurePcExists(world, pos);
        if (config == null) {
            return PcValidationResult.failed(List.of(Text.translatable("openpc.error.no_block_entity")));
        }

        if (start) {
            PcValidationResult validation = PcValidation.validate(config);
            if (!validation.isValid()) {
                OpenpcNetworking.sendSnapshotTo(player, pos, config);
                return validation;
            }
            UUID previousOwner = ACTIVE_OWNERS.get(config.pcId());
            if (previousOwner != null && !previousOwner.equals(player.getUuid())) {
                return PcValidationResult.failed(List.of(Text.translatable("openpc.error.already_running_elsewhere")));
            }
            config.setPowerState(PcPowerState.RUNNING);
            ACTIVE_OWNERS.put(config.pcId(), player.getUuid());
        } else {
            config.setPowerState(PcPowerState.OFF);
            ACTIVE_OWNERS.remove(config.pcId());
        }

        blockEntity.setConfig(config);
        OpenpcNetworking.sendSnapshotTo(player, pos, config);
        return PcValidationResult.ok();
    }

    public static void releaseOwner(long pcId) {
        ACTIVE_OWNERS.remove(pcId);
    }

    public static boolean isActive(long pcId) {
        return ACTIVE_OWNERS.containsKey(pcId);
    }

    private static PcCaseBlockEntity blockEntity(ServerWorld world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof PcCaseBlockEntity blockEntity) {
            return blockEntity;
        }
        return null;
    }

    private static ServerWorld worldOf(ServerPlayerEntity player) {
        return (ServerWorld) player.getEntityWorld();
    }

    private static PcValidationResult applyInventoryCost(ServerPlayerEntity player, PcConfig current, PcConfig proposed) {
        if (player.isCreative()) {
            return PcValidationResult.ok();
        }

        Map<String, Integer> currentParts = partCounts(current);
        Map<String, Integer> proposedParts = partCounts(proposed);
        List<String> additions = new ArrayList<>();
        List<String> removals = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : proposedParts.entrySet()) {
            int missing = entry.getValue() - currentParts.getOrDefault(entry.getKey(), 0);
            for (int i = 0; i < missing; i++) {
                additions.add(entry.getKey());
            }
        }
        for (Map.Entry<String, Integer> entry : currentParts.entrySet()) {
            int removed = entry.getValue() - proposedParts.getOrDefault(entry.getKey(), 0);
            for (int i = 0; i < removed; i++) {
                removals.add(entry.getKey());
            }
        }

        Map<Item, Integer> required = new HashMap<>();
        for (String id : additions) {
            Item item = OpenpcItems.forHardware(id);
            if (item == null) {
                return PcValidationResult.failed(List.of(Text.translatable("openpc.error.unknown_component", id)));
            }
            required.put(item, required.getOrDefault(item, 0) + 1);
        }
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            if (countItem(player, entry.getKey()) < entry.getValue()) {
                return PcValidationResult.failed(List.of(Text.translatable("openpc.error.missing_item")));
            }
        }
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            consumeItem(player, entry.getKey(), entry.getValue());
        }
        for (String id : removals) {
            Item item = OpenpcItems.forHardware(id);
            if (item != null) {
                player.getInventory().offerOrDrop(new ItemStack(item));
            }
        }
        player.getInventory().markDirty();
        return PcValidationResult.ok();
    }

    private static void applyStorageDiskChanges(PcConfig current, PcConfig next) {
        for (int slot = 0; slot < PcConfig.MAX_STORAGE_SLOTS; slot++) {
            boolean wasFilled = current.storageAt(slot) != null;
            boolean nowFilled = next.storageAt(slot) != null;
            if (wasFilled && !nowFilled) {
                // The HDD was removed, so delete its disk image too.
                PcDataStore.deleteDiskFile(next.pcId(), slot);
            } else if (!wasFilled && nowFilled) {
                // A drive was added into a slot that may still hold an orphaned default
                // 'test' disk from older versions (created when no storage configured).
                // Discard it so a correctly-sized image is created on next boot.
                PcDataStore.deleteDiskFile(next.pcId(), slot);
            }
        }
    }

    private static Map<String, Integer> partCounts(PcConfig config) {
        Map<String, Integer> counts = new HashMap<>();
        if (config == null) {
            return counts;
        }
        addPart(counts, config.motherboardId());
        addPart(counts, config.cpuId());
        for (String id : config.storageSlots()) {
            if (id != null) {
                addPart(counts, id);
            }
        }
        addPart(counts, config.gpuId());
        addPart(counts, config.audioId());
        addPart(counts, config.networkId());
        addPart(counts, config.opticalId());
        addPart(counts, config.floppyId());
        for (String id : config.ramSlots()) {
            addPart(counts, id);
        }
        for (String id : config.expansionSlots()) {
            addPart(counts, id);
        }
        return counts;
    }

    private static void addPart(Map<String, Integer> counts, String id) {
        if (id != null) {
            counts.put(id, counts.getOrDefault(id, 0) + 1);
        }
    }

    private static int countItem(ServerPlayerEntity player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void consumeItem(ServerPlayerEntity player, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                int removed = Math.min(remaining, stack.getCount());
                stack.decrement(removed);
                remaining -= removed;
            }
        }
    }
}

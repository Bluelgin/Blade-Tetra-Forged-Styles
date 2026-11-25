package dev.bladetetra.export;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** The work slot belongs to the block's chunk and has one depositor while occupied. */
final class BladeSealWorkbenchStorage extends BlockEntity {
    private final SimpleContainer inventory = new SimpleContainer(1);
    private UUID depositor;

    BladeSealWorkbenchStorage(BlockPos position, BlockState state) {
        super(BladeSealWorkbench.STORAGE.get(), position, state);
        inventory.addListener(container -> {
            if (container.isEmpty()) depositor = null;
            setChanged();
        });
    }

    SimpleContainer inventory() {
        return inventory;
    }

    boolean canUse(Player player) {
        return depositor == null || depositor.equals(player.getUUID());
    }

    void recordDepositor(Player player, ItemStack stack) {
        if (!stack.isEmpty()) {
            depositor = player.getUUID();
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ItemStack blade = inventory.getItem(0);
        if (!blade.isEmpty()) {
            tag.put("Blade", blade.save(new CompoundTag()));
            if (depositor != null) tag.putUUID("Depositor", depositor);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.setItem(0, tag.contains("Blade", Tag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound("Blade")) : ItemStack.EMPTY);
        depositor = !inventory.isEmpty() && tag.hasUUID("Depositor") ? tag.getUUID("Depositor") : null;
    }
}

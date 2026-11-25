package dev.bladetetra.export;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BladeSealWorkbench extends Block implements EntityBlock {
    private static final String ID = "blade_sealing_workbench";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BladeTetra.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, BladeTetra.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, BladeTetra.MOD_ID);
    public static final RegistryObject<Block> BLOCK = BLOCKS.register(ID, BladeSealWorkbench::new);
    static final RegistryObject<BlockEntityType<BladeSealWorkbenchStorage>> STORAGE = BLOCK_ENTITIES.register(ID,
            () -> BlockEntityType.Builder.of(BladeSealWorkbenchStorage::new, BLOCK.get()).build(null));
    public static final RegistryObject<Item> ITEM = ModItems.ITEMS.register(ID,
            () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<MenuType<WorkbenchMenu>> MENU = MENUS.register(ID,
            () -> IForgeMenuType.create((id, inventory, data) -> new WorkbenchMenu(id, inventory, null)));

    private BladeSealWorkbench() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F, 4.0F).sound(SoundType.WOOD));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        MENUS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new BladeSealWorkbenchStorage(position, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean moving) {
        if (!level.isClientSide && !state.is(replacement.getBlock())
                && level.getBlockEntity(position) instanceof BladeSealWorkbenchStorage storage) {
            Containers.dropContents(level, position, storage.inventory());
        }
        super.onRemove(state, level, position, replacement, moving);
    }

    static void open(ServerPlayer player, BlockPos position) {
        if (!(player.level().getBlockEntity(position) instanceof BladeSealWorkbenchStorage storage)) return;
        if (!storage.canUse(player)) {
            player.displayClientMessage(Component.translatable("blade_tetra.export.workbench.occupied"), true);
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) -> new WorkbenchMenu(id, inventory, storage),
                Component.translatable("block.blade_tetra." + ID)));
    }

    public static final class WorkbenchMenu extends AbstractContainerMenu {
        static final int OPEN_STUDIO = 0;
        private static final int INVENTORY_END = 37;
        private final BladeSealWorkbenchStorage storage;
        private final SimpleContainer work;

        WorkbenchMenu(int id, Inventory inventory, BladeSealWorkbenchStorage storage) {
            super(MENU.get(), id);
            this.storage = storage;
            work = storage == null ? new SimpleContainer(1) : storage.inventory();
            addSlot(new Slot(work, 0, 80, 24) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof ModularSlashBladeItem
                            && (storage == null || storage.canUse(inventory.player));
                }

                @Override
                public boolean mayPickup(Player player) {
                    return storage == null || storage.canUse(player);
                }

                @Override
                public void set(ItemStack stack) {
                    if (storage != null) storage.recordDepositor(inventory.player, stack);
                    super.set(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 9; column++) {
                    addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 83 + row * 18));
                }
            }
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column, 8 + column * 18, 141));
            }
        }

        ItemStack blade() {
            return work.getItem(0);
        }

        @Override
        public boolean clickMenuButton(Player player, int button) {
            return player instanceof ServerPlayer serverPlayer && button == OPEN_STUDIO && stillValid(player)
                    && BladeSealSessions.open(serverPlayer, this);
        }

        @Override
        public boolean stillValid(Player player) {
            if (storage == null) return true;
            var pos = storage.getBlockPos();
            return !storage.isRemoved() && storage.canUse(player)
                    && player.level() == storage.getLevel() && player.level().hasChunkAt(pos)
                    && player.level().getBlockEntity(pos) == storage
                    && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
            Slot source = slots.get(index);
            if (!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;
            ItemStack stack = source.getItem();
            ItemStack original = stack.copy();
            boolean moved = index == 0 ? moveItemStackTo(stack, 1, INVENTORY_END, true)
                    : moveItemStackTo(stack, 0, 1, false);
            if (!moved) return ItemStack.EMPTY;
            if (stack.isEmpty()) source.set(ItemStack.EMPTY);
            else source.setChanged();
            source.onTake(player, stack);
            return original;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            if (player instanceof ServerPlayer serverPlayer) {
                BladeSealSessions.close(serverPlayer, this);
            }
        }
    }
}

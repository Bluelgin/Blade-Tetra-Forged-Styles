package dev.bladetetra.forging;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.network.LegacyImprintOpenPacket;
import dev.bladetetra.network.LegacyImprintBeginPacket;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.entity.BladeStandEntity;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** Server-authoritative state and result handling for the imprinting minigame. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyImprinting {
    private static final String SESSION = "blade_tetra_legacy_imprint";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation BASIC_WORKBENCH =
            new ResourceLocation("tetra", "basic_workbench");
    private static final ResourceLocation FORGED_WORKBENCH =
            new ResourceLocation("tetra", "forged_workbench");
    private static final ResourceLocation PROUDSOUL_SPHERE =
            new ResourceLocation("slashblade", "proudsoul_sphere");
    private static final ResourceLocation PROUDSOUL_CRYSTAL =
            new ResourceLocation("slashblade", "proudsoul_crystal");
    private static final ResourceLocation PROUDSOUL_TRAPEZOHEDRON =
            new ResourceLocation("slashblade", "proudsoul_trapezohedron");

    public static InteractionResult tryBegin(UseOnContext context) {
        return tryBegin(context.getLevel(), context.getPlayer(), context.getHand(),
                context.getClickedPos());
    }

    /** Runs before Tetra opens its workbench so the held scroll gets first refusal. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickWorkbench(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(ModItems.LEGACY_IMPRINT_SCROLL.get())) {
            return;
        }
        if (!event.getEntity().isShiftKeyDown() || !isWorkbench(event.getLevel(), event.getPos())) {
            return;
        }
        InteractionResult result;
        if (event.getLevel().isClientSide) {
            ModNetwork.CHANNEL.sendToServer(new LegacyImprintBeginPacket(
                    event.getPos(), event.getHand() == InteractionHand.OFF_HAND));
            result = InteractionResult.SUCCESS;
        } else {
            result = tryBegin(event.getLevel(), event.getEntity(), event.getHand(),
                    event.getPos());
        }
        if (result.consumesAction()) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    public static void beginFromPacket(ServerPlayer player, InteractionHand hand,
            BlockPos pos) {
        if (!player.getItemInHand(hand).is(ModItems.LEGACY_IMPRINT_SCROLL.get())
                || !player.isShiftKeyDown()
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0D) {
            return;
        }
        LOGGER.info("{} requested legacy imprinting at {}", player.getScoreboardName(), pos);
        tryBegin(player.level(), player, hand, pos);
    }

    private static InteractionResult tryBegin(Level level,
            net.minecraft.world.entity.player.Player interactingPlayer,
            InteractionHand hand, BlockPos clickedPos) {
        if (interactingPlayer == null || !interactingPlayer.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!isWorkbench(level, clickedPos)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(interactingPlayer instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        // A physical click may reach us through both Forge's server event and
        // the explicit client packet. Treat those as one request so the screen
        // and its server-side session are not opened twice.
        CompoundTag activeSession = player.getPersistentData().getCompound(SESSION);
        if (!activeSession.isEmpty()
                && activeSession.getLong("workbench") == clickedPos.asLong()
                && level.getGameTime() - activeSession.getLong("started") <= 5L) {
            return InteractionResult.CONSUME;
        }

        BladeStandEntity stand = level.getEntitiesOfClass(
                        BladeStandEntity.class,
                        standColumn(clickedPos),
                        candidate -> identify(candidate.getItem()) != null)
                .stream()
                .min(java.util.Comparator.comparingDouble(candidate ->
                        Math.abs(candidate.getY() - (clickedPos.getY() + 1.15D))))
                .orElse(null);
        if (stand == null) {
            LOGGER.info("Legacy imprinting found no supported blade stand directly above {}",
                    clickedPos);
            player.displayClientMessage(Component.translatable(
                    "message.blade_tetra.imprint.no_blade_stand").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        LegacyImprintKind kind = identify(stand.getItem());
        LOGGER.info("Legacy imprinting recognized {} on blade stand {}", kind.id(), stand.getId());
        Booster booster = Booster.find(player.getInventory());
        CompoundTag session = new CompoundTag();
        session.putInt("stand", stand.getId());
        session.putString("kind", kind.id());
        session.putLong("workbench", clickedPos.asLong());
        session.putLong("started", level.getGameTime());
        session.putString("hand", hand.name());
        player.getPersistentData().put(SESSION, session);

        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new LegacyImprintOpenPacket(kind.id(), booster.bonus, booster.protects,
                        LegacyCalibration.known(player, kind)));
        return InteractionResult.CONSUME;
    }

    private static boolean isWorkbench(Level level, BlockPos pos) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
        return BASIC_WORKBENCH.equals(id) || FORGED_WORKBENCH.equals(id);
    }

    public static void finish(ServerPlayer player, int accuracy,
            LegacyCalibrationProfile profile) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(SESSION)) {
            return;
        }
        CompoundTag session = data.getCompound(SESSION);
        data.remove(SESSION);
        long elapsed = player.level().getGameTime() - session.getLong("started");
        // Advanced assembly editing can take longer than the tracing minigame.
        if (elapsed < 10L || elapsed > 20L * 600L) {
            return;
        }

        int standId = session.getInt("stand");
        BlockPos workbench = BlockPos.of(session.getLong("workbench"));
        if (!isWorkbench(player.level(), workbench)
                || !(player.level().getEntity(standId) instanceof BladeStandEntity stand)
                || player.distanceToSqr(stand) > 100.0D
                || !standColumn(workbench).contains(stand.position())) {
            return;
        }
        LegacyImprintKind kind;
        try {
            kind = NamedLegacyCatalog.get(session.getString("kind"));
        } catch (IllegalArgumentException exception) {
            return;
        }
        if (kind == null || !kind.equals(identify(stand.getItem())) || !consumeBlankScroll(player)) {
            return;
        }

        Booster booster = Booster.find(player.getInventory());
        booster.consume(player.getInventory());
        int skill = Math.max(0, Math.min(100, accuracy));
        int chance = skill >= 90 ? 100
                : skill >= 70 ? Math.min(95, 65 + (skill - 70) + booster.bonus)
                : skill >= 50 ? Math.min(65, 20 + (skill - 50) + booster.bonus)
                : Math.min(30, 5 + booster.bonus / 2);
        boolean success = chance >= 100 || player.getRandom().nextInt(100) < chance;
        if (success) {
            ItemStack result = ForgingScrolls.create(kind);
            // Keep historical calibration data intact; new research is automatic.
            if (!player.getInventory().add(result)) {
                player.drop(result, false);
            }
            player.displayClientMessage(Component.translatable(
                    "message.blade_tetra.imprint.success", Component.translatable(kind.translationKey()))
                    .withStyle(ChatFormatting.GOLD), false);
            return;
        }

        int miss = Math.max(0, 70 - skill);
        ItemStack blade = stand.getItem();
        blade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            int damage = Math.max(8, Math.round(state.getMaxDamage()
                    * (miss >= 38 ? 0.45F : 0.18F)));
            state.setDamage(Math.min(state.getMaxDamage() - 1, state.getDamage() + damage));
            if (miss >= 38 && !booster.protects) {
                state.setBroken(true);
            }
        });
        // Item frames synchronize their displayed stack through entity data;
        // re-setting the mutated stack makes the damaged/broken state visible
        // immediately and ensures it is persisted with the stand.
        stand.setItem(blade, false);
        player.displayClientMessage(Component.translatable(miss >= 38 && !booster.protects
                        ? "message.blade_tetra.imprint.fail_broken"
                        : "message.blade_tetra.imprint.fail_damaged")
                .withStyle(ChatFormatting.RED), false);
    }

    private static boolean consumeBlankScroll(ServerPlayer player) {
        if (player.isCreative()) {
            return true;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.LEGACY_IMPRINT_SCROLL.get())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** Only the vertical column immediately over the clicked workbench is a valid altar. */
    private static AABB standColumn(BlockPos workbench) {
        return new AABB(
                workbench.getX() - 0.20D, workbench.getY() + 0.75D, workbench.getZ() - 0.20D,
                workbench.getX() + 1.20D, workbench.getY() + 2.60D, workbench.getZ() + 1.20D);
    }

    private static LegacyImprintKind identify(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemSlashBlade)) {
            return null;
        }
        return stack.getCapability(ItemSlashBlade.BLADESTATE)
                .resolve()
                .map(state -> LegacyImprintKind.fromTranslationKey(state.getTranslationKey()))
                .orElse(null);
    }

    private record Booster(ResourceLocation item, int bonus, boolean protects) {
        private static final Booster NONE = new Booster(null, 0, false);

        static Booster find(Inventory inventory) {
            if (contains(inventory, PROUDSOUL_TRAPEZOHEDRON)) {
                return new Booster(PROUDSOUL_TRAPEZOHEDRON, 25, true);
            }
            if (contains(inventory, PROUDSOUL_CRYSTAL)) {
                return new Booster(PROUDSOUL_CRYSTAL, 15, true);
            }
            if (contains(inventory, PROUDSOUL_SPHERE)) {
                return new Booster(PROUDSOUL_SPHERE, 8, false);
            }
            return NONE;
        }

        void consume(Inventory inventory) {
            if (item == null) {
                return;
            }
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (item.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))) {
                    stack.shrink(1);
                    return;
                }
            }
        }

        private static boolean contains(Inventory inventory, ResourceLocation id) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (id.equals(ForgeRegistries.ITEMS.getKey(inventory.getItem(i).getItem()))) {
                    return true;
                }
            }
            return false;
        }
    }

    private LegacyImprinting() {
    }
}

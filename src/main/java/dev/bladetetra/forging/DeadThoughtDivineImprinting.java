package dev.bladetetra.forging;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.LegacyFusionHandler;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.entity.BladeStandEntity;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Final forge step for the Divine-Domain route to Dead Thought.
 *
 * <p>Put a Blade Tetra modular SlashBlade on a SlashBlade stand directly above a
 * Tetra workbench, then sneak-interact with that stand while holding the 无生残印.
 * The seal is consumed only after the blade has been bound successfully.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DeadThoughtDivineImprinting {
    public static final String PLAYER_BOUND = "blade_tetra_dead_thought_divine_bound";

    private static final ResourceLocation BASIC_WORKBENCH =
            new ResourceLocation("tetra", "basic_workbench");
    private static final ResourceLocation FORGED_WORKBENCH =
            new ResourceLocation("tetra", "forged_workbench");

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractStand(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof BladeStandEntity stand)
                || !event.getEntity().isShiftKeyDown()
                || !event.getItemStack().is(ModItems.DEAD_THOUGHT_SOUL_SEAL.get())) {
            return;
        }

        // BladeStandEntity performs its real swap/pose logic only on the server.
        // Let the client send the ordinary entity-interaction packet, then take first
        // refusal server-side so the seal can never accidentally rotate/swap the stand.
        if (event.getLevel().isClientSide) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ItemStack blade = stand.getItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)) {
            player.displayClientMessage(Component.literal(
                    "无生残印只会回应你亲手锻成的 Blade Tetra 刀。")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!hasWorkbenchBelow(stand)) {
            player.displayClientMessage(Component.literal(
                    "把挂刀台置于 Tetra 工作台上方，再进行死念刻印。")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (DeadThoughtDivineLegacy.isBound(blade)) {
            player.displayClientMessage(Component.literal(
                    "这把刀已经记住了死念。")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        DeadThoughtDivineLegacy.bind(blade);
        blade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state ->
                LegacyFusionHandler.sync(blade, state));
        stand.setItem(blade, false);

        if (!player.getAbilities().instabuild) {
            event.getItemStack().shrink(1);
        }
        var data = player.getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(PLAYER_BOUND, true);
        data.put(Player.PERSISTED_NBT_TAG, persisted);

        player.level().playSound(null, stand.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
                0.9F, 0.65F);
        player.sendSystemMessage(Component.literal(
                "无生残印沉入刀身。血樱终景「无生」与命蚀已成为这把刀的死念异传。")
                .withStyle(ChatFormatting.DARK_RED));
    }

    private static boolean hasWorkbenchBelow(BladeStandEntity stand) {
        BlockPos base = stand.blockPosition();
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos pos = base.below(dy);
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(
                    stand.level().getBlockState(pos).getBlock());
            if (BASIC_WORKBENCH.equals(id) || FORGED_WORKBENCH.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private DeadThoughtDivineImprinting() {
    }
}

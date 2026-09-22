package dev.bladetetra.item;

import dev.bladetetra.combat.ForgedSlashArtPlan;
import dev.bladetetra.combat.ForgedSlashArtSpec;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import se.mickelus.tetra.gui.GuiModuleOffsets;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.items.modular.ModularItem;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Tetra-editable carrier for a player-authored Slash Art.
 *
 * <p>The four authoring modules live on this orb, never on the weapon. Using a
 * complete orb with a SlashBlade in the opposite hand snapshots the authored
 * specification onto that blade.</p>
 */
public final class ForgedSlashArtOrbItem extends ModularItem {
    public static final String SA_CORE_SLOT = "slashblade/sa_core";
    public static final String SA_PRIMARY_SLOT = "slashblade/sa_primary";
    public static final String SA_SECONDARY_SLOT = "slashblade/sa_secondary";
    public static final String SA_MODIFIER_SLOT = "slashblade/sa_modifier";

    public static final String SA_CORE_MODULE = "slashblade/sa_core";
    public static final String SA_PRIMARY_MODULE = "slashblade/sa_primary";
    public static final String SA_SECONDARY_MODULE = "slashblade/sa_secondary";
    public static final String SA_MODIFIER_MODULE = "slashblade/sa_modifier";

    private static final GuiModuleOffsets ORB_MINOR_OFFSETS =
            new GuiModuleOffsets(-14, -8, 14, -8, -14, 14, 14, 14);

    public ForgedSlashArtOrbItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
        this.majorModuleKeys = new String[0];
        this.minorModuleKeys = new String[] {
                SA_CORE_SLOT,
                SA_PRIMARY_SLOT,
                SA_SECONDARY_SLOT,
                SA_MODIFIER_SLOT
        };
        this.requiredModules = new String[0];
        this.canHone = false;
    }

    public ItemStack createDefaultStack() {
        ItemStack stack = new ItemStack(this);
        IModularItem.updateIdentifier(stack);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.blade_tetra.forged_slash_art_orb");
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public GuiModuleOffsets getMinorGuiOffsets(ItemStack itemStack) {
        return ORB_MINOR_OFFSETS;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        ForgedSlashArtPlan.appendOrbTooltip(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.blade_tetra.forged.orb.apply")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.blade_tetra.forged.orb.clear")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack orb = player.getItemInHand(hand);
        InteractionHand targetHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack blade = player.getItemInHand(targetHand);
        ISlashBladeState state = blade.getCapability(ItemSlashBlade.BLADESTATE)
                .orElse(null);
        if (state == null) {
            return InteractionResultHolder.pass(orb);
        }

        if (player.isShiftKeyDown()) {
            if (!ForgedSlashArtSpec.hasInscription(blade)) {
                return InteractionResultHolder.pass(orb);
            }
            if (!level.isClientSide) {
                clearInscription(blade, state);
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                player.displayClientMessage(Component.translatable(
                        "message.blade_tetra.forged.cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(orb, level.isClientSide);
        }

        ForgedSlashArtSpec spec = ForgedSlashArtSpec.fromOrb(orb);
        if (spec == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "message.blade_tetra.forged.incomplete"), true);
            }
            return InteractionResultHolder.fail(orb);
        }

        if (!level.isClientSide) {
            applyInscription(blade, state, spec);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            player.displayClientMessage(Component.translatable(
                    "message.blade_tetra.forged.applied"), true);
        }
        return InteractionResultHolder.sidedSuccess(orb, level.isClientSide);
    }

    private static void applyInscription(
            ItemStack blade, ISlashBladeState state, ForgedSlashArtSpec spec) {
        ResourceLocation forgedId = ModSlashBladeAbilities.FORGED_SLASH_ART.getId();
        if (blade.getItem() instanceof ModularSlashBladeItem modularBlade) {
            spec.writeToBlade(blade);
            modularBlade.syncDerivedBladeState(blade);
            return;
        }

        ResourceLocation current = state.getSlashArtsKey();
        if (!forgedId.equals(current)) {
            ForgedSlashArtSpec.rememberPreviousSlashArt(blade, current);
        }
        spec.writeToBlade(blade);
        state.setSlashArtsKey(forgedId);
    }

    private static void clearInscription(
            ItemStack blade, ISlashBladeState state) {
        ForgedSlashArtSpec.clearFromBlade(blade);
        if (blade.getItem() instanceof ModularSlashBladeItem modularBlade) {
            modularBlade.syncDerivedBladeState(blade);
            ForgedSlashArtSpec.clearPreviousSlashArt(blade);
            return;
        }

        if (ModSlashBladeAbilities.FORGED_SLASH_ART.getId()
                .equals(state.getSlashArtsKey())) {
            ResourceLocation previous = ForgedSlashArtSpec.previousSlashArt(blade);
            state.setSlashArtsKey(previous == null
                    ? SlashArtsRegistry.NONE.getId() : previous);
        }
        ForgedSlashArtSpec.clearPreviousSlashArt(blade);
    }
}

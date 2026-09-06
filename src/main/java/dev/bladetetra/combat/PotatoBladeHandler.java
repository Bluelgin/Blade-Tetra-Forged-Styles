package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.MaterialAppearance;
import java.util.List;
import java.util.UUID;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Six-potato-component novelty blade and its environmental states. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PotatoBladeHandler {
    private static final String POTATO = "potato";
    private static final UUID SOGGY_SPEED_MODIFIER =
            UUID.fromString("352bdd68-8e83-4b3f-a0fe-f10e84c8cc99");
    private static final double FRIED_DAMAGE_MULTIPLIER = 1.15D;
    private static final double SOGGY_DAMAGE_MULTIPLIER = 0.75D;

    public static boolean isPotatoBlade(ItemStack stack) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem)) {
            return false;
        }
        MaterialAppearance materials = MaterialAppearance.fromStack(stack);
        return materials.physicalComponentsMatch(POTATO);
    }

    public static State state(LivingEntity user, ItemStack stack) {
        if (user.isInWaterOrBubble()) {
            return State.SOGGY;
        }
        if (user.isOnFire()
                || EnchantmentHelper.getItemEnchantmentLevel(
                        Enchantments.FIRE_ASPECT, stack) > 0) {
            return State.FRIED;
        }
        return State.NORMAL;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSlash(SlashBladeEvent.DoSlashEvent event) {
        if (!isPotatoBlade(event.getBlade())) {
            return;
        }
        switch (state(event.getUser(), event.getBlade())) {
            case FRIED -> event.setDamage(
                    event.getDamage() * FRIED_DAMAGE_MULTIPLIER);
            case SOGGY -> event.setDamage(
                    event.getDamage() * SOGGY_DAMAGE_MULTIPLIER);
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onBladeHit(SlashBladeEvent.HitEvent event) {
        if (event.getUser().level().isClientSide()
                || !isPotatoBlade(event.getBlade())) {
            return;
        }
        State state = state(event.getUser(), event.getBlade());
        if (state == State.FRIED) {
            event.getTarget().setSecondsOnFire(2);
            event.getBlade().hurtAndBreak(
                    1,
                    event.getUser(),
                    ItemSlashBlade.getOnBroken(event.getBlade()));
        }

        if (event.getTarget().level() instanceof ServerLevel level) {
            level.sendParticles(
                    new ItemParticleOption(
                            ParticleTypes.ITEM,
                            new ItemStack(state == State.FRIED
                                    ? Items.BAKED_POTATO
                                    : Items.POTATO)),
                    event.getTarget().getX(),
                    event.getTarget().getY() + event.getTarget().getBbHeight() * 0.55D,
                    event.getTarget().getZ(),
                    state == State.SOGGY ? 3 : 6,
                    event.getTarget().getBbWidth() * 0.25D,
                    event.getTarget().getBbHeight() * 0.18D,
                    event.getTarget().getBbWidth() * 0.25D,
                    0.06D);
            level.playSound(
                    null,
                    event.getTarget().blockPosition(),
                    state == State.SOGGY
                            ? SoundEvents.SLIME_BLOCK_HIT
                            : SoundEvents.BAMBOO_WOOD_HIT,
                    SoundSource.PLAYERS,
                    0.35F,
                    state == State.FRIED ? 1.45F : state == State.SOGGY ? 0.72F : 1.15F);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        AttributeInstance attackSpeed = entity.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed == null) {
            return;
        }
        AttributeModifier existing = attackSpeed.getModifier(SOGGY_SPEED_MODIFIER);
        boolean soggy = isPotatoBlade(entity.getMainHandItem())
                && state(entity, entity.getMainHandItem()) == State.SOGGY;
        if (soggy && existing == null) {
            attackSpeed.addTransientModifier(new AttributeModifier(
                    SOGGY_SPEED_MODIFIER,
                    "Soggy potato blade",
                    -0.12D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!soggy && existing != null) {
            attackSpeed.removeModifier(SOGGY_SPEED_MODIFIER);
        }
    }

    public static void appendTooltip(
            ItemStack stack,
            Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        if (!isPotatoBlade(stack)) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.blade_tetra.potato_blade")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.blade_tetra.potato_blade.fried")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.blade_tetra.potato_blade.soggy")
                .withStyle(ChatFormatting.BLUE));
    }

    public enum State {
        NORMAL,
        FRIED,
        SOGGY
    }

    private PotatoBladeHandler() {
    }
}

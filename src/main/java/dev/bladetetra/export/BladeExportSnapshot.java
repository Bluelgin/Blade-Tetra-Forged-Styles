package dev.bladetetra.export;

import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.ComponentCombatValues;
import dev.bladetetra.combat.ComponentEffectResolver;
import dev.bladetetra.combat.ForgedSlashArtSpec;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.customstyle.CustomBladeLength;
import dev.bladetetra.customstyle.CustomStyleSpec;
import dev.bladetetra.easteregg.BladeLegacyEasterEggs;
import dev.bladetetra.easteregg.NbtSageEasterEgg;
import dev.bladetetra.easteregg.SoulLegacyState;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** The server reads the held blade directly. An exported recipe creates a fresh example of that build. */
public record BladeExportSnapshot(String displayName, float attack, int durability,
        ResourceLocation combo, ResourceLocation slashArt, List<ResourceLocation> specialEffects,
        List<EnchantmentValue> enchantments, List<AttributeValue> attributes,
        int color, boolean colorInverse, String carryType, Vec3 adjust,
        boolean bewitched, boolean sealed, boolean broken, boolean unbreakable,
        ResourceLocation model, ResourceLocation texture, String style,
        CustomStyle customStyle, ForgedArt forgedArt, Traditions traditions,
        ComponentEffects componentEffects, List<Soul> inscriptions, List<NamedPartProvenance> namedParts) {

    public BladeExportSnapshot {
        specialEffects = List.copyOf(specialEffects);
        enchantments = List.copyOf(enchantments);
        attributes = List.copyOf(attributes);
        inscriptions = List.copyOf(inscriptions);
        namedParts = List.copyOf(namedParts);
    }

    public static boolean mayExport(ServerPlayer player) {
        return player.isCreative() && player.hasPermissions(2);
    }

    public static BladeExportSnapshot capture(ServerPlayer player, InteractionHand bladeHand) {
        if (!mayExport(player)) {
            throw new IllegalStateException("Export requires an administrator in creative mode");
        }
        ItemStack stack = player.getItemInHand(bladeHand).copy();
        if (!(stack.getItem() instanceof ModularSlashBladeItem modular)) {
            throw new IllegalArgumentException("Hold a Blade Tetra modular blade to export");
        }

        var attributes = modular.getAttributeModifiers(EquipmentSlot.MAINHAND, stack).entries().stream()
                .map(entry -> new AttributeValue(
                        Objects.requireNonNull(ForgeRegistries.ATTRIBUTES.getKey(entry.getKey())),
                        entry.getValue().getId().toString(),
                        entry.getValue().getAmount(), entry.getValue().getOperation().name()))
                .sorted(Comparator.comparing((AttributeValue value) -> value.id().toString())
                        .thenComparing(AttributeValue::modifierId))
                .toList();
        var enchantments = EnchantmentHelper.getEnchantments(stack).entrySet().stream()
                .map(entry -> new EnchantmentValue(
                        Objects.requireNonNull(ForgeRegistries.ENCHANTMENTS.getKey(entry.getKey())),
                        entry.getValue()))
                .sorted(Comparator.comparing(value -> value.id().toString()))
                .toList();
        BladeStyle style = StyleResolver.resolve(stack);
        CustomStyle customStyle = customStyle(stack, style);
        ForgedSlashArtSpec art = ForgedSlashArtSpec.fromBlade(stack);
        ForgedArt forgedArt = art == null ? null : new ForgedArt(
                art.coreVariant(), art.primary().id(), art.secondary().id());
        var traditions = new Traditions(BladeLegacyEasterEggs.isShoshinUnlocked(stack),
                BladeLegacyEasterEggs.isBairenUnlocked(stack), BladeLegacyEasterEggs.isBanshoUnlocked(stack),
                NbtSageEasterEgg.isUnlocked(stack));
        var inscriptions = SoulLegacyState.inscribed(stack).stream()
                .map(soul -> new Soul(soul.name(), SoulLegacyState.isUnlocked(stack, soul))).toList();
        return stack.getCapability(ItemSlashBlade.BLADESTATE).map(state -> new BladeExportSnapshot(
                stack.getHoverName().getString(), state.getBaseAttackModifier(), state.getMaxDamage(),
                state.getComboRoot(), state.getSlashArtsKey(),
                state.getSpecialEffects().stream().sorted().toList(), enchantments, attributes,
                state.getColorCode(), state.isEffectColorInverse(), state.getCarryType().name(),
                state.getAdjust(), state.isDefaultBewitched(), state.isSealed(), state.isBroken(),
                stack.getTag() != null && stack.getTag().getBoolean("Unbreakable"),
                state.getModel().orElseThrow(), state.getTexture().orElseThrow(),
                style.name(), customStyle, forgedArt, traditions, componentEffects(stack), inscriptions, NamedPartProvenance.capture(stack)
        )).orElseThrow(() -> new IllegalStateException("Missing SlashBlade state"));
    }

    private static CustomStyle customStyle(ItemStack stack, BladeStyle style) {
        if (style != BladeStyle.CUSTOM) return null;
        var tag = stack.getTag();
        CustomStyleSpec spec = tag == null ? null : CustomStyleSpec.decode(tag.getCompound(CustomStyleSpec.STACK_KEY));
        if (spec == null) throw new IllegalStateException("Custom blade has no valid recorded sword style");
        return new CustomStyle(spec.name(), spec.actions(), CustomBladeLength.from(stack).name());
    }

    private static ComponentEffects componentEffects(ItemStack stack) {
        boolean draw = ComponentEffectResolver.hasModule(stack,
                ModularSlashBladeItem.SAYA_SLOT, ModularSlashBladeItem.QUICKDRAW_SAYA_MODULE);
        boolean spirit = ComponentEffectResolver.hasModule(stack,
                ModularSlashBladeItem.SAYA_SLOT, ModularSlashBladeItem.SPIRIT_SAYA_MODULE);
        boolean heavy = ComponentEffectResolver.hasModule(stack,
                ModularSlashBladeItem.KASHIRA_SLOT, ModularSlashBladeItem.HEAVY_KASHIRA_MODULE);
        boolean rank = ComponentEffectResolver.hasModule(stack,
                ModularSlashBladeItem.TSUKA_SLOT, ModularSlashBladeItem.STABLE_TSUKA_MODULE);
        boolean guard = ComponentEffectResolver.hasModule(stack,
                ModularSlashBladeItem.TSUBA_SLOT, ModularSlashBladeItem.GUARD_TSUBA_MODULE);
        boolean precisionHabaki = ComponentEffectResolver.hasModule(stack,
                ModularSlashBladeItem.HABAKI_SLOT, ModularSlashBladeItem.PRECISION_HABAKI_MODULE);
        boolean reinforcedHabaki = ComponentEffectResolver.hasModule(stack,
                ModularSlashBladeItem.HABAKI_SLOT, ModularSlashBladeItem.REINFORCED_HABAKI_MODULE);
        boolean unsupported = dev.bladetetra.challenge.BoundaryForging.isForged(stack);
        return new ComponentEffects(draw ? ComponentCombatValues.DRAW_BONUS : 0,
                spirit ? ComponentCombatValues.SPIRIT_BONUS : 0, spirit ? ComponentCombatValues.SPIRIT_TICKS : 0,
                heavy, rank ? ComponentCombatValues.RANK_UNIT_DIVISOR : 0,
                guard ? ComponentCombatValues.GUARD_REDUCTION : 0,
                guard ? ComponentCombatValues.GUARD_DURABILITY_COST : 0, precisionHabaki, reinforcedHabaki, unsupported);
    }

    public record ComponentEffects(double drawBonus, double spiritBonus, int spiritTicks, boolean heavyPommel,
                                   int rankDivisor, float guardReduction, int guardDurability,
                                   boolean precisionHabaki, boolean reinforcedHabaki, boolean unsupported) {}

    public record CustomStyle(String name, List<ResourceLocation> actions, String length) {
        public CustomStyle {
            actions = List.copyOf(actions);
        }
    }

    public record ForgedArt(String core, String primary, String secondary) {}
    public record Traditions(boolean shoshin, boolean bairen, boolean bansho, boolean nbtSage) {}
    public record Soul(String name, boolean unlocked) {}
    public record EnchantmentValue(ResourceLocation id, int level) {}
    public record AttributeValue(ResourceLocation id, String modifierId, double amount, String operation) {}
}

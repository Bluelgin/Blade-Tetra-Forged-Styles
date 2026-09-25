package dev.bladetetra.item;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.LegacyFusionHandler;
import dev.bladetetra.combat.ComponentEffectResolver;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.combat.PotatoBladeHandler;
import dev.bladetetra.combat.StyleInputBuffer;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.compat.TetraDurabilityCompat;
import dev.bladetetra.registry.ModEnchantments;
import dev.bladetetra.easteregg.BladeLegacyEasterEggs;
import dev.bladetetra.easteregg.NbtSageEasterEgg;
import dev.bladetetra.easteregg.SenbonzakuraAwakening;
import dev.bladetetra.challenge.BoundaryForging;
import dev.bladetetra.forging.FoxLegacyParts;
import dev.bladetetra.forging.LegacyCalibration;
import dev.bladetetra.forging.ImprintAffinity;
import dev.bladetetra.forging.NamedLegacyParts;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;
import se.mickelus.tetra.ConfigHandler;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.gui.GuiModuleOffsets;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.module.SchematicRegistry;
import se.mickelus.tetra.module.data.EffectData;
import se.mickelus.tetra.module.data.ItemProperties;
import se.mickelus.tetra.module.data.SynergyData;
import se.mickelus.tetra.module.schematic.RepairSchematic;
import se.mickelus.tetra.properties.AttributeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A SlashBlade item whose persistent construction is provided by Tetra modules.
 *
 * <p>Tetra owns the module setup and derived maximum durability. SlashBlade owns
 * combo state, ownership, kill count, break state, and current damage. The vanilla
 * durability methods below are a view over the same integer damage value used by
 * SlashBlade: Resharped, so Tetra never creates a second durability source.</p>
 */
public class ModularSlashBladeItem extends ItemSlashBlade implements IModularItem {
    public static final String BLADE_SLOT = "slashblade/blade";
    public static final String TSUKA_SLOT = "slashblade/tsuka";
    public static final String TSUBA_SLOT = "slashblade/tsuba";
    public static final String SAYA_SLOT = "slashblade/saya";
    public static final String HABAKI_SLOT = "slashblade/habaki";
    public static final String KASHIRA_SLOT = "slashblade/kashira";
    public static final String FULLER_SLOT = "slashblade/fuller";
    public static final String INSCRIPTION_SLOT = "slashblade/inscription";

    public static final String BLADE_MODULE = "slashblade/katana_blade";
    public static final String ORTHODOX_BLADE_MODULE = "slashblade/orthodox_blade";
    public static final String WAKIZASHI_MODULE = "slashblade/wakizashi_blade";
    public static final String NODACHI_MODULE = "slashblade/nodachi_blade";
    public static final String TSUKA_MODULE = "slashblade/wrapped_tsuka";
    public static final String SWIFT_TSUKA_MODULE = "slashblade/swift_tsuka";
    public static final String STABLE_TSUKA_MODULE = "slashblade/stable_tsuka";
    public static final String TSUBA_MODULE = "slashblade/simple_tsuba";
    public static final String LIGHT_TSUBA_MODULE = "slashblade/light_tsuba";
    public static final String GUARD_TSUBA_MODULE = "slashblade/guard_tsuba";
    public static final String TSUBALESS_MODULE = "slashblade/tsubaless";
    public static final String SAYA_MODULE = "slashblade/basic_saya";
    public static final String QUICKDRAW_SAYA_MODULE = "slashblade/quickdraw_saya";
    public static final String SPIRIT_SAYA_MODULE = "slashblade/spirit_saya";
    public static final String HABAKI_MODULE = "slashblade/basic_habaki";
    public static final String PRECISION_HABAKI_MODULE = "slashblade/precision_habaki";
    public static final String REINFORCED_HABAKI_MODULE = "slashblade/reinforced_habaki";
    public static final String KASHIRA_MODULE = "slashblade/simple_kashira";
    public static final String LIGHT_KASHIRA_MODULE = "slashblade/light_kashira";
    public static final String HEAVY_KASHIRA_MODULE = "slashblade/heavy_kashira";
    public static final String FULLER_MODULE = "slashblade/reinforced_fuller";
    public static final String LIGHTWEIGHT_FULLER_MODULE = "slashblade/lightweight_fuller";
    public static final String SOUL_INSCRIPTION_MODULE = "slashblade/soul_inscription";
    public static final String AWAKENED_SOUL_INSCRIPTION_MODULE =
            "slashblade/awakened_soul_inscription";
    public static final String FOX_SAYA_MODULE = "slashblade/fox_saya";
    public static final String FOX_TSUBA_MODULE = "slashblade/fox_tsuba";

    private static final String[] MAJOR_MODULES = { BLADE_SLOT, TSUKA_SLOT };
    private static final String[] MINOR_MODULES = {
            SAYA_SLOT,
            TSUBA_SLOT,
            HABAKI_SLOT,
            KASHIRA_SLOT,
            FULLER_SLOT,
            INSCRIPTION_SLOT
    };
    private static final String[] REQUIRED_MODULES = { BLADE_SLOT, TSUKA_SLOT, SAYA_SLOT };
    private static final GuiModuleOffsets MAJOR_GUI_OFFSETS =
            new GuiModuleOffsets(22, -2, 22, 18);
    private static final GuiModuleOffsets MINOR_GUI_OFFSETS =
            new GuiModuleOffsets(
                    -21, -25,
                    -21, -12,
                    -21, 1,
                    -21, 14,
                    -21, 27,
                    -21, 40);
    private static final SynergyData[] NO_SYNERGIES = new SynergyData[0];
    private static final int FALLBACK_DURABILITY = 250;
    private static final double IAIDO_REACH_AMPLIFIER = 1.0D;
    private static final double DEFAULT_REACH_AMPLIFIER = 2.5D;
    private static final double DANGAKU_REACH_AMPLIFIER = 4.0D;
    private static final java.util.UUID IMPRINT_AFFINITY_DAMAGE_UUID =
            java.util.UUID.fromString("1e16e1a0-68f1-4f84-a028-2da67d7055d8");

    private final Cache<String, Multimap<Attribute, AttributeModifier>> attributeCache =
            CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(5, TimeUnit.MINUTES).build();
    private final Cache<String, EffectData> effectCache =
            CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(5, TimeUnit.MINUTES).build();
    private final Cache<String, ItemProperties> propertyCache =
            CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(5, TimeUnit.MINUTES).build();

    public ModularSlashBladeItem() {
        super(Tiers.IRON, 1, -2.4F, new Item.Properties().stacksTo(1).fireResistant());
        DataManager.instance.moduleData.onReload(this::clearCaches);
        SchematicRegistry.instance.registerSchematic(
                new RepairSchematic(this, "modular_slashblade"));
    }

    public ItemStack createDefaultStack() {
        ItemStack stack = new ItemStack(this);
        BladeModuleMigration.installModule(stack, BLADE_SLOT, BLADE_MODULE, "katana_blade/iron");
        BladeModuleMigration.installModule(stack, TSUKA_SLOT, TSUKA_MODULE, "wrapped_tsuka/stick");
        BladeModuleMigration.installModule(stack, TSUBA_SLOT, TSUBA_MODULE, "simple_tsuba/iron");
        BladeModuleMigration.installModule(stack, SAYA_SLOT, SAYA_MODULE, "basic_saya/oak");
        BladeModuleMigration.installModule(stack, HABAKI_SLOT, HABAKI_MODULE, "basic_habaki/iron");
        BladeModuleMigration.installModule(stack, KASHIRA_SLOT, KASHIRA_MODULE, "simple_kashira/iron");
        BladeModuleMigration.markCurrent(stack);
        IModularItem.updateIdentifier(stack);
        syncDerivedBladeState(stack);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        return BladeNameResolver.resolve(stack);
    }

    @Override
    public Item getItem() {
        return this;
    }

    @Override
    public void clearCaches() {
        attributeCache.invalidateAll();
        effectCache.invalidateAll();
        propertyCache.invalidateAll();
    }

    @Override
    public String[] getMajorModuleKeys(ItemStack itemStack) {
        return MAJOR_MODULES;
    }

    @Override
    public String[] getMinorModuleKeys(ItemStack itemStack) {
        return MINOR_MODULES;
    }

    @Override
    public String[] getRequiredModules(ItemStack itemStack) {
        return REQUIRED_MODULES;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public GuiModuleOffsets getMajorGuiOffsets(ItemStack itemStack) {
        return MAJOR_GUI_OFFSETS;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public GuiModuleOffsets getMinorGuiOffsets(ItemStack itemStack) {
        return MINOR_GUI_OFFSETS;
    }

    @Override
    public int getHoneBase(ItemStack itemStack) {
        return ConfigHandler.honeSwordBase.get();
    }

    @Override
    public int getHoneIntegrityMultiplier(ItemStack itemStack) {
        return ConfigHandler.honeSwordIntegrityMultiplier.get();
    }

    @Override
    public boolean canGainHoneProgress(ItemStack itemStack) {
        return true;
    }

    @Override
    public Cache<String, Multimap<Attribute, AttributeModifier>> getAttributeModifierCache() {
        return attributeCache;
    }

    @Override
    public Cache<String, EffectData> getEffectDataCache() {
        return effectCache;
    }

    @Override
    public Cache<String, ItemProperties> getPropertyCache() {
        return propertyCache;
    }

    @Override
    public SynergyData[] getAllSynergyData(ItemStack itemStack) {
        return NO_SYNERGIES;
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        migrateLegacyModules(stack);
        int modularDurability = getModuleMaxDamage(stack);
        var completeSet = NamedLegacyParts.fromStack(stack).completeSet();
        if (completeSet == null) {
            return modularDurability;
        }
        ImprintAffinity affinity = ImprintAffinity.calculate(
                getModuleAttackDamage(stack), modularDurability,
                completeSet.baseAttack(), completeSet.maxDamage());
        return modularDurability + affinity.durabilityBonus();
    }

    private int getModuleMaxDamage(ItemStack stack) {
        ItemProperties properties = getPropertiesCached(stack);
        int calculated = Math.round(properties.durability * properties.durabilityMultiplier);
        return calculated > 1 ? calculated : FALLBACK_DURABILITY;
    }

    private double getModuleAttackDamage(ItemStack stack) {
        return AttributeHelper.getMergedAmount(
                getAttributeModifiersCached(stack).get(Attributes.ATTACK_DAMAGE));
    }

    public ImprintAffinity getImprintAffinity(ItemStack stack) {
        var completeSet = NamedLegacyParts.fromStack(stack).completeSet();
        if (completeSet == null) {
            return null;
        }
        return ImprintAffinity.calculate(
                getModuleAttackDamage(stack), getModuleMaxDamage(stack),
                completeSet.baseAttack(), completeSet.maxDamage());
    }

    @Override
    public int getDamage(ItemStack stack) {
        int finalDamage = getMaxDamage(stack) - 1;
        return stack.getCapability(BLADESTATE)
                .map(state -> Mth.clamp(state.getDamage(), 0, finalDamage))
                .orElse(0);
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {
        int finalDamage = getMaxDamage(stack) - 1;
        int clampedDamage = Mth.clamp(damage, 0, finalDamage);
        super.setDamage(stack, clampedDamage);
    }

    @Override
    public boolean isDamaged(ItemStack stack) {
        return getDamage(stack) > 0;
    }

    @Override
    public <T extends LivingEntity> int damageItem(
            ItemStack stack,
            int amount,
            T entity,
            Consumer<T> onBroken) {
        int reducedAmount = TetraDurabilityCompat.preprocessDamage(
                this, stack, amount, entity, onBroken);
        if (reducedAmount <= 0) {
            return 0;
        }
        if (ComponentEffectResolver.hasModule(
                stack, HABAKI_SLOT, REINFORCED_HABAKI_MODULE)
                && entity.getRandom().nextFloat() < 0.30F) {
            return 0;
        }

        int currentDamage = getDamage(stack);
        int maxDamage = getMaxDamage(stack);
        int requestedDamage =
                currentDamage + reducedAmount >= maxDamage - 1
                        ? Math.max(1, maxDamage - currentDamage)
                        : reducedAmount;
        int appliedDamage =
                super.damageItem(stack, requestedDamage, entity, onBroken);
        if (appliedDamage > 0 && !entity.level().isClientSide()) {
            tickProgression(entity, stack, appliedDamage);
        }
        return appliedDamage;
    }

    @Override
    public boolean hurtEnemy(
            ItemStack stack,
            LivingEntity target,
            LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (stack.getCapability(BLADESTATE).map(state -> !state.isBroken()).orElse(false)) {
            applyUsageEffects(attacker, stack, 1);
        }
        return result;
    }

    @Override
    public boolean mineBlock(
            ItemStack stack,
            Level level,
            BlockState state,
            BlockPos pos,
            LivingEntity entity) {
        boolean result = super.mineBlock(stack, level, state, pos, entity);
        boolean bladeIsUsable =
                stack.getCapability(BLADESTATE).map(blade -> !blade.isBroken()).orElse(false);
        if (state.getDestroySpeed(level, pos) > 0 && bladeIsUsable) {
            applyUsageEffects(entity, stack, 1);
        }
        return result;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        // SlashBlade renders its own material-backed diamond durability gauge
        // behind the inventory icon. Hiding the vanilla bar avoids displaying
        // the same durability value twice.
        return false;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F - getDamage(stack) * 13.0F / (getMaxDamage(stack) - 1.0F));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float remaining = 1.0F - getDamage(stack) / (getMaxDamage(stack) - 1.0F);
        return Mth.hsvToRgb(Math.max(0, remaining) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            EquipmentSlot slot,
            ItemStack stack) {
        syncDerivedBladeState(stack);
        Multimap<Attribute, AttributeModifier> result =
                ArrayListMultimap.create(super.getAttributeModifiers(slot, stack));

        if (slot != EquipmentSlot.MAINHAND) {
            return result;
        }

        result.entries().removeIf(entry -> {
            AttributeModifier modifier = entry.getValue();
            return modifier.getId().equals(Item.BASE_ATTACK_SPEED_UUID);
        });

        Multimap<Attribute, AttributeModifier> tetraModifiers = getAttributeModifiersCached(stack);
        tetraModifiers.forEach((attribute, modifier) -> {
            boolean isBaseAttackDamage = attribute == Attributes.ATTACK_DAMAGE
                    && modifier.getId().equals(Item.BASE_ATTACK_DAMAGE_UUID);
            if (!isBaseAttackDamage) {
                result.put(attribute, modifier);
            }
        });

        boolean isBroken = stack.getCapability(BLADESTATE)
                .map(state -> state.isBroken())
                .orElse(false);
        if (!isBroken) {
            double reachAmplifier = switch (StyleResolver.resolve(stack)) {
                case IAIDO -> IAIDO_REACH_AMPLIFIER;
                case DANGAKU -> DANGAKU_REACH_AMPLIFIER;
                default -> DEFAULT_REACH_AMPLIFIER;
            };
            result.entries().removeIf(entry ->
                    entry.getKey() == ForgeMod.ENTITY_REACH.get()
                            && entry.getValue().getId().equals(PLAYER_REACH_AMPLIFIER));
            result.put(
                    ForgeMod.ENTITY_REACH.get(),
                    new AttributeModifier(
                            PLAYER_REACH_AMPLIFIER,
                            "Blade Tetra style reach",
                            reachAmplifier,
                            AttributeModifier.Operation.ADDITION));
            ImprintAffinity affinity = getImprintAffinity(stack);
            if (affinity != null && affinity.attackBonus() > 0.0D) {
                result.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                        IMPRINT_AFFINITY_DAMAGE_UUID,
                        "Blade Tetra imprint affinity",
                        affinity.attackBonus(),
                        AttributeModifier.Operation.ADDITION));
            }
        }
        return result;
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int slot,
            boolean selected) {
        syncDerivedBladeState(stack);
        if (!level.isClientSide && entity instanceof Player player) {
            LegacyCalibration.migrateStack(stack);
        }
        BladeLegacyEasterEggs.trackInventoryState(stack, level, entity);
        NbtSageEasterEgg.trackInventoryState(stack, level, entity);
        SenbonzakuraAwakening.trackInventoryState(
                stack, level, entity, selected);
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public void verifyTagAfterLoad(CompoundTag tag) {
        super.verifyTagAfterLoad(tag);
        LegacyCalibration.migrateStackTag(tag);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, net.minecraft.world.entity.player.Player player) {
        IModularItem.updateIdentifier(stack);
        LegacyCalibration.migrateStack(stack);
        syncDerivedBladeState(stack);
        super.onCraftedBy(stack, level, player);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand) {
        StyleInputBuffer.queueIfLocked(player.getItemInHand(hand), player);
        return super.use(level, player, hand);
    }

    @Override
    public boolean onLeftClickEntity(
            ItemStack stack,
            Player player,
            Entity target) {
        StyleInputBuffer.queueIfLocked(stack, player);
        return super.onLeftClickEntity(stack, player, target);
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        if (ComboState.getElapsed(entity) > 0L) {
            StyleInputBuffer.queueIfLocked(stack, entity);
        }
        return super.onEntitySwing(stack, entity);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        migrateLegacyModules(stack);
        super.appendHoverText(stack, level, tooltip, flag);
        PotatoBladeHandler.appendTooltip(stack, level, tooltip, flag);
        BoundaryForging.appendTooltip(stack, tooltip);
        boolean expanded = TooltipKeyState.isAltDown();
        if (expanded) {
            BladeDetailTooltip.append(stack, tooltip);
        }
        tooltip.add(Component.translatable(expanded
                        ? "tooltip.blade_tetra.details.collapse"
                        : "tooltip.blade_tetra.details.expand")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.addAll(getTooltip(stack, level, flag));
    }

    @Override
    public void assemble(ItemStack stack, @Nullable Level level, float severity) {
        IModularItem.super.assemble(stack, level, severity);
        syncDerivedBladeState(stack);
    }

    public void syncDerivedBladeState(ItemStack stack) {
        migrateLegacyModules(stack);
        float moduleDamage = (float) AttributeHelper.getMergedAmount(
                getAttributeModifiersCached(stack).get(Attributes.ATTACK_DAMAGE));
        ModularBladeStateSync.sync(stack, moduleDamage, getMaxDamage(stack));
    }

    /**
     * Upgrades Alpha 1-3 stacks and imported Tetra swords to the dedicated
     * SlashBlade module schema. This only rewrites Tetra construction data;
     * SlashBlade's blade state is left untouched.
     */
    public boolean migrateLegacyModules(ItemStack stack) {
        return BladeModuleMigration.migrate(stack, this::clearCaches);
    }

}

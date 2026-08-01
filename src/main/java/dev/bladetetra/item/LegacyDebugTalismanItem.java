package dev.bladetetra.item;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.easteregg.AkatsukiAwakening;
import dev.bladetetra.easteregg.BladeLegacyEasterEggs;
import dev.bladetetra.easteregg.KyoukaAwakening;
import dev.bladetetra.easteregg.NbtSageEasterEgg;
import dev.bladetetra.easteregg.SenbonzakuraAwakening;
import dev.bladetetra.forging.ForgingImprovements;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.visual.SayaPresetSkin;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLEnvironment;
import se.mickelus.tetra.items.modular.IModularItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dev-only helper for exercising every hidden blade progression. */
public final class LegacyDebugTalismanItem extends Item {
    private static final String MODE_TAG = "BladeTetraLegacyDebugMode";
    private static final String TARGET_TAG = "BladeTetraLegacyDebugTarget";

    public LegacyDebugTalismanItem() {
        super(new Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
            InteractionHand hand) {
        ItemStack talisman = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(talisman);
        if (!checkAllowed(player)) return InteractionResultHolder.fail(talisman);
        if (player.isShiftKeyDown()) {
            applyMode(player, target(talisman), mode(talisman));
        } else {
            DebugMode next = nextMode(mode(talisman), target(talisman));
            talisman.getOrCreateTag().putInt(MODE_TAG, next.ordinal());
            player.displayClientMessage(Component.translatable(
                    "message.blade_tetra.legacy_debug.mode", next.displayName())
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResultHolder.consume(talisman);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!checkAllowed(player)) return InteractionResult.FAIL;
        ItemStack talisman = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            applyMode(player, target(talisman), mode(talisman));
        } else {
            DebugTarget next = nextTarget(target(talisman));
            talisman.getOrCreateTag().putInt(TARGET_TAG, next.ordinal());
            talisman.getOrCreateTag().putInt(
                    MODE_TAG, firstMode(next).ordinal());
            player.displayClientMessage(Component.translatable(
                    "message.blade_tetra.legacy_debug.target", next.displayName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.blade_tetra.legacy_debug.warning")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.blade_tetra.legacy_debug.target",
                target(stack).displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.blade_tetra.legacy_debug.mode",
                mode(stack).displayName()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.blade_tetra.legacy_debug.use")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static boolean checkAllowed(Player player) {
        if ("Dev".equals(player.getGameProfile().getName())
                && (!FMLEnvironment.production
                || GameplayConfig.ENABLE_DEVELOPER_TOOLS.get())) return true;
        player.displayClientMessage(Component.translatable(
                "message.blade_tetra.legacy_debug.denied")
                .withStyle(ChatFormatting.RED), false);
        return false;
    }

    private static void applyMode(Player player, DebugTarget target, DebugMode mode) {
        if (target == DebugTarget.FORGING) {
            applyForgingMode(player, mode);
            return;
        }
        if (mode == DebugMode.MATERIALS) {
            if (giveMaterials(player, target)) success(player, target, mode);
            else invalid(player, target, mode);
            return;
        }
        if (mode == DebugMode.CANDIDATE) {
            give(player, createCandidateBlade(target));
            success(player, target, mode);
            return;
        }
        ItemStack blade = player.getOffhandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem modularBlade)) {
            player.displayClientMessage(Component.translatable(
                    "message.blade_tetra.legacy_debug.need_blade")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        if (mode == DebugMode.READY && target == DebugTarget.NBT_SAGE
                && player instanceof ServerPlayer serverPlayer) {
            awardAdvancement(serverPlayer, DebugTarget.SHOSHIN);
            awardAdvancement(serverPlayer, DebugTarget.BAIREN);
            awardAdvancement(serverPlayer, DebugTarget.BANSHO);
        }
        boolean applied = switch (mode) {
            case READY -> prepare(blade, target, player.level().getGameTime());
            case AWAKENED -> unlock(blade, target);
            case DORMANT -> makeDormant(blade, modularBlade, target);
            case BROKEN -> setBroken(blade, true);
            case RESTORED -> restore(blade, modularBlade, target);
            case RESET -> reset(blade, target);
            default -> false;
        };
        if (!applied) {
            invalid(player, target, mode);
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (mode == DebugMode.AWAKENED) awardAdvancement(serverPlayer, target);
            if (mode == DebugMode.RESET) revokeAdvancement(serverPlayer, target);
        }
        success(player, target, mode);
    }

    private static void applyForgingMode(Player player, DebugMode mode) {
        if (mode == DebugMode.MATERIALS) {
            giveForgingMaterials(player);
            success(player, DebugTarget.FORGING, mode);
            return;
        }
        if (mode == DebugMode.CANDIDATE) {
            give(player, createCandidateBlade(DebugTarget.FORGING));
            success(player, DebugTarget.FORGING, mode);
            return;
        }

        ItemStack blade = player.getOffhandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)) {
            player.displayClientMessage(Component.translatable(
                    "message.blade_tetra.legacy_debug.need_blade")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        boolean applied = switch (mode) {
            case KOBUSE -> applyForging(
                    blade, ModularSlashBladeItem.BLADE_SLOT,
                    ForgingImprovements.KOBUSE);
            case SANMAI -> applyForging(
                    blade, ModularSlashBladeItem.BLADE_SLOT,
                    ForgingImprovements.SANMAI);
            case SHIHOZUME -> applyForging(
                    blade, ModularSlashBladeItem.BLADE_SLOT,
                    ForgingImprovements.SHIHOZUME);
            case HAMAGURI -> applyForging(
                    blade, ModularSlashBladeItem.BLADE_SLOT,
                    ForgingImprovements.HAMAGURI);
            case HIRA -> applyForging(
                    blade, ModularSlashBladeItem.BLADE_SLOT,
                    ForgingImprovements.HIRA);
            case USUBA -> applyForging(
                    blade, ModularSlashBladeItem.BLADE_SLOT,
                    ForgingImprovements.USUBA);
            case NAKAGO_FIT -> applyForging(
                    blade, ModularSlashBladeItem.TSUKA_SLOT,
                    ForgingImprovements.NAKAGO_FIT);
            case RIGID_ASSEMBLY -> applyForging(
                    blade, ModularSlashBladeItem.TSUKA_SLOT,
                    ForgingImprovements.RIGID_ASSEMBLY);
            case NORMALIZE -> normalizeForging(blade);
            default -> false;
        };
        if (applied) {
            success(player, DebugTarget.FORGING, mode);
        } else {
            invalid(player, DebugTarget.FORGING, mode);
        }
    }

    private static boolean applyForging(
            ItemStack blade, String slot, String improvement) {
        return ForgingImprovements.apply(blade, slot, improvement);
    }

    private static boolean normalizeForging(ItemStack blade) {
        return applyForging(
                blade, ModularSlashBladeItem.BLADE_SLOT,
                ForgingImprovements.NORMALIZED_CONSTRUCTION)
                && applyForging(
                blade, ModularSlashBladeItem.BLADE_SLOT,
                ForgingImprovements.NORMALIZED_EDGE)
                && applyForging(
                blade, ModularSlashBladeItem.TSUKA_SLOT,
                ForgingImprovements.NORMALIZED_ASSEMBLY);
    }

    private static void giveForgingMaterials(Player player) {
        give(player, new ItemStack(Items.IRON_INGOT, 32));
        give(player, new ItemStack(Items.CHARCOAL, 8));
        give(player, new ItemStack(Items.QUARTZ, 8));
        give(player, new ItemStack(Items.FLINT, 8));
        give(player, new ItemStack(Items.AMETHYST_SHARD, 8));
        give(player, new ItemStack(Items.IRON_NUGGET, 16));
        give(player, new ItemStack(Items.STRING, 8));
    }

    private static boolean prepare(ItemStack blade, DebugTarget target, long now) {
        return switch (target) {
            case SHOSHIN -> BladeLegacyEasterEggs.prepareShoshinForDebug(blade);
            case BAIREN -> BladeLegacyEasterEggs.prepareBairenForDebug(blade);
            case BANSHO -> BladeLegacyEasterEggs.prepareBanshoForDebug(blade);
            case RAIKIRI -> BladeLegacyEasterEggs.prepareRaikiriForDebug(blade, now);
            case AKATSUKI -> AkatsukiAwakening.prepareForDebug(blade);
            case KYOUKA -> KyoukaAwakening.prepareForDebug(blade, now);
            case SENBONZAKURA -> SenbonzakuraAwakening.prepareForDebug(blade, now);
            case NBT_SAGE -> NbtSageEasterEgg.prepareForDebug(blade);
            case FORGING -> false;
        };
    }

    private static boolean unlock(ItemStack blade, DebugTarget target) {
        return switch (target) {
            case SHOSHIN, BAIREN, BANSHO, RAIKIRI ->
                    BladeLegacyEasterEggs.unlockForDebug(blade, target.id);
            case AKATSUKI -> AkatsukiAwakening.unlockForDebug(blade);
            case KYOUKA -> KyoukaAwakening.unlockForDebug(blade);
            case SENBONZAKURA -> SenbonzakuraAwakening.unlockForDebug(blade);
            case NBT_SAGE -> NbtSageEasterEgg.unlockForDebug(blade);
            case FORGING -> false;
        };
    }

    private static boolean reset(ItemStack blade, DebugTarget target) {
        return switch (target) {
            case SHOSHIN, BAIREN, BANSHO, RAIKIRI ->
                    BladeLegacyEasterEggs.resetForDebug(blade, target.id);
            case AKATSUKI -> AkatsukiAwakening.resetForDebug(blade);
            case KYOUKA -> KyoukaAwakening.resetForDebug(blade);
            case SENBONZAKURA -> {
                SenbonzakuraAwakening.resetForDebug(blade);
                yield true;
            }
            case NBT_SAGE -> NbtSageEasterEgg.resetForDebug(blade);
            case FORGING -> false;
        };
    }

    private static ItemStack createCandidateBlade(DebugTarget target) {
        ModularSlashBladeItem item = ModItems.MODULAR_SLASHBLADE.get();
        ItemStack blade = item.createDefaultStack();
        switch (target) {
            case SHOSHIN -> BladeLegacyEasterEggs.markIronKatanaOrigin(blade);
            case RAIKIRI -> {
                install(blade, ModularSlashBladeItem.TSUBA_SLOT,
                        ModularSlashBladeItem.TSUBA_MODULE, "simple_tsuba/copper");
                SayaPresetSkin.apply(blade, SayaPresetSkin.PURPLE_LIGHTNING);
            }
            case AKATSUKI -> {
                installAwakenedInscription(blade,
                        "awakened_soul_inscription/blood_crystal");
                SayaPresetSkin.apply(blade, SayaPresetSkin.AKATSUKI);
            }
            case KYOUKA -> {
                install(blade, ModularSlashBladeItem.BLADE_SLOT,
                        ModularSlashBladeItem.BLADE_MODULE, "katana_blade/amethyst");
                installAwakenedInscription(blade,
                        "awakened_soul_inscription/proudsoul_sphere");
                SayaPresetSkin.apply(blade, SayaPresetSkin.KYOUKA);
            }
            case SENBONZAKURA -> {
                install(blade, ModularSlashBladeItem.BLADE_SLOT,
                        ModularSlashBladeItem.WAKIZASHI_MODULE, "wakizashi_blade/iron");
                install(blade, ModularSlashBladeItem.TSUKA_SLOT,
                        ModularSlashBladeItem.TSUKA_MODULE, "wrapped_tsuka/cherry");
                install(blade, ModularSlashBladeItem.SAYA_SLOT,
                        ModularSlashBladeItem.SAYA_MODULE, "basic_saya/cherry");
                installAwakenedInscription(blade, SenbonzakuraAwakening.SAKURA_VARIANT);
                SayaPresetSkin.apply(blade, SayaPresetSkin.SAKURA);
            }
            case NBT_SAGE -> {
                install(blade, ModularSlashBladeItem.FULLER_SLOT,
                        ModularSlashBladeItem.FULLER_MODULE, "reinforced_fuller/iron");
                install(blade, ModularSlashBladeItem.INSCRIPTION_SLOT,
                        ModularSlashBladeItem.SOUL_INSCRIPTION_MODULE,
                        "soul_inscription/proudsoul_ingot");
                SayaPresetSkin.apply(blade, SayaPresetSkin.SEIGAIHA);
            }
            case FORGING -> {
                install(blade, ModularSlashBladeItem.TSUKA_SLOT,
                        ModularSlashBladeItem.STABLE_TSUKA_MODULE,
                        "stable_tsuka/iron");
                install(blade, ModularSlashBladeItem.TSUBA_SLOT,
                        ModularSlashBladeItem.GUARD_TSUBA_MODULE,
                        "guard_tsuba/iron");
                install(blade, ModularSlashBladeItem.HABAKI_SLOT,
                        ModularSlashBladeItem.REINFORCED_HABAKI_MODULE,
                        "reinforced_habaki/iron");
            }
            default -> { }
        }
        IModularItem.updateIdentifier(blade);
        item.syncDerivedBladeState(blade);
        return blade;
    }

    private static boolean giveMaterials(Player player, DebugTarget target) {
        return switch (target) {
            case RAIKIRI -> {
                give(player, new ItemStack(ModItems.SAYA_PATTERN_PURPLE_LIGHTNING.get()));
                yield true;
            }
            case AKATSUKI -> {
                give(player, new ItemStack(ModItems.SAYA_PATTERN_AKATSUKI.get()));
                yield true;
            }
            case KYOUKA -> {
                give(player, new ItemStack(ModItems.SAYA_PATTERN_KYOUKA.get()));
                yield true;
            }
            case SENBONZAKURA -> {
                give(player, new ItemStack(ModItems.SAKURA_SOUL_CRYSTAL.get(), 4));
                give(player, new ItemStack(ModItems.SAYA_PATTERN_SAKURA.get()));
                yield true;
            }
            case FORGING -> {
                giveForgingMaterials(player);
                yield true;
            }
            default -> false;
        };
    }

    private static boolean makeDormant(ItemStack blade, ModularSlashBladeItem item,
            DebugTarget target) {
        if (target != DebugTarget.AKATSUKI && target != DebugTarget.KYOUKA
                && target != DebugTarget.SENBONZAKURA) return false;
        install(blade, ModularSlashBladeItem.INSCRIPTION_SLOT,
                ModularSlashBladeItem.SOUL_INSCRIPTION_MODULE,
                "soul_inscription/proudsoul_ingot");
        IModularItem.updateIdentifier(blade);
        item.syncDerivedBladeState(blade);
        return true;
    }

    private static boolean restore(ItemStack blade, ModularSlashBladeItem item,
            DebugTarget target) {
        if (!setBroken(blade, false)) return false;
        switch (target) {
            case AKATSUKI -> {
                installAwakenedInscription(blade,
                        "awakened_soul_inscription/blood_crystal");
                SayaPresetSkin.apply(blade, SayaPresetSkin.AKATSUKI);
            }
            case KYOUKA -> {
                install(blade, ModularSlashBladeItem.BLADE_SLOT,
                        ModularSlashBladeItem.BLADE_MODULE, "katana_blade/amethyst");
                installAwakenedInscription(blade,
                        "awakened_soul_inscription/proudsoul_sphere");
                SayaPresetSkin.apply(blade, SayaPresetSkin.KYOUKA);
            }
            case SENBONZAKURA -> {
                install(blade, ModularSlashBladeItem.BLADE_SLOT,
                        ModularSlashBladeItem.WAKIZASHI_MODULE, "wakizashi_blade/iron");
                installAwakenedInscription(blade, SenbonzakuraAwakening.SAKURA_VARIANT);
                SayaPresetSkin.apply(blade, SayaPresetSkin.SAKURA);
            }
            default -> { }
        }
        IModularItem.updateIdentifier(blade);
        item.syncDerivedBladeState(blade);
        return true;
    }

    private static boolean setBroken(ItemStack blade, boolean broken) {
        return blade.getCapability(ModularSlashBladeItem.BLADESTATE).map(state -> {
            state.setBroken(broken);
            if (!broken && state.getDamage() >= state.getMaxDamage()) {
                state.setDamage(Math.max(0, state.getMaxDamage() - 1));
            }
            return true;
        }).orElse(false);
    }

    private static void installAwakenedInscription(ItemStack blade, String variant) {
        install(blade, ModularSlashBladeItem.INSCRIPTION_SLOT,
                ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE, variant);
    }

    private static void install(ItemStack blade, String slot, String module,
            String variant) {
        IModularItem.putModuleInSlot(blade, slot, module, module + "_material", variant);
    }

    private static void awardAdvancement(ServerPlayer player, DebugTarget target) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(target.advancement());
        if (advancement == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    private static void revokeAdvancement(ServerPlayer player, DebugTarget target) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(target.advancement());
        if (advancement == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        List<String> completed = new ArrayList<>();
        progress.getCompletedCriteria().forEach(completed::add);
        for (String criterion : completed) {
            player.getAdvancements().revoke(advancement, criterion);
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void success(Player player, DebugTarget target, DebugMode mode) {
        player.displayClientMessage(Component.translatable(
                "message.blade_tetra.legacy_debug.applied",
                target.displayName(), mode.displayName()).withStyle(ChatFormatting.GREEN), false);
    }

    private static void invalid(Player player, DebugTarget target, DebugMode mode) {
        player.displayClientMessage(Component.translatable(
                "message.blade_tetra.legacy_debug.invalid",
                target.displayName(), mode.displayName()).withStyle(ChatFormatting.RED), false);
    }

    private static DebugMode mode(ItemStack stack) {
        int stored = stack.getOrCreateTag().getInt(MODE_TAG);
        DebugMode[] values = DebugMode.values();
        DebugMode mode = stored >= 0 && stored < values.length
                ? values[stored]
                : DebugMode.MATERIALS;
        return allowedModes(target(stack)).contains(mode)
                ? mode
                : firstMode(target(stack));
    }

    private static DebugTarget target(ItemStack stack) {
        int stored = stack.getOrCreateTag().getInt(TARGET_TAG);
        DebugTarget[] values = DebugTarget.values();
        return stored >= 0 && stored < values.length ? values[stored] : DebugTarget.SHOSHIN;
    }

    private static DebugMode nextMode(DebugMode mode, DebugTarget target) {
        List<DebugMode> values = allowedModes(target);
        int index = values.indexOf(mode);
        return values.get((index + 1) % values.size());
    }

    private static DebugMode firstMode(DebugTarget target) {
        return allowedModes(target).get(0);
    }

    private static List<DebugMode> allowedModes(DebugTarget target) {
        if (target == DebugTarget.FORGING) {
            return List.of(
                    DebugMode.MATERIALS,
                    DebugMode.CANDIDATE,
                    DebugMode.KOBUSE,
                    DebugMode.SANMAI,
                    DebugMode.SHIHOZUME,
                    DebugMode.HAMAGURI,
                    DebugMode.HIRA,
                    DebugMode.USUBA,
                    DebugMode.NAKAGO_FIT,
                    DebugMode.RIGID_ASSEMBLY,
                    DebugMode.NORMALIZE);
        }
        return List.of(
                DebugMode.MATERIALS,
                DebugMode.CANDIDATE,
                DebugMode.READY,
                DebugMode.AWAKENED,
                DebugMode.DORMANT,
                DebugMode.BROKEN,
                DebugMode.RESTORED,
                DebugMode.RESET);
    }

    private static DebugTarget nextTarget(DebugTarget target) {
        DebugTarget[] values = DebugTarget.values();
        return values[(target.ordinal() + 1) % values.length];
    }

    private enum DebugTarget {
        SHOSHIN("shoshin"), BAIREN("bairen"), BANSHO("bansho"),
        RAIKIRI("raikiri"), AKATSUKI("akatsuki"), KYOUKA("kyouka"),
        SENBONZAKURA("senbonzakura"), NBT_SAGE("nbt_sage"),
        FORGING("forging");

        private final String id;

        DebugTarget(String id) { this.id = id; }

        private Component displayName() {
            return Component.translatable("debug_target.blade_tetra." + id);
        }

        private ResourceLocation advancement() {
            return ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID, id);
        }
    }

    private enum DebugMode {
        MATERIALS, CANDIDATE, READY, AWAKENED, DORMANT, BROKEN, RESTORED, RESET,
        KOBUSE, SANMAI, SHIHOZUME, HAMAGURI, HIRA, USUBA,
        NAKAGO_FIT, RIGID_ASSEMBLY, NORMALIZE;

        private Component displayName() {
            return Component.translatable("debug_mode.blade_tetra."
                    + name().toLowerCase(Locale.ROOT));
        }
    }
}

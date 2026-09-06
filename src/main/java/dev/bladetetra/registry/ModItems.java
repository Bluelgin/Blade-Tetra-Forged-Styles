package dev.bladetetra.registry;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.item.LegacyDebugTalismanItem;
import dev.bladetetra.item.LegacyImprintScrollItem;
import dev.bladetetra.item.SayaPatternItem;
import dev.bladetetra.item.SmithingClueItem;
import dev.bladetetra.item.SmithingJournalItem;
import dev.bladetetra.challenge.BrokenOniMaskItem;
import dev.bladetetra.challenge.BoundaryGateCharmItem;
import dev.bladetetra.challenge.SwordGhostRemnantItem;
import dev.bladetetra.forging.ForgingScrolls;
import dev.bladetetra.lore.SmithingLore;
import dev.bladetetra.visual.SayaPresetSkin;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BladeTetra.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BladeTetra.MOD_ID);

    public static final RegistryObject<ModularSlashBladeItem> MODULAR_SLASHBLADE =
            ITEMS.register("modular_slashblade", ModularSlashBladeItem::new);
    public static final RegistryObject<Item> BLOOD_CRYSTAL =
            ITEMS.register("blood_crystal", () -> new Item(
                    new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SAKURA_SOUL_CRYSTAL =
            ITEMS.register("sakura_soul_crystal", () -> new Item(
                    new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<BoundaryGateCharmItem> BOUNDARY_GATE_CHARM =
            ITEMS.register("boundary_gate_charm", BoundaryGateCharmItem::new);
    public static final RegistryObject<SwordGhostRemnantItem> SWORD_GHOST_REMNANT =
            ITEMS.register("sword_ghost_remnant", SwordGhostRemnantItem::new);
    public static final RegistryObject<BrokenOniMaskItem> BROKEN_ONI_MASK =
            ITEMS.register("broken_oni_mask", BrokenOniMaskItem::new);
    public static final RegistryObject<LegacyDebugTalismanItem>
            LEGACY_DEBUG_TALISMAN = ITEMS.register(
                    "legacy_debug_talisman",
                    LegacyDebugTalismanItem::new);
    public static final RegistryObject<SmithingClueItem> SMITHING_CLUE =
            ITEMS.register("smithing_clue", SmithingClueItem::new);
    public static final RegistryObject<SmithingJournalItem> SMITHING_JOURNAL =
            ITEMS.register("smithing_journal", SmithingJournalItem::new);
    public static final RegistryObject<LegacyImprintScrollItem> LEGACY_IMPRINT_SCROLL =
            ITEMS.register("legacy_imprint_scroll", LegacyImprintScrollItem::new);
    public static final RegistryObject<SayaPatternItem> SAYA_PATTERN_BLACK_GOLD =
            registerSayaPattern("saya_pattern_black_gold", SayaPresetSkin.BLACK_GOLD);
    public static final RegistryObject<SayaPatternItem> SAYA_PATTERN_VERMILION_CLOUD =
            registerSayaPattern(
                    "saya_pattern_vermilion_cloud",
                    SayaPresetSkin.VERMILION_CLOUD);
    public static final RegistryObject<SayaPatternItem> SAYA_PATTERN_SEIGAIHA =
            registerSayaPattern("saya_pattern_seigaiha", SayaPresetSkin.SEIGAIHA);
    public static final RegistryObject<SayaPatternItem> SAYA_PATTERN_SAKURA =
            registerSayaPattern("saya_pattern_sakura", SayaPresetSkin.SAKURA);
    public static final RegistryObject<SayaPatternItem> SAYA_PATTERN_PURPLE_LIGHTNING =
            registerSayaPattern(
                    "saya_pattern_purple_lightning",
                    SayaPresetSkin.PURPLE_LIGHTNING);
    public static final RegistryObject<SayaPatternItem> SAYA_PATTERN_AKATSUKI =
            registerSayaPattern("saya_pattern_akatsuki", SayaPresetSkin.AKATSUKI);
    public static final RegistryObject<SayaPatternItem> SAYA_PATTERN_KYOUKA =
            registerSayaPattern("saya_pattern_kyouka", SayaPresetSkin.KYOUKA);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.blade_tetra"))
                    .icon(() -> MODULAR_SLASHBLADE.get().createDefaultStack())
                    .displayItems((parameters, output) -> {
                        output.accept(MODULAR_SLASHBLADE.get().createDefaultStack());
                        output.accept(BLOOD_CRYSTAL.get());
                        output.accept(SAKURA_SOUL_CRYSTAL.get());
                        output.accept(BOUNDARY_GATE_CHARM.get());
                        output.accept(SWORD_GHOST_REMNANT.get());
                        output.accept(BROKEN_ONI_MASK.get());
                        output.accept(SMITHING_JOURNAL.get().getDefaultInstance());
                        output.accept(LEGACY_IMPRINT_SCROLL.get());
                        output.accept(ForgingScrolls.create(ForgingScrolls.Kind.EDGE));
                        output.accept(ForgingScrolls.create(ForgingScrolls.Kind.CONSTRUCTION));
                        output.accept(ForgingScrolls.create(ForgingScrolls.Kind.ASSEMBLY));
                        output.accept(ForgingScrolls.create(ForgingScrolls.Kind.WIND_CUT));
                        output.accept(ForgingScrolls.create(ForgingScrolls.Kind.FLYING_SWALLOW));
                        output.accept(ForgingScrolls.create(ForgingScrolls.Kind.FULL_MOON));
                        output.accept(ForgingScrolls.create(ForgingScrolls.Kind.ZANSHIN));
                        output.accept(ForgingScrolls.create(ForgingScrolls.Kind.BLACK_FOX));
                        output.accept(ForgingScrolls.create(ForgingScrolls.Kind.WHITE_FOX));
                        SmithingLore.CLUES.forEach(clue ->
                                output.accept(SmithingClueItem.create(clue)));
                        output.accept(SAYA_PATTERN_BLACK_GOLD.get());
                        output.accept(SAYA_PATTERN_VERMILION_CLOUD.get());
                        output.accept(SAYA_PATTERN_SEIGAIHA.get());
                        output.accept(SAYA_PATTERN_SAKURA.get());
                        output.accept(SAYA_PATTERN_PURPLE_LIGHTNING.get());
                        output.accept(SAYA_PATTERN_AKATSUKI.get());
                        output.accept(SAYA_PATTERN_KYOUKA.get());
                    })
                    .build());

    private static RegistryObject<SayaPatternItem> registerSayaPattern(
            String name,
            SayaPresetSkin preset) {
        return ITEMS.register(name, () -> new SayaPatternItem(preset));
    }

    private ModItems() {
    }
}

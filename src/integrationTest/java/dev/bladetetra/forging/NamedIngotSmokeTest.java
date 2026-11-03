package dev.bladetetra.forging;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.LegacyFusionHandler;
import dev.bladetetra.compat.LegacyImprintCraftingOutcome;
import dev.bladetetra.item.NamedBladeIngotItem;
import dev.bladetetra.recipe.NamedBladeIngotRecipe;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.module.schematic.ConfigSchematic;

import java.util.List;
import java.util.Map;

/** Opt-in, single-world Tetra integration smoke: does not run in player worlds. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class NamedIngotSmokeTest {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.namedIngotSmoke") && !Boolean.getBoolean("blade_tetra.legacySmoke")) return;
        try {
            var player = FakePlayerFactory.getMinecraft(event.getServer().overworld());
            verifyExtraction(player);
            verifyAbilitySplit();
            ManualFusionSmoke.verify(player);
            String[][] modules = {
                {"blade", "katana_blade", "katana_blade"},
                {"blade", "nodachi_blade", "nodachi_blade"},
                {"blade", "wakizashi_blade", "wakizashi_blade"},
                {"blade", "orthodox_blade", "orthodox_blade"},
                {"blade", "custom_standard_blade", "custom_style_standard"},
                {"blade", "custom_long_blade", "custom_style_long"},
                {"blade", "custom_short_blade", "custom_style_short"},
                {"saya", "basic_saya", "basic_saya"},
                {"saya", "quickdraw_saya", "quickdraw_saya"},
                {"saya", "spirit_saya", "spirit_saya"},
                {"tsuba", "simple_tsuba", "simple_tsuba"},
                {"tsuba", "light_tsuba", "light_tsuba"},
                {"tsuba", "guard_tsuba", "guard_tsuba"}
            };
            verifyCatalogPreviews(player, modules);
            int count = 0;
            for (LegacyImprintKind source : NamedLegacyCatalog.values()) {
                for (String[] spec : modules) {
                    ItemStack stack = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
                    String slot = "slashblade/" + spec[0];
                    var definition = DataManager.instance.schematicData.getData(
                            ResourceLocation.fromNamespaceAndPath("tetra", "slashblade/" + spec[2]));
                    if (definition == null) throw new IllegalStateException("Missing ordinary schematic " + spec[2]);
                    var schematic = new ConfigSchematic(definition);
                    ItemStack ingot = NamedBladeIngotItem.create(source);
                    ingot.setCount(64);
                    ItemStack[] materials = {ingot};
                    check(schematic.isMaterialsValid(stack, slot, materials), "Material rejected: " + spec[2]);
                    check(schematic.getRequiredToolLevels(stack, materials).equals(
                            Map.of(net.minecraftforge.common.ToolAction.get("hammer_dig"), 3)), "Hammer level: " + spec[2]);
                    ItemStack preview = schematic.applyUpgrade(stack, materials, false, slot, player);
                    check(source.equals(NamedLegacyImprintStorage.snapshot(preview, spec[0])), "Preview lacks snapshot");
                    var module = se.mickelus.tetra.module.ItemUpgradeRegistry.instance.getModule("slashblade/" + spec[1]);
                    double attack = se.mickelus.tetra.properties.AttributeHelper.getMergedAmount(
                            module.getVariantData(preview).attributes.get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
                    check(Math.abs(attack - NamedMaterialStats.of(source.baseAttack()).contribution(spec[0])) < .011,
                            "Attack split: " + spec[1] + " " + source.id() + " got " + attack);
                    int required = schematic.getRequiredQuantity(stack, 0, ingot);
                    ingot.setCount(required);
                    ItemStack crafted = schematic.applyUpgrade(stack, materials, true, slot, player);
                    check(ingot.isEmpty(), "Actual craft did not consume material");
                    check(source.equals(NamedLegacyImprintStorage.snapshot(crafted, spec[0])), "Consumed last ingot lost snapshot");
                    check(module.getVariantData(preview).durability == module.getVariantData(crafted).durability,
                            "Preview/craft durability differs");
                    check(!stack.getOrCreateTag().contains(NamedLegacyImprintStorage.ROOT), "Preview changed original stack");
                    count++;
                }
            }
            verifyMigrationAndMixedSources(player);
            LogUtils.getLogger().info("NAMED_INGOT_SMOKE_PASS: {} native-material preview/craft pairs; level 3 hammer; exact part attack; migration and mixed sources", count);
        } catch (Exception e) {
            LogUtils.getLogger().error("NAMED_INGOT_SMOKE_FAIL", e);
        } finally {
            event.getServer().halt(false);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void verifyAbilitySplit() {
        var bladeEffect = ModSlashBladeAbilities.TWIN_FOX_REFLECTION.getId();
        var fittingsEffect = ModSlashBladeAbilities.SNAKE_MOLT.getId();
        var fittings = withEffect(LegacyImprintKind.BLACK_FOX, fittingsEffect);
        var edge = withEffect(LegacyImprintKind.WHITE_FOX, bladeEffect);
        ItemStack stack = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        var tag = stack.getOrCreateTag();
        for (String part : List.of("saya", "tsuba", "blade")) {
            var source = part.equals("blade") ? edge : fittings;
            String module = tag.getString("slashblade/" + part);
            tag.putString(module + "_material", NamedLegacyImprintStorage.genericVariant(part));
            NamedLegacyImprintStorage.putSource(tag, part, source.id());
            check(NamedLegacyImprintStorage.putSnapshot(stack, part, source),
                    "Missing inheritance fixture: " + part);
        }
        var state = stack.getCapability(ItemSlashBlade.BLADESTATE).resolve().orElseThrow();
        // Model a saved stack from the old matching-fittings SE rule.
        state.addSpecialEffect(fittingsEffect);
        tag.putString("blade_tetra_named_owned_effects", fittingsEffect.toString());
        LegacyFusionHandler.sync(stack, state);
        check(fittings.slashArt().equals(state.getSlashArtsKey()), "Matching fittings lost SA");
        check(state.hasSpecialEffect(bladeEffect), "Blade SE was not inherited");
        check(!state.hasSpecialEffect(fittingsEffect), "Old fittings SE remained applied");

        NamedLegacyImprintStorage.clearSource(tag, "tsuba");
        LegacyFusionHandler.sync(stack, state);
        check(!fittings.slashArt().equals(state.getSlashArtsKey()), "Unmatched fittings retained SA");
        check(state.hasSpecialEffect(bladeEffect), "Changing fittings removed independent blade SE");

        state.addSpecialEffect(fittingsEffect);
        NamedLegacyImprintStorage.clearSource(tag, "blade");
        LegacyFusionHandler.sync(stack, state);
        check(!state.hasSpecialEffect(bladeEffect), "Replacing the blade retained its old SE");
        check(state.hasSpecialEffect(fittingsEffect), "Replacing the blade removed an external SE");
    }

    private static LegacyImprintKind withEffect(LegacyImprintKind source, ResourceLocation effect) {
        return new LegacyImprintKind(source.id(), source.name(), source.model(), source.texture(),
                source.material(), source.defaultProfile(), source.baseAttack(), source.maxDamage(),
                source.slashArt(), List.of(effect));
    }

    private static void verifyCatalogPreviews(Player player, String[][] modules) throws Exception {
        var material = DataManager.instance.materialData.getData(
                ResourceLocation.fromNamespaceAndPath("tetra", "named_blade/ingot"));
        check(material.primary == 0 && material.secondary == 0 && material.tertiary == 0
                && material.durability == 0 && material.magicCapacity == 0
                && material.integrityGain == 0 && material.integrityCost == 0
                && material.toolLevel == 0 && material.toolEfficiency == 0, "Catalog material is not neutral");
        var source = NamedLegacyCatalog.values().stream().findFirst().orElseThrow();
        for (String[] spec : modules) {
            String slot = "slashblade/" + spec[0];
            var definition = DataManager.instance.schematicData.getData(
                    ResourceLocation.fromNamespaceAndPath("tetra", "slashblade/" + spec[2]));
            var schematic = new ConfigSchematic(definition);
            ItemStack ingot = NamedBladeIngotItem.create(source); ingot.setCount(64);
            ItemStack actual = schematic.applyUpgrade(ModItems.MODULAR_SLASHBLADE.get().createDefaultStack(),
                    new ItemStack[]{ingot}, false, slot, player);
            // getPreviews also formats client-only localized names. Exercise its
            // exact copy + applyOutcome path here without loading I18n on a server.
            var outcome = java.util.Arrays.stream(definition.outcomes)
                    .filter(p -> NamedBladeMaterial.namedVariant(p.moduleVariant)).findFirst().orElseThrow();
            ItemStack catalog = actual.copy();
            var applyOutcome = ConfigSchematic.class.getDeclaredMethod("applyOutcome",
                    se.mickelus.tetra.module.schematic.OutcomeDefinition.class, ItemStack.class,
                    boolean.class, String.class, Player.class);
            applyOutcome.setAccessible(true);
            applyOutcome.invoke(schematic, outcome, catalog, false, slot, null);
            check(NamedLegacyImprintStorage.sourceId(catalog, spec[0]) == null,
                    "Catalog inherited actual blade source: " + spec[1]);
            var module = se.mickelus.tetra.module.ItemUpgradeRegistry.instance.getModule("slashblade/" + spec[1]);
            for (var data : new se.mickelus.tetra.module.data.VariantData[]{
                    module.getVariantData(outcome.moduleVariant), module.getVariantData(catalog)}) {
                check(data.attributes.isEmpty() && data.durability == 0 && data.magicCapacity == 0
                        && data.integrity == 0 && data.integrityUsage == 0
                        && data.tools.getValues().isEmpty() && data.effects.getValues().isEmpty()
                        && data.aspects.getValues().isEmpty(), "Nonzero catalog module stats: " + spec[1]);
                check(data.durabilityMultiplier == 1 && data.integrityMultiplier == 1,
                        "Neutral preview erased unrelated module multipliers");
            }
            check(outcome.requiredTools.getLevelMap().equals(Map.of(net.minecraftforge.common.ToolAction.get("hammer_dig"), 3)),
                    "Catalog hammer requirement lost");
            var materials = outcome.material.getApplicableItemStacks();
            check(materials.length > 0 && materials[0].getCount() > 0, "Catalog ingredient quantity lost");
            check(source.equals(NamedLegacyImprintStorage.snapshot(actual, spec[0])), "Holo changed actual source");
            check(module.getVariantData(actual).durability > 0, "Actual material became a catalog placeholder");
        }
        LogUtils.getLogger().info("NAMED_CATALOG_ZERO_PASS: {} modules; neutral raw material, static variants and native Holo outcome path; no stale source; hammer and quantities retained", modules.length);
    }

    private static void verifyMigrationAndMixedSources(Player player) throws Exception {
        LegacyImprintKind source = NamedLegacyCatalog.values().stream().findFirst().orElseThrow();
        ItemStack old = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        var tag = old.getOrCreateTag();
        tag.putString("slashblade/blade", "slashblade/legacy_blade");
        tag.putString("slashblade/legacy_blade_material", "legacy_blade/imprinted");
        NamedLegacyImprintStorage.putSource(tag, "blade", source.id());
        NamedLegacyImprintStorage.putSnapshot(old, "blade", source);
        tag.putInt("slashblade/blade:sharpness", 3);
        tag.putString("othermod:payload", "preserve-me");
        check(NamedBladeMaterial.migrate(old), "Legacy blade not migrated");
        check(!NamedBladeMaterial.migrate(old), "Migration not idempotent");
        check(tag.getInt("slashblade/blade:sharpness") == 3 && tag.getString("othermod:payload").equals("preserve-me"),
                "Migration changed independent data");
        ItemStack loaded = ItemStack.of(old.save(new net.minecraft.nbt.CompoundTag()));
        check(source.equals(NamedLegacyImprintStorage.snapshot(loaded, "blade")), "Save/load lost snapshot");
        for (String part : new String[]{"saya", "tsuba"}) {
            String name = part.equals("saya") ? "basic_saya" : "simple_tsuba";
            var definition = DataManager.instance.schematicData.getData(
                    ResourceLocation.fromNamespaceAndPath("tetra", "slashblade/" + name));
            var schematic = new ConfigSchematic(definition);
            ItemStack ingot = NamedBladeIngotItem.create(source); ingot.setCount(64);
            loaded = schematic.applyUpgrade(loaded, new ItemStack[]{ingot}, false, "slashblade/" + part, player);
        }
        check(source.equals(NamedLegacyParts.fromStack(loaded).completeSet()), "Orthodox identity lost");
        check(ModItems.MODULAR_SLASHBLADE.get().getImprintAffinity(loaded) == null, "Double attack affinity bonus");
        var rawModule = DataManager.instance.moduleData.getData(
                ResourceLocation.fromNamespaceAndPath("tetra", "slashblade/katana_blade"));
        check(java.util.Arrays.stream(rawModule.variants).anyMatch(v ->
                v instanceof se.mickelus.tetra.module.data.MaterialVariantData material
                        && java.util.Arrays.stream(material.materials).anyMatch(id -> id.toString().equals("tetra:named_blade/"))),
                "Server registry erased extraction templates before client synchronization");
        var absent = new LegacyImprintKind("removed_provider/test_blade", source.name(), source.model(),
                source.texture(), source.material(), source.defaultProfile(), 24, 1200,
                source.slashArt(), source.specialEffects());
        NamedLegacyImprintStorage.putSource(loaded.getOrCreateTag(), "blade", absent.id());
        NamedLegacyImprintStorage.putSnapshot(loaded, "blade", absent);
        loaded = ItemStack.of(loaded.save(new net.minecraft.nbt.CompoundTag()));
        var bladeModule = se.mickelus.tetra.module.ItemUpgradeRegistry.instance.getModule("slashblade/katana_blade");
        double absentAttack = se.mickelus.tetra.properties.AttributeHelper.getMergedAmount(
                bladeModule.getVariantData(loaded).attributes.get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
        check(Math.abs(absentAttack - 12) < .001, "Missing provider changed persisted material attack");
        var replacement = new ConfigSchematic(DataManager.instance.schematicData.getData(
                ResourceLocation.fromNamespaceAndPath("tetra", "slashblade/basic_saya")));
        ItemStack other = NamedBladeIngotItem.create(LegacyImprintKind.WOODEN_PUPPET); other.setCount(64);
        loaded = replacement.applyUpgrade(loaded, new ItemStack[]{other}, false, "slashblade/saya", player);
        check(LegacyImprintKind.WOODEN_PUPPET.equals(NamedLegacyImprintStorage.snapshot(loaded, "saya")), "Mixed source lost");
        ItemStack blank = ModItems.NAMED_BLADE_INGOT.get().getDefaultInstance(); blank.setCount(64);
        check(!replacement.isMaterialsValid(loaded, "slashblade/saya", new ItemStack[]{blank}), "Blank catalog ingot accepted");
        ItemStack fox = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        fox.getOrCreateTag().putString("slashblade/saya", "slashblade/fox_saya");
        fox.getOrCreateTag().putString("slashblade/fox_saya_material", "fox_saya/black");
        check(NamedBladeMaterial.migrate(fox), "Historical fox saya not migrated");
        check(LegacyImprintKind.BLACK_FOX.equals(NamedLegacyImprintStorage.snapshot(fox, "saya")), "Fox source lost");
        check(DataManager.instance.schematicData.getData(ResourceLocation.fromNamespaceAndPath(
                "tetra", "slashblade/legacy_ingot_blade")) == null, "Retired recipe exposed");
    }

    private static void verifyExtraction(Player player) {
        AbstractContainerMenu dummyMenu = new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int slot) {
                return ItemStack.EMPTY;
            }
            @Override public boolean stillValid(Player player) { return true; }
            @Override public void slotsChanged(net.minecraft.world.Container container) {}
        };
        TransientCraftingContainer grid = new TransientCraftingContainer(dummyMenu, 3, 3);
        ItemStack wooden = new ItemStack(ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("slashblade", "slashblade_wood")));
        grid.setItem(4, wooden);
        for (int i = 0; i < 9; i++) if (i != 4) grid.setItem(i,
                new ItemStack(ForgeRegistries.ITEMS.getValue(
                        new ResourceLocation("slashblade", "proudsoul_tiny"))));
        NamedBladeIngotRecipe recipe = new NamedBladeIngotRecipe(
                new ResourceLocation(BladeTetra.MOD_ID, "named_blade_ingot"),
                CraftingBookCategory.EQUIPMENT);
        if (!recipe.matches(grid, player.level())) {
            throw new IllegalStateException("3x3 wooden puppet extraction unavailable");
        }
        ItemStack result = recipe.assemble(grid, player.level().registryAccess());
        if (!LegacyImprintKind.WOODEN_PUPPET.id().equals(
                NamedBladeIngotItem.sourceId(result))) {
            throw new IllegalStateException("Extraction produced wrong source");
        }
        int before = wooden.getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state.getDamage()).orElse(-1);
        ItemStack returned = recipe.getRemainingItems(grid).get(4);
        int after = returned.getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state.getDamage()).orElse(-1);
        if (returned.isEmpty() || before < 0 || after <= before) {
            throw new IllegalStateException("Source blade not safely returned with wear");
        }
        grid.setItem(0, new ItemStack(ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("slashblade", "proudsoul"))));
        if (recipe.matches(grid, player.level())) {
            throw new IllegalStateException("Wrong Proud Soul variant accepted");
        }
    }

    private NamedIngotSmokeTest() {}
}

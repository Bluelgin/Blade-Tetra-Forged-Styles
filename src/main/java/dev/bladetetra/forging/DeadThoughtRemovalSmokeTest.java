package dev.bladetetra.forging;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.LegacyFusionHandler;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import se.mickelus.tetra.module.SchematicRegistry;

/** Development-only isolated-world test of the real schematic and SlashBlade capability. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class DeadThoughtRemovalSmokeTest {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.deadThoughtRemovalSmoke")) return;
        try {
            var player = FakePlayerFactory.getMinecraft(event.getServer().overworld());
            player.getAbilities().instabuild = false;
            var schematic = SchematicRegistry.getSchematic(ResourceLocation.fromNamespaceAndPath("tetra", DeadThoughtRemovalSchematic.KEY));
            check(schematic != null, "registered schematic");
            var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            var slot = ModularSlashBladeItem.BLADE_SLOT;
            check(!schematic.isRelevant(blade), "hidden before injection");
            check(java.util.Arrays.stream(SchematicRegistry.getSchematics(blade, slot))
                    .noneMatch(entry -> entry.getKey().equals(schematic.getKey())), "native menu hides unbound blade");
            check(!schematic.isRelevant(new ItemStack(Items.STICK)), "foreign items excluded");
            var state = blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            var originalArt = SlashArtsRegistry.JUDGEMENT_CUT.getId();
            var unrelatedEffect = ModSlashBladeAbilities.TWIN_FOX_REFLECTION.getId();
            state.setSlashArtsKey(originalArt);
            state.addSpecialEffect(unrelatedEffect);
            blade.getOrCreateTag().putString("test_foreign_data", "keep");
            blade.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 2);
            DeadThoughtDivineLegacy.bind(blade);
            LegacyFusionHandler.sync(blade, state);
            check(state.getSlashArtsKey().equals(ModSlashBladeAbilities.BLOOD_CHERRY_FINAL_SCENE.getId()), "injected SA");
            check(state.getSpecialEffects().containsAll(LegacyFusion.NIHILUL_SAYA_CRIMSON_CHERRY_HILT.specialEffects()), "injected SE");
            check(schematic.isRelevant(blade), "visible after injection");
            check(java.util.Arrays.stream(SchematicRegistry.getSchematics(blade, slot))
                    .anyMatch(entry -> entry.getKey().equals(schematic.getKey())), "native menu exposes bound blade");
            check(!schematic.isApplicableForSlot(ModularSlashBladeItem.SAYA_SLOT, blade), "only blade operation slot");
            var material = new ItemStack(Items.AMETHYST_SHARD, 3);
            var materials = new ItemStack[]{material};
            check(!schematic.isMaterialsValid(blade, slot, new ItemStack[]{new ItemStack(Items.DIAMOND)}), "wrong material rejected");
            check(!schematic.isMaterialsValid(blade, slot, new ItemStack[]{ItemStack.EMPTY}), "empty material rejected");
            var beforePreview = blade.save(new net.minecraft.nbt.CompoundTag());
            var preview = schematic.applyUpgrade(blade, materials, false, slot, null);
            check(material.getCount() == 3, "preview consumes nothing");
            check(beforePreview.equals(blade.save(new net.minecraft.nbt.CompoundTag())), "preview leaves source/capability intact");
            check(!DeadThoughtDivineLegacy.isBound(preview), "preview reflects removal");
            var result = schematic.applyUpgrade(blade, materials, true, slot, player);
            check(material.getCount() == 2, "one shard consumed");
            check(DeadThoughtDivineLegacy.isBound(blade), "source copy retained");
            check(!schematic.isRelevant(result), "hidden after removal");
            var restored = result.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            check(restored.getSlashArtsKey().equals(originalArt), "original SA restored: expected=" + originalArt
                    + " actual=" + restored.getSlashArtsKey());
            check(restored.getSpecialEffects().contains(unrelatedEffect), "unrelated SE retained");
            for (var effect : LegacyFusion.NIHILUL_SAYA_CRIMSON_CHERRY_HILT.specialEffects()) {
                check(!restored.getSpecialEffects().contains(effect), "owned Dead Thought SE withdrawn");
            }
            check(result.getTag().getString("test_foreign_data").equals("keep"), "foreign data retained");
            check(result.getTag().get("Enchantments").equals(blade.getTag().get("Enchantments")), "enchantments retained");
            for (var key : blade.getTag().getAllKeys()) {
                if (key.startsWith("slashblade/") || key.endsWith("_material") || key.endsWith("_improvements")) {
                    check(java.util.Objects.equals(blade.getTag().get(key), result.getTag().get(key)), "module data retained: " + key);
                }
            }
            schematic.applyUpgrade(result, materials, true, slot, player);
            check(material.getCount() == 2, "stale/repeated removal consumes nothing");
            DeadThoughtDivineLegacy.bind(result);
            LegacyFusionHandler.sync(result, restored);
            check(schematic.isRelevant(result), "re-injection restores operation");
            player.getAbilities().instabuild = true;
            schematic.applyUpgrade(result, materials, true, slot, player);
            check(material.getCount() == 2, "creative removal consumes nothing");
            var fittingBlade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            installFitting(fittingBlade, "saya", "slashblade_addon/nihilul");
            installFitting(fittingBlade, "tsuba", "slashblade_addon/crimsoncherry");
            var fittingFusion = LegacyFusion.NIHILUL_SAYA_CRIMSON_CHERRY_HILT;
            check(LegacyFusion.installed(fittingBlade) == fittingFusion, "fitting convergence fixture");
            check(ForgingImprovements.apply(fittingBlade, slot, fittingFusion.improvement()), "fitting attunement");
            check(!schematic.isRelevant(fittingBlade), "fitting-only Dead Thought has no removal operation");
            DeadThoughtDivineLegacy.bind(fittingBlade);
            check(schematic.getDescription(fittingBlade).contains(net.minecraft.network.chat.Component.translatable(
                    "blade_tetra.schematic.remove_dead_thought.fitting_warning").getString()), "fitting warning shown");
            var fittingResult = schematic.applyUpgrade(fittingBlade, materials, false, slot, null);
            check(!DeadThoughtDivineLegacy.isBound(fittingResult), "injected marker removed above fittings");
            check(LegacyFusion.active(fittingResult) == fittingFusion, "fitting-granted Dead Thought survives removal");
            LogUtils.getLogger().info("DEAD_THOUGHT_REMOVAL_SMOKE_PASS: visibility, preview isolation, cost, restoration, preservation, re-injection");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("DEAD_THOUGHT_REMOVAL_SMOKE_FAIL", failure);
            throw new IllegalStateException("Dead Thought removal diagnostic failed", failure);
        } finally { event.getServer().halt(false); }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static void installFitting(ItemStack blade, String part, String source) {
        String module = "slashblade/legacy_" + part;
        var tag = blade.getOrCreateTag();
        tag.putString("slashblade/" + part, module);
        tag.putString(module + "_material", "legacy_" + part + "/imprinted");
        var root = tag.getCompound(NamedLegacyImprintStorage.ROOT);
        root.putString(part, source);
        tag.put(NamedLegacyImprintStorage.ROOT, root);
        var location = ResourceLocation.fromNamespaceAndPath("blade_tetra", "test_fitting");
        var kind = new LegacyImprintKind(source, location, location, location, "minecraft:iron_ingot",
                LegacyCalibrationProfile.DEFAULT, 5D, 70, null, java.util.List.of());
        check(NamedLegacyImprintStorage.putSnapshot(blade, part, kind), "persisted fitting fixture");
    }
    private DeadThoughtRemovalSmokeTest() {}
}

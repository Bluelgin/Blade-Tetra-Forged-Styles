package dev.bladetetra.forging;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.LegacyAbilityResolver;
import dev.bladetetra.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.module.schematic.ConfigSchematic;

/** Opt-in development smoke test; never runs in ordinary worlds. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class NamedLegacySmokeTest {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.legacySmoke")) return;
        int count = 0;
        int abilitySets = 0;
        int externalAbilitySets = 0;
        try {
            var player = FakePlayerFactory.getMinecraft(event.getServer().overworld());
            if (NamedLegacyCatalog.values().isEmpty()) {
                throw new IllegalStateException("Empty catalog");
            }
            for (var kind : NamedLegacyCatalog.values()) {
                boolean declaresAbilities = kind.slashArt() != null
                        || !kind.specialEffects().isEmpty();
                if (declaresAbilities != kind.supportsOrthodoxInheritance()) {
                    throw new IllegalStateException("Orthodox inheritance gate mismatch " + kind.id());
                }
                if (kind.slashArt() != null
                        && !LegacyAbilityResolver.isSlashArtRegistered(kind.slashArt())) {
                    throw new IllegalStateException("Unregistered Slash Art "
                            + kind.slashArt() + " for " + kind.id());
                }
                for (ResourceLocation effect : kind.specialEffects()) {
                    if (!LegacyAbilityResolver.isSpecialEffectRegistered(effect)) {
                        throw new IllegalStateException("Unregistered Special Effect "
                                + effect + " for " + kind.id());
                    }
                }
                if (declaresAbilities) {
                    abilitySets++;
                    if (!kind.name().getNamespace().equals("slashblade")) {
                        externalAbilitySets++;
                    }
                }

                ItemStack stack = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
                if (!ForgingImprovements.apply(stack, "slashblade/tsuka", kind.improvement())) {
                    throw new IllegalStateException("Old grip record no longer readable " + kind.id());
                }
                if (NamedLegacyParts.fromStack(stack).present()) {
                    throw new IllegalStateException("Retired standalone grip still renders " + kind.id());
                }
                var historical = new net.minecraft.nbt.CompoundTag();
                historical.put(kind.id(), LegacyCalibrationProfile.DEFAULT.write());
                historical.put("removed_addon:old_blade", LegacyCalibrationProfile.DEFAULT.write());
                stack.getOrCreateTag().put(LegacyCalibration.STACK_ROOT, historical);
                if (!LegacyCalibration.migrateStack(stack)
                        || stack.getOrCreateTag().contains(LegacyCalibration.STACK_ROOT)) {
                    throw new IllegalStateException("Historical calibration was not compacted");
                }

                String gripModule = stack.getOrCreateTag().getString("slashblade/tsuka");
                String gripMaterial = stack.getOrCreateTag().getString(gripModule + "_material");
                for (String part : java.util.List.of("saya", "tsuba")) {
                    var definition = DataManager.instance.schematicData.getData(
                            new ResourceLocation("tetra", kind.schematic(part)));
                    if (definition == null) {
                        throw new IllegalStateException("Missing schematic " + kind.schematic(part));
                    }
                    var schematic = new ConfigSchematic(definition);
                    var requirement = new dev.bladetetra.compat.LegacyPatternRequirement(
                            kind.id(), part);
                    var locked = new se.mickelus.tetra.module.schematic.CraftingContext(
                            player.level(), player.blockPosition(),
                            player.level().getBlockState(player.blockPosition()),
                            player, stack, "slashblade/" + part, new ResourceLocation[0]);
                    var unlocked = new se.mickelus.tetra.module.schematic.CraftingContext(
                            player.level(), player.blockPosition(),
                            player.level().getBlockState(player.blockPosition()),
                            player, stack, "slashblade/" + part,
                            new ResourceLocation[]{new ResourceLocation("tetra", kind.schematic(part))});
                    if (requirement.test(locked) || !requirement.test(unlocked)) {
                        throw new IllegalStateException("Invalid scroll gating " + kind.id() + "/" + part);
                    }
                    ItemStack[] materials = {
                            new ItemStack(ForgeRegistries.ITEMS.getValue(
                                    new ResourceLocation("slashblade:proudsoul_ingot"))),
                            new ItemStack(ForgeRegistries.ITEMS.getValue(
                                    new ResourceLocation(kind.material())))
                    };
                    if (!schematic.isMaterialsValid(stack, "slashblade/" + part, materials)) {
                        throw new IllegalStateException("Invalid materials " + kind.id() + "/" + part);
                    }
                    if (schematic.isMaterialsValid(stack, "slashblade/" + part,
                            new ItemStack[]{materials[0], ItemStack.EMPTY})) {
                        throw new IllegalStateException("Missing feature material accepted " + kind.id());
                    }
                    stack = schematic.applyUpgrade(stack, materials, false,
                            "slashblade/" + part, player);
                    // Direct ConfigSchematic smoke calls do not execute WorkbenchTile's
                    // crafting-effect phase, so mirror the V2 identity outcome explicitly.
                    NamedLegacyImprintStorage.applyCraftResult(stack,
                            "slashblade/" + part, kind.schematic(part));
                    String module = stack.getOrCreateTag().getString("slashblade/" + part);
                    String variant = stack.getOrCreateTag().getString(module + "_material");
                    if (!NamedLegacyImprintStorage.genericVariant(part).equals(variant)) {
                        throw new IllegalStateException("Per-blade variant leaked into V2 "
                                + kind.id() + "/" + part + ": " + variant);
                    }
                    if (!kind.id().equals(NamedLegacyImprintStorage.sourceId(stack, part))) {
                        throw new IllegalStateException("Missing V2 source identity "
                                + kind.id() + "/" + part);
                    }
                    if (!part.equals("tsuba")
                            && NamedLegacyParts.fromStack(stack).completeSet() != null) {
                        throw new IllegalStateException("Partial set grants resonance " + kind.id());
                    }
                    count++;
                }
                if (!kind.equals(NamedLegacyParts.fromStack(stack).completeSet())) {
                    throw new IllegalStateException("Incomplete crafted set "
                            + kind.id() + ": " + stack.getTag());
                }
                if (!gripModule.equals(stack.getOrCreateTag().getString("slashblade/tsuka"))
                        || !gripMaterial.equals(stack.getOrCreateTag().getString(gripModule + "_material"))) {
                    throw new IllegalStateException("Hilt replaced core grip material " + kind.id());
                }
                if (!ForgingImprovements.has(stack, "slashblade/tsuka", kind.improvement())) {
                    throw new IllegalStateException("Historical grip record was deleted " + kind.id());
                }
                if (new dev.bladetetra.compat.LegacyPatternRequirement(
                        kind.id(), "tsuka").test(null)) {
                    throw new IllegalStateException("Standalone grip crafting still exposed");
                }
            }
            LogUtils.getLogger().info(
                    "LEGACY_SMOKE_PASS: {} real Tetra upgrades across {} named blades; "
                            + "{} orthodox ability sets ({} external); discovery diagnostics {}",
                    count, NamedLegacyCatalog.values().size(), abilitySets, externalAbilitySets,
                    NamedLegacyCatalog.diagnosticCounts());
        } catch (Exception exception) {
            LogUtils.getLogger().error("LEGACY_SMOKE_FAIL after {} upgrades", count, exception);
        } finally {
            event.getServer().halt(false);
        }
    }

    private NamedLegacySmokeTest() {}
}

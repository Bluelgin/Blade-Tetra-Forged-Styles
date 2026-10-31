package dev.bladetetra.forging;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.compat.LegacyImprintCraftingOutcome;
import dev.bladetetra.item.NamedBladeIngotItem;
import dev.bladetetra.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.module.schematic.ConfigSchematic;

import java.util.Map;

/** Opt-in, single-world Tetra integration smoke: does not run in player worlds. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class NamedIngotSmokeTest {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.namedIngotSmoke")) return;
        try {
            var player = FakePlayerFactory.getMinecraft(event.getServer().overworld());
            LegacyImprintKind source = NamedLegacyCatalog.values().stream()
                    .filter(kind -> kind.visualUsable() || kind == LegacyImprintKind.WOODEN_PUPPET)
                    .findFirst().orElseThrow();
            ItemStack ingot = NamedBladeIngotItem.create(source);
            ItemStack stack = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            String oldGrip = stack.getOrCreateTag().getString("slashblade/tsuka");
            for (String part : new String[]{"blade", "saya", "tsuba"}) {
                String slot = "slashblade/" + part;
                String path = "slashblade/legacy_ingot_" + part;
                var definition = DataManager.instance.schematicData.getData(
                        new ResourceLocation("tetra", path));
                if (definition == null) throw new IllegalStateException("Missing " + path);
                var schematic = new ConfigSchematic(definition);
                ItemStack[] materials = {ingot.copy()};
                if (!schematic.isMaterialsValid(stack, slot, materials)) {
                    throw new IllegalStateException("Ingot rejected at " + slot);
                }
                stack = schematic.applyUpgrade(stack, materials, false, slot, player);
                new LegacyImprintCraftingOutcome().apply(
                        new ResourceLocation[0], stack, slot, true, player,
                        materials, Map.of(), player.level(), schematic,
                        player.blockPosition(), player.level().getBlockState(player.blockPosition()),
                        true, new ItemStack[]{ItemStack.EMPTY});
                if (!source.id().equals(NamedLegacyImprintStorage.sourceId(stack, part))) {
                    throw new IllegalStateException("Wrong ingot source at " + slot);
                }
            }
            if (!source.equals(NamedLegacyParts.fromStack(stack).completeSet())) {
                throw new IllegalStateException("Completed saya + composite hilt lost its source");
            }
            if (!oldGrip.equals(stack.getOrCreateTag().getString("slashblade/tsuka"))) {
                throw new IllegalStateException("Ingot changed original Tetra grip");
            }
            LogUtils.getLogger().info("NAMED_INGOT_SMOKE_PASS: three Tetra upgrades with {}", source.id());
        } catch (Exception e) {
            LogUtils.getLogger().error("NAMED_INGOT_SMOKE_FAIL", e);
        } finally {
            event.getServer().halt(false);
        }
    }

    private NamedIngotSmokeTest() {}
}

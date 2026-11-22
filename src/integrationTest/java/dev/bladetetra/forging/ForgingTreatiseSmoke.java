package dev.bladetetra.forging;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.effect.ItemEffect;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.module.schematic.ConfigSchematic;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class ForgingTreatiseSmoke {
    private static final String BLADE = ModularSlashBladeItem.BLADE_SLOT;

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.forgingTreatiseSmoke")) return;
        try {
            verify(FakePlayerFactory.getMinecraft(event.getServer().overworld()));
            LogUtils.getLogger().info("FORGING_TREATISE_SMOKE_PASS");
        } catch (Exception e) {
            LogUtils.getLogger().error("FORGING_TREATISE_SMOKE_FAIL", e);
        } finally {
            event.getServer().halt(false);
        }
    }

    private static void verify(Player player) throws Exception {
        var item = ModItems.MODULAR_SLASHBLADE.get();
        ItemStack blade = item.createDefaultStack();
        double damage = item.getAttributeValue(blade, Attributes.ATTACK_DAMAGE);
        check(damage > 0, "Starter blade has no damage");
        int originalIntegrity = IModularItem.getIntegrityCost(blade);
        int originalGain = IModularItem.getIntegrityGain(blade);
        String[] structures = {"kobuse", "sanmai", "shihozume"};
        int[] quantities = {3, 5, 8};
        int[] effectLevels = {8, 11, 15};
        double[] bonuses = {0, .12, .08};
        for (int i = 0; i < structures.length; i++) {
            var schematic = schematic("forging/" + structures[i]);
            var gold = new ItemStack(Items.GOLD_INGOT, quantities[i]);
            ItemStack[] materials = {gold};
            check(schematic.getRequiredQuantity(blade, 0, gold) == quantities[i], "Wrong metal cost");
            check(schematic.isMaterialsValid(blade, BLADE, materials), "Exact metal count rejected");
            ItemStack preview = schematic.applyUpgrade(blade, materials, false, BLADE, player);
            check(gold.getCount() == quantities[i], "Preview consumed metal");
            ItemStack finished = schematic.applyUpgrade(blade, materials, true, BLADE, player);
            check(gold.isEmpty(), "Crafting did not consume the exact metal count");
            check(item.getEffectLevel(preview, ItemEffect.stabilizing) == effectLevels[i], "Preview effect differs for " + structures[i] + ": " + item.getEffectLevel(preview, ItemEffect.stabilizing));
            check(item.getEffectLevel(finished, ItemEffect.stabilizing) == effectLevels[i], "Result lost the core effect");
            check(IModularItem.getIntegrityCost(finished) == originalIntegrity, "Composite forging consumes integrity");
            close(item.getAttributeValue(preview, Attributes.ATTACK_DAMAGE), damage * (1 + bonuses[i]),
                    "Preview scaled damage");
            close(item.getAttributeValue(finished, Attributes.ATTACK_DAMAGE), damage * (1 + bonuses[i]),
                    "Actual scaled damage");
            var loaded = ItemStack.of(finished.save(new CompoundTag()));
            check(item.getEffectLevel(loaded, ItemEffect.stabilizing) == effectLevels[i], "Reload lost core effect");
            var annealed = schematic("forging/normalize_construction").applyUpgrade(loaded,
                    new ItemStack[]{new ItemStack(Items.CHARCOAL)}, true, BLADE, player);
            check(item.getEffectLevel(annealed, ItemEffect.stabilizing) == 0, "Annealing kept the core effect");
        }

        var crystal = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("slashblade:proudsoul_crystal"));
        var rigid = schematic("finishing/rigid_assembly").applyUpgrade(blade,
                new ItemStack[]{new ItemStack(crystal)}, true, ModularSlashBladeItem.TSUKA_SLOT, player);
        int originalAvailable = originalGain - originalIntegrity;
        int rigidAvailable = IModularItem.getIntegrityGain(rigid) - IModularItem.getIntegrityCost(rigid);
        check(rigidAvailable == originalAvailable + 1, "Rigid assembly did not grant one integrity");

        var oldBlade = blade.copy();
        check(ForgingImprovements.apply(oldBlade, BLADE, ForgingImprovements.SANMAI), "Old structure fixture failed");
        check(item.getEffectLevel(oldBlade, ItemEffect.stabilizing) == 0, "An old blade invented a core material");
        check(IModularItem.getIntegrityCost(oldBlade) == originalIntegrity, "Old blade retained integrity cost");
        var sphere = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("slashblade:proudsoul_sphere"));
        var thinEdge = schematic("finishing/usuba").applyUpgrade(oldBlade,
                new ItemStack[]{new ItemStack(sphere)}, true, BLADE, player);
        item.syncDerivedBladeState(thinEdge);
        close(item.getAttributeValue(thinEdge, Attributes.ATTACK_DAMAGE), damage * 1.22, "Combined bonus");
        close(thinEdge.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new)
                .getBaseAttackModifier(), damage * 1.22, "SlashBlade runtime damage");
        double configured = GameplayConfig.FORGING_DAMAGE_MULTIPLIER.get();
        try {
            GameplayConfig.FORGING_DAMAGE_MULTIPLIER.set(0.0);
            close(item.getAttributeValue(thinEdge, Attributes.ATTACK_DAMAGE), damage, "Config multiplier");
        } finally {
            GameplayConfig.FORGING_DAMAGE_MULTIPLIER.set(configured);
        }
    }

    private static ConfigSchematic schematic(String name) throws Exception {
        return new ConfigSchematic(DataManager.instance.schematicData.getData(
                ResourceLocation.tryParse("tetra:slashblade/" + name)));
    }

    private static void close(double actual, double expected, String label) {
        check(Math.abs(actual - expected) < .01, label + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

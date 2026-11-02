package dev.bladetetra.compat.attachments;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.visual.BladeAttachmentAppearance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.module.ItemModuleMajor;
import se.mickelus.tetra.module.ModuleRegistry;
import se.mickelus.tetra.module.SchematicRegistry;

/** Opt-in isolated server diagnostic. Does not run or stop ordinary player servers. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SwordAttachmentSmokeTest {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.attachmentSmoke")) return;
        try {
            var player = FakePlayerFactory.getMinecraft(event.getServer().overworld());
            ModularSlashBladeItem item = ModItems.MODULAR_SLASHBLADE.get();
            ItemStack blade = item.createDefaultStack();
            var socketRecipe = SchematicRegistry.getSchematic(SwordAttachmentSchematics.SOCKET_SCHEMATIC);
            check(socketRecipe != null, "socket alias registered");
            var originalSocket = ModuleRegistry.instance.getModule(new ResourceLocation("tetra", "sword/socket"));
            var mountedSocket = ModuleRegistry.instance.getModule(new ResourceLocation("tetra", SwordAttachmentSchematics.SOCKET_MODULE));
            check(originalSocket != null && mountedSocket != null, "both socket modules registered");
            check(java.util.Arrays.equals(originalSocket.getVariantData(), mountedSocket.getVariantData()),
                    "socket variants reused without copying stats");
            ItemStack gem = new ItemStack(Items.DIAMOND, 64);
            check(socketRecipe.isMaterialsValid(blade, ModularSlashBladeItem.KASHIRA_SLOT,
                    new ItemStack[]{gem}), "native socket ingredient accepted: " + gem);
            blade = socketRecipe.applyUpgrade(blade, new ItemStack[]{gem},
                    false, ModularSlashBladeItem.KASHIRA_SLOT, player);
            check(!BladeAttachmentAppearance.fromStack(blade).socket().isBlank(), "socket appearance tracks actual variant");
            check(item.getModuleFromSlot(blade, ModularSlashBladeItem.KASHIRA_SLOT).getKey()
                    .equals(SwordAttachmentSchematics.SOCKET_MODULE), "socket mounted on kashira only");
            var wrapRecipe = SchematicRegistry.getSchematic(new ResourceLocation("tetra", "sword/basic_hilt/wrap_hilt"));
            check(wrapRecipe.isApplicableForSlot(ModularSlashBladeItem.TSUKA_SLOT, blade), "native wrap mapped to tsuka");
            check(wrapRecipe.isMaterialsValid(blade, ModularSlashBladeItem.TSUKA_SLOT,
                    new ItemStack[]{new ItemStack(Items.LEATHER, 64)}), "native leather wrap accepted");
            blade = wrapRecipe.applyUpgrade(blade, new ItemStack[]{new ItemStack(Items.LEATHER, 64)},
                    false, ModularSlashBladeItem.TSUKA_SLOT, player);
            var appearance = BladeAttachmentAppearance.fromStack(blade);
            check(!appearance.wrap().isBlank(), "wrap appearance tracks native improvement");
            var grip = (ItemModuleMajor) item.getModuleFromSlot(blade, ModularSlashBladeItem.TSUKA_SLOT);
            var nativeGrip = (ItemModuleMajor) ModuleRegistry.instance.getModule(new ResourceLocation("tetra", "sword/basic_hilt"));
            ItemStack reference = new ItemStack(Items.STICK);
            nativeGrip.addModule(reference, nativeGrip.getDefaultData().key, player);
            nativeGrip.addImprovement(reference, appearance.wrap(), 0);
            check(grip.getImprovement(blade, appearance.wrap()) == nativeGrip.getImprovement(reference, appearance.wrap()),
                    "wrapped tsuka uses identical native improvement numbers/effects");
            int coatings = 0;
            var materialRecipe = SchematicRegistry.getSchematic(MaterialFullerCoating.RECIPE);
            if (net.minecraftforge.fml.ModList.get().isLoaded("more_mod_tetra")) {
                check(materialRecipe != null, "MMT material coating registered without more_tetra_tools");
                check(materialRecipe.isApplicableForSlot(ModularSlashBladeItem.BLADE_SLOT, blade),
                        "MMT material coating offered for blade");
                var definition = DataManager.instance.schematicData.getData(MaterialFullerCoating.RECIPE);
                var ingredient = java.util.Arrays.stream(definition.outcomes)
                        .filter(outcome -> outcome.material != null)
                        .flatMap(outcome -> java.util.Arrays.stream(outcome.material.getApplicableItemStacks()))
                        .filter(stack -> !stack.isEmpty())
                        .filter(stack -> materialRecipe.isMaterialsValid(item.createDefaultStack(),
                                ModularSlashBladeItem.BLADE_SLOT, new ItemStack[]{stack}))
                        .findFirst().orElseThrow();
                var coated = materialRecipe.applyUpgrade(blade.copy(), new ItemStack[]{ingredient.copy()},
                        false, ModularSlashBladeItem.BLADE_SLOT, player);
                check(item.getModuleFromSlot(coated, ModularSlashBladeItem.BLADE_SLOT).getKey()
                        .equals(item.getModuleFromSlot(blade, ModularSlashBladeItem.BLADE_SLOT).getKey()),
                        "material coating preserves original blade module");
                var materialKeys = BladeAttachmentAppearance.fromStack(coated).coatings();
                check(materialKeys.size() == 1 && materialKeys.get(0).startsWith(MaterialFullerCoating.IMPROVEMENT_PREFIX),
                        "material coating installs native shared improvement");
                var originalData = java.util.Arrays.stream(DataManager.instance.improvementData.getData(
                        new ResourceLocation("tetra", "shared/mtt/material_fuller")))
                        .filter(data -> data.key.equals(materialKeys.get(0))).findFirst().orElseThrow();
                check(((ItemModuleMajor) item.getModuleFromSlot(coated, ModularSlashBladeItem.BLADE_SLOT))
                        .getImprovement(coated, materialKeys.get(0)) == originalData,
                        "material coating reuses provider improvement identity");
                var coatingContext = new se.mickelus.tetra.module.schematic.CraftingContext(
                        player.level(), player.blockPosition(), player.level().getBlockState(player.blockPosition()),
                        player, coated, ModularSlashBladeItem.BLADE_SLOT, new ResourceLocation[0]);
                check(materialRecipe.matchesRequirements(coatingContext), "material coating can be replaced");
                var replacement = java.util.Arrays.stream(definition.outcomes)
                        .filter(outcome -> outcome.material != null)
                        .flatMap(outcome -> java.util.Arrays.stream(outcome.material.getApplicableItemStacks()))
                        .filter(stack -> !stack.isEmpty() && stack.getItem() != ingredient.getItem())
                        .filter(stack -> materialRecipe.isMaterialsValid(item.createDefaultStack(),
                                ModularSlashBladeItem.BLADE_SLOT, new ItemStack[]{stack}))
                        .findFirst();
                if (replacement.isPresent()) {
                    coated = materialRecipe.applyUpgrade(coated, new ItemStack[]{replacement.get().copy()},
                            false, ModularSlashBladeItem.BLADE_SLOT, player);
                    var replacedKeys = BladeAttachmentAppearance.fromStack(coated).coatings();
                    check(replacedKeys.size() == 1 && !replacedKeys.equals(materialKeys),
                            "native material coating group replaces rather than stacks");
                }
                LogUtils.getLogger().info("MMT_MATERIAL_COATING_PASS: {} using {}", materialKeys, ingredient);
            }
            for (var recipe : DataManager.instance.schematicData.getData().values()) {
                if (!SwordAttachmentSchematics.isCoating(recipe)
                        || !java.util.Arrays.asList(recipe.slots).contains("sword/blade")) continue;
                check(java.util.Arrays.asList(recipe.slots).contains(ModularSlashBladeItem.BLADE_SLOT),
                        "loaded sword coating has blade slot: " + recipe.key);
                coatings++;
            }
            var redCoat = SchematicRegistry.getSchematic(new ResourceLocation("tetra", "sword/smoke_red_coating"));
            if (redCoat != null) {
                var blueCoat = SchematicRegistry.getSchematic(new ResourceLocation("tetra", "sword/smoke_blue_coating"));
                var context = new se.mickelus.tetra.module.schematic.CraftingContext(
                        player.level(), player.blockPosition(), player.level().getBlockState(player.blockPosition()),
                        player, blade, ModularSlashBladeItem.BLADE_SLOT, new ResourceLocation[0]);
                check(redCoat.matchesRequirements(context), "first coating available");
                blade = redCoat.applyUpgrade(blade, new ItemStack[]{new ItemStack(Items.HONEY_BOTTLE)},
                        false, ModularSlashBladeItem.BLADE_SLOT, player);
                context = new se.mickelus.tetra.module.schematic.CraftingContext(
                        player.level(), player.blockPosition(), player.level().getBlockState(player.blockPosition()),
                        player, blade, ModularSlashBladeItem.BLADE_SLOT, new ResourceLocation[0]);
                check(!blueCoat.matchesRequirements(context), "second coating blocked without modifying native stats");
                check(BladeAttachmentAppearance.fromStack(blade).coatings().equals(java.util.List.of("smoke_red_coating")),
                        "coating appearance tracks actual active improvement");
                var slashbladeModule = (ItemModuleMajor) item.getModuleFromSlot(blade, ModularSlashBladeItem.BLADE_SLOT);
                var sourceImprovement = java.util.Arrays.stream(DataManager.instance.improvementData.getData(
                        new ResourceLocation("tetra", "shared/smoke_coatings")))
                        .filter(data -> data.key.equals("smoke_red_coating")).findFirst().orElseThrow();
                check(slashbladeModule.getImprovement(blade, "smoke_red_coating") == sourceImprovement,
                        "coating uses identical loaded provider improvement");
            }
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, blade);
            blade.getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                    .ifPresent(state -> {state.setBroken(false); state.setSealed(false);});
            var target = new net.minecraft.world.entity.monster.Zombie(player.level());
            target.moveTo(player.getX() + 1, player.getY(), player.getZ());
            float health = target.getHealth();
            mods.flammpfeil.slashblade.util.AttackManager.doMeleeAttack(player, target, false, false);
            check(target.getHealth() < health, "native right-click melee path succeeds with attachments");
            // Simulate a data reload on expanded data: no duplicate slots or socket variants.
            SwordAttachmentSchematics.adapt(DataManager.instance.schematicData.getData());
            var wrap = DataManager.instance.schematicData.getData(new ResourceLocation("tetra", "sword/basic_hilt/wrap_hilt"));
            check(java.util.Arrays.stream(wrap.slots).filter(ModularSlashBladeItem.TSUKA_SLOT::equals).count() == 1,
                    "reload is idempotent");
            LogUtils.getLogger().info("SWORD_ATTACHMENT_SMOKE_PASS: native socket + wrap, loaded coatings={}", coatings);
        } catch (Throwable failure) {
            LogUtils.getLogger().error("SWORD_ATTACHMENT_SMOKE_FAIL", failure);
            throw new IllegalStateException("Sword attachment diagnostic failed", failure);
        } finally {
            event.getServer().halt(false);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private SwordAttachmentSmokeTest() {}
}

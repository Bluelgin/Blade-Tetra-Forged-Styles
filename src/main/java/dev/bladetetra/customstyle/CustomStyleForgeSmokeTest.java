package dev.bladetetra.customstyle;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import se.mickelus.tetra.blocks.scroll.ScrollTile;
import se.mickelus.tetra.blocks.workbench.AbstractWorkbenchBlock;
import se.mickelus.tetra.craftingeffect.CraftingEffectRegistry;
import se.mickelus.tetra.module.SchematicRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Opt-in Forge server integration check: real Tetra placed scroll, schematics,
 * crafting-effect pipeline, saved blade spec, and ordinary SlashBlade combo root.
 * Does not run on players' worlds or production servers.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class CustomStyleForgeSmokeTest {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.customStyleSmoke")) return;
        var level = event.getServer().overworld();
        BlockPos benchPos = level.getSharedSpawnPos().offset(12, 8, 12);
        BlockState oldBench = level.getBlockState(benchPos);
        BlockState oldScroll = level.getBlockState(benchPos.above());
        try {
            verify(level, benchPos);
            LogUtils.getLogger().info("CUSTOM_STYLE_SMOKE_PASS");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("CUSTOM_STYLE_SMOKE_FAIL", failure);
            throw new IllegalStateException("Custom style Tetra/SlashBlade smoke failed", failure);
        } finally {
            level.setBlockAndUpdate(benchPos.above(), oldScroll);
            level.setBlockAndUpdate(benchPos, oldBench);
            event.getServer().halt(false);
        }
    }

    private static void verify(ServerLevel level, BlockPos benchPos) {
        Block workbench = requiredBlock("tetra:basic_workbench");
        Block openScroll = requiredBlock("tetra:scroll_open");
        check(level.setBlockAndUpdate(benchPos, workbench.defaultBlockState()), "place Tetra workbench");
        check(level.setBlockAndUpdate(benchPos.above(), openScroll.defaultBlockState()), "place Tetra scroll");
        check(level.getBlockEntity(benchPos.above()) instanceof ScrollTile, "native ScrollTile exists");
        ScrollTile tile = (ScrollTile) level.getBlockEntity(benchPos.above());

        var spec = new CustomStyleSpec("Twin Slash", List.of(
                ComboStateRegistry.COMBO_A1.getId(), ComboStateRegistry.COMBO_B1.getId()));
        var recorded = CustomStyleScrolls.create(spec);
        check(CustomStyleScrolls.read(recorded).equals(spec), "recorded native ScrollItem retains spec");
        String shareCode = CustomStyleExchange.export(spec);
        var importedResult = CustomStyleExchange.importValidated(shareCode);
        check(importedResult.accepted() && spec.equals(importedResult.style()),
                "community export and server-validated import preserve the same spec");
        var privileged = new CustomStyleSpec("Forbidden SA",
                List.of(ComboStateRegistry.JUDGEMENT_CUT.getId()));
        check(CustomStyleExchange.importValidated(CustomStyleExchange.export(privileged)).status()
                        == CustomStyleExchange.Status.UNAVAILABLE_ACTION,
                "share codes must not turn a privileged Slash Art into a normal attack");
        var importedScroll = CustomStyleScrolls.create(importedResult.style());
        check(tile.addScroll(importedScroll),
                "imported community code unfolds as the same genuine Tetra schematic");
        check(level.getServer().getCommands().getDispatcher().getRoot()
                        .getChild("blade_tetra_style") != null,
                "in-game authoring and import/export commands registered");
        check(CustomStyleScrollLookup.unique(level, benchPos).equals(spec), "lookup real unfolded scroll");

        AbstractWorkbenchBlock bench = (AbstractWorkbenchBlock) workbench;
        var baseBlade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        var slot = ModularSlashBladeItem.BLADE_SLOT;
        var player = FakePlayerFactory.getMinecraft(level);
        level.addNewPlayer(player);
        var unlocks = bench.getSchematics(level, benchPos, level.getBlockState(benchPos));
        var active = SchematicRegistry.getSchematics(baseBlade, slot, player, level, benchPos,
                level.getBlockState(benchPos), unlocks);
        check(Arrays.asList(unlocks).containsAll(CustomStyleScrolls.UNLOCKS), "native scroll unlocks all 3 schematics");
        var effectUnlocks = bench.getCraftingEffects(level, benchPos, level.getBlockState(benchPos));
        check(Arrays.asList(effectUnlocks).contains(CustomStyleScrolls.CRAFTING_EFFECT), "native crafting effect unlocked");

        String[] names = {"short", "standard", "long"};
        String[] modules = {ModularSlashBladeItem.CUSTOM_SHORT_BLADE_MODULE,
                ModularSlashBladeItem.CUSTOM_STANDARD_BLADE_MODULE,
                ModularSlashBladeItem.CUSTOM_LONG_BLADE_MODULE};
        for (int i = 0; i < names.length; i++) {
            ResourceLocation id = new ResourceLocation("tetra", "slashblade/custom_style_" + names[i]);
            var schematic = SchematicRegistry.getSchematic(id);
            check(schematic != null, "data-backed custom schematic " + id);
            check(Arrays.stream(active).anyMatch(it -> schematic.getKey().equals(it.getKey())),
                    "Tetra workbench lists unlocked " + names[i] + " blade");
            var material = new ItemStack(Items.IRON_INGOT, 12);
            var materials = new ItemStack[]{material};
            check(schematic.acceptsMaterial(baseBlade, slot, 0, material),
                    "custom " + names[i] + " schematic recognizes iron through Tetra materials");
            check(schematic.isMaterialsValid(baseBlade, slot, materials),
                    "native workbench material validation " + names[i]);
            var result = schematic.applyUpgrade(baseBlade, materials, false, slot, player);
            check(modules[i].equals(result.getOrCreateTag().getString(slot)),
                    "real Tetra schematic installs " + modules[i]);
            var effects = CraftingEffectRegistry.getEffects(effectUnlocks, result, slot,
                    true, player, materials, Map.<ToolAction, Integer>of(), schematic,
                    level, benchPos, level.getBlockState(benchPos));
            check(effects.length == 1, "precisely one style-encoding crafting effect");
            check(effects[0].applyOutcomes(effectUnlocks, result, slot,
                    true, player, materials, materials, Map.of(),
                    level, schematic, benchPos, level.getBlockState(benchPos), true),
                    "native Tetra crafting outcome succeeds");
            check(spec.equals(CustomStyleSpec.decode(
                    result.getTag().getCompound(CustomStyleSpec.STACK_KEY))), "style persisted onto " + names[i]);
            check(StyleResolver.resolve(result) == BladeStyle.CUSTOM,
                    "new blade resolves CUSTOM without altering preset styles");
            ModItems.MODULAR_SLASHBLADE.get().syncDerivedBladeState(result);
            check(result.getCapability(ModularSlashBladeItem.BLADESTATE)
                    .map(state -> ModComboStates.CUSTOM_ROOT.getId().equals(state.getComboRoot()))
                    .orElse(false), "native SlashBlade root points to player custom style");
            if (i == 1) verifyNativeCombo(player, result);
        }

        check(StyleResolver.resolve(baseBlade) == BladeStyle.IAIDO, "original Iaido remains unchanged");
        check(!new CustomStyleCraftingCondition().test(effectUnlocks, baseBlade,
                slot, true, player, new ItemStack[0], Map.of(),
                SchematicRegistry.getSchematic(new ResourceLocation("tetra","slashblade/katana_blade")),
                level, benchPos, level.getBlockState(benchPos)),
                "custom effect never touches unrelated preset schematic");

        // Other scroll stacks carrying a different recording are ambiguous:
        var another = CustomStyleScrolls.create(new CustomStyleSpec("Other", List.of(ComboStateRegistry.COMBO_A2.getId())));
        check(tile.addScroll(another), "Tetra permits a second scroll");
        check(CustomStyleScrollLookup.unique(level, benchPos) == null,
                "ambiguous scrolls block forging rather than silently selecting one");
    }

    private static void verifyNativeCombo(net.minecraft.world.entity.player.Player player, ItemStack blade) {
        player.setItemSlot(EquipmentSlot.MAINHAND, blade);
        player.setOnGround(true);
        var input = player.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
        input.getCommands().clear();
        input.getCommands().add(InputCommand.R_CLICK);
        var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
        state.setComboSeq(ComboStateRegistry.NONE.getId());
        var preview = state.progressCombo(player, true);
        check(preview.equals(ComboStateRegistry.COMBO_A1.getId()),
                "virtual native first combo is the first recorded action: " + preview);
        var committed = state.progressCombo(player);
        check(committed.equals(ComboStateRegistry.COMBO_A1.getId()),
                "native first combo is the first recorded action: " + committed);
        // The native A1 action has a five-frame commitment window.
        // Querying a virtual prediction must NOT advance the authored cursor.
        state.setLastActionTime(player.level().getGameTime() - 4L);
        var secondPreview = state.progressCombo(player, true);
        check(secondPreview.equals(ComboStateRegistry.COMBO_B1.getId()),
                "virtual follow-up predicts recorded second move: " + secondPreview);
        var secondCommitted = state.progressCombo(player);
        check(secondCommitted.equals(ComboStateRegistry.COMBO_B1.getId()),
                "native follow-up commits second recorded action: " + secondCommitted);
        check(state.getComboSeq().equals(ComboStateRegistry.COMBO_B1.getId()),
                "server-side ComboState is second recorded action");
    }

    private static Block requiredBlock(String id) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        check(block != null && block != net.minecraft.world.level.block.Blocks.AIR, "registered block " + id);
        return block;
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private CustomStyleForgeSmokeTest() {}
}

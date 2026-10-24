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
    private static net.minecraft.world.entity.player.Player timingActor;
    private static int timelineSlashes;
    @SubscribeEvent public static void slash(mods.flammpfeil.slashblade.event.SlashBladeEvent.DoSlashEvent event) {
        if (timingActor != null && event.getUser() == timingActor) timelineSlashes++;
    }
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.customStyleSmoke")) return;
        var level = event.getServer().overworld();
        BlockPos benchPos = level.getSharedSpawnPos().offset(12, 8, 12);
        BlockState oldBench = level.getBlockState(benchPos);
        BlockState oldScroll = level.getBlockState(benchPos.above());
        try {
            verify(level, benchPos);
            verifyExchange(level);
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
            if (i == 1) {
                verifyNativeCombo(player, result);
                verifyInputTicks(level, player, result);
            }
            verifyRoutes(level, player, result, CustomBladeLength.values()[i]);
            verifyTiming(level, player, result, CustomBladeLength.values()[i]);
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
        tickBlade((ServerLevel) player.level(), player, blade, 4);
        var secondPreview = state.progressCombo(player, true);
        check(secondPreview.equals(ComboStateRegistry.COMBO_B1.getId()),
                "virtual follow-up predicts recorded second move: " + secondPreview);
        var secondCommitted = state.progressCombo(player);
        check(secondCommitted.equals(ComboStateRegistry.COMBO_B1.getId()),
                "native follow-up commits second recorded action: " + secondCommitted);
        check(state.getComboSeq().equals(ComboStateRegistry.COMBO_B1.getId()),
                "server-side ComboState is second recorded action");

        // Regression: two adjacent identical native states MUST each execute
        // once. NextComboEvent is non-cancelable: the old workaround crashed.
        var duplicates = new CustomStyleSpec("Echo", List.of(
                ComboStateRegistry.COMBO_A1.getId(),
                ComboStateRegistry.COMBO_A1.getId(),
                ComboStateRegistry.COMBO_B1.getId()));
        blade.getOrCreateTag().put(CustomStyleSpec.STACK_KEY, duplicates.encode());
        state.setComboSeq(ComboStateRegistry.NONE.getId());
        check(state.progressCombo(player).equals(ComboStateRegistry.COMBO_A1.getId()),
                "duplicate-style first click is A1");
        tickBlade((ServerLevel) player.level(), player, blade, 4);
        check(state.progressCombo(player).equals(ComboStateRegistry.COMBO_A1.getId()),
                "duplicate-style second click re-enters A1 without canceling an uncancelable event");
        tickBlade((ServerLevel) player.level(), player, blade, 4);
        check(state.progressCombo(player).equals(ComboStateRegistry.COMBO_B1.getId()),
                "duplicate-style third click advances to B1");

    }

    private static void verifyInputTicks(ServerLevel level, net.minecraft.world.entity.player.Player player, ItemStack blade) {
        var spec = new CustomStyleSpec("Input", List.of(ComboStateRegistry.COMBO_A1.getId(),
                ComboStateRegistry.COMBO_B1.getId(), ComboStateRegistry.COMBO_A2.getId()));
        blade.getOrCreateTag().put(CustomStyleSpec.STACK_KEY, spec.encode());
        var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
        state.setComboSeq(ComboStateRegistry.NONE.getId());
        player.getCapability(CapabilityInputState.INPUT_STATE).ifPresent(input -> input.getCommands().clear());
        for (int round = 0; round < 6; round++) {
            blade.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            player.stopUsingItem();
            LogUtils.getLogger().info("CUSTOM_INPUT beat={} actual={} expected={}", round, state.getComboSeq(), spec.actions().get(round % 3));
            check(state.getComboSeq().equals(spec.actions().get(round % 3)), "Item.use + inventory ticks beat " + round);
            tickBlade(level, player, blade, 12);
        }
    }

    private static void tickBlade(ServerLevel level, net.minecraft.world.entity.player.Player player, ItemStack blade, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            blade.getItem().inventoryTick(blade, level, player, player.getInventory().selected, true);
            level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime() + 1);
        }
    }

    private static void verifyRoutes(ServerLevel level, net.minecraft.world.entity.player.Player player, ItemStack blade, CustomBladeLength length) {
        player.setItemSlot(EquipmentSlot.MAINHAND, blade); player.setOnGround(true);
        var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
        var input = player.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
        input.getCommands().clear();
        List<List<ResourceLocation>> routes = List.of(
                ids("combo_a1", "combo_b1", "combo_a2", "combo_b2"),
                ids("combo_a1", "combo_a1", "combo_b1"),
                ids("combo_a1", "combo_b1", "combo_a2", "combo_b2", "combo_a3", "combo_c"),
                ids("combo_a1"), ids("upperslash", "upperslash", "rapid_slash"),
                ids("rapid_slash", "combo_a1"));
        for (var route : routes) {
            blade.getOrCreateTag().put(CustomStyleSpec.STACK_KEY, new CustomStyleSpec("Routes", route).encode());
            CustomComboRouter.interrupt(player); state.setComboSeq(ComboStateRegistry.NONE.getId());
            for (int beat = 0; beat < route.size() * 2; beat++) {
                var action = route.get(beat % route.size());
                blade.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND); player.stopUsingItem();
                check(state.getComboSeq().equals(action), "input route " + length + " beat=" + beat + " actual=" + state.getComboSeq() + " expected=" + action);
                long entered = state.getLastActionTime();
                // An early second click must not restart, skip or duplicate this hit.
                blade.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND); player.stopUsingItem();
                check(state.getLastActionTime() == entered && state.getComboSeq().equals(action), "early click rejected " + length);
                tickBlade(level, player, blade, 28);
            }
            tickBlade(level, player, blade, 41);
            blade.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND); player.stopUsingItem();
            check(state.getComboSeq().equals(route.get(0)), "timeout restarts at first action " + length);
        }
        // Native target search, with actual held-item attribute modifiers applied.
        var reach = player.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
        var modifiers = blade.getAttributeModifiers(EquipmentSlot.MAINHAND);
        var owned = modifiers.get(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
        for (var modifier : owned) { reach.removeModifier(modifier); reach.addTransientModifier(modifier); }
        double resolved = mods.flammpfeil.slashblade.util.TargetSelector.getResolvedReach(player);
        check(Math.abs(resolved - (reach.getBaseValue() + length.reachBonus() - 1)) < 1e-5, "native reach resolves actual attribute " + length + " " + resolved);
        var target = new net.minecraft.world.entity.monster.Zombie(level);
        target.setNoAi(true); target.setNoGravity(true); target.setPos(player.getX(), player.getY(), player.getZ() + resolved - .2);
        level.addFreshEntity(target); player.setYRot(0); player.setXRot(0);
        check(mods.flammpfeil.slashblade.util.TargetSelector.getTargettableEntitiesWithinAABB(level, player).contains(target), "native server target in reach " + length);
        float beforeHit = target.getHealth();
        mods.flammpfeil.slashblade.util.AttackManager.areaAttack(player, ignored -> {}, 1F, true, true, true);
        check(target.getHealth() < beforeHit, "native managed melee damages the target inside reach " + length);
        target.setPos(player.getX(), player.getY(), player.getZ() + resolved + .2);
        check(!mods.flammpfeil.slashblade.util.TargetSelector.getTargettableEntitiesWithinAABB(level, player).contains(target), "native server target outside reach " + length);
        float afterHit = target.getHealth();
        mods.flammpfeil.slashblade.util.AttackManager.areaAttack(player, ignored -> {}, 1F, true, true, true);
        check(target.getHealth() == afterHit, "native managed melee cannot damage outside reach " + length);
        target.discard();
        dev.bladetetra.combat.CombatBalanceRuntime.selectedSlashArt(player, state.getComboSeq());
        check(Math.abs(mods.flammpfeil.slashblade.util.TargetSelector.getResolvedReach(player)
                - (reach.getBaseValue() + CustomBladeLength.STANDARD.reachBonus() - 1)) < 1e-5, "SA has standard reach independent of length");
        dev.bladetetra.combat.CombatBalanceRuntime.ordinaryCombo(player);
        owned.forEach(reach::removeModifier);
        // Air and SA interruption return to the first authored move on landing.
        tickBlade(level, player, blade, 45);
        player.setOnGround(false);
        blade.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND); player.stopUsingItem();
        check(state.getComboSeq().equals(ComboStateRegistry.AERIAL_RAVE_A1.getId()), "native air path preserved: " + state.getComboSeq());
        player.setOnGround(true); tickBlade(level, player, blade, 45);
        blade.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND); player.stopUsingItem();
        check(state.getComboSeq().equals(ids("rapid_slash").get(0)), "landing restarts last route");
        // Real stack copies keep the stable Tetra identifier; actual holstering resets.
        blade.getOrCreateTag().put(CustomStyleSpec.STACK_KEY, new CustomStyleSpec("Switch", ids("combo_a1", "combo_b1", "combo_a2")).encode());
        state.setComboSeq(ComboStateRegistry.NONE.getId()); CustomComboRouter.interrupt(player);
        blade.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND); player.stopUsingItem();
        tickBlade(level, player, blade, 5);
        var copied = blade.copy(); player.setItemSlot(EquipmentSlot.MAINHAND, copied);
        copied.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND); player.stopUsingItem();
        check(copied.getCapability(ModularSlashBladeItem.BLADESTATE).map(it -> it.getComboSeq().equals(ids("combo_b1").get(0))).orElse(false), "Tetra stack copy does not reset cursor");
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        CustomComboRouter.held(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(player));
        player.setItemSlot(EquipmentSlot.MAINHAND, blade); tickBlade(level, player, blade, 5);
        blade.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND); player.stopUsingItem();
        check(state.getComboSeq().equals(ids("combo_a1").get(0)), "holstering and drawing starts first action");
        // Actual native item release enters an SA; its completion resets the authored chain.
        blade.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
        state.setBroken(false); state.setSealed(false);
        tickBlade(level, player, blade, 5);
        blade.getItem().releaseUsing(blade, level, player, blade.getUseDuration() - state.getFullChargeTicks(player) - 1);
        check(!ComboCompatibilityResolver.playable(state.getComboSeq()) && !state.getComboSeq().equals(ComboStateRegistry.NONE.getId()), "native charged item release enters privileged SA");
        tickBlade(level, player, blade, 200);
        blade.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND); player.stopUsingItem();
        check(state.getComboSeq().equals(ids("combo_a1").get(0)), "first ordinary action after SA recovery");
        LogUtils.getLogger().info("CUSTOM_ROUTES_PASS length={} nativeReach={}", length, resolved);
    }
    private static List<ResourceLocation> ids(String... paths) {
        return Arrays.stream(paths).map(path -> new ResourceLocation("slashblade", path)).toList();
    }

    private static void verifyTiming(ServerLevel level, net.minecraft.world.entity.player.Player player, ItemStack blade, CustomBladeLength length) {
        var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
        for (var sample : Map.of("combo_a1", 1, "combo_c", 2, "combo_a3", 2,
                "combo_a4_ex", 2, "upperslash", 1, "rapid_slash", 5).entrySet()) {
            var action = ids(sample.getKey()).get(0);
            int minimum = CustomStyleTimingPolicy.timing(action, length).effectiveTicks();
            blade.getOrCreateTag().put(CustomStyleSpec.STACK_KEY, new CustomStyleSpec("Timing", ids(sample.getKey(), "combo_a2")).encode());
            state.setComboSeq(ComboStateRegistry.NONE.getId()); CustomComboRouter.interrupt(player);
            timingActor = player; timelineSlashes = 0;
            blade.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND); player.stopUsingItem();
            long entered = state.getLastActionTime();
            tickBlade(level, player, blade, minimum - 1);
            blade.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND); player.stopUsingItem();
            check(state.getLastActionTime() == entered || !state.getComboSeq().equals(ids("combo_a2").get(0)), "no premature follow-up " + sample.getKey());
            tickBlade(level, player, blade, 1);
            check(timelineSlashes == sample.getValue(), "required native callbacks exactly once " + sample.getKey() + " " + length + " count=" + timelineSlashes);
            blade.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND); player.stopUsingItem();
            check(state.getComboSeq().equals(ids("combo_a2").get(0)), "follow-up at safe boundary " + length + " " + sample.getKey());
            check(timelineSlashes == sample.getValue() + 1, "one native follow-up slash, no duplicate damage");
            timingActor = null;
            LogUtils.getLogger().info("CUSTOM_TIMING_PASS length={} action={} tick={} requiredSlashes={}", length, sample.getKey(), minimum, sample.getValue());
        }
    }

    private static void verifyExchange(ServerLevel level) {
        var player = FakePlayerFactory.getMinecraft(level);
        var spec = new CustomStyleSpec("Import draft", ids("combo_a1", "combo_a1", "combo_b1"));
        String code = CustomStyleExchange.export(spec);
        String enriched = CustomStyleExchange.exportWithDependencies(spec);
        check(CustomStyleExchange.importValidated(enriched).accepted()
                && CustomStyleExchange.inspect(enriched).requirements().isEmpty(),
                "built-in provider IDs implied without metadata overhead");
        check(StyleRecordingSession.importCode(player, code), "valid share code loads draft");
        check(StyleRecordingSession.exportable(player).equals(spec), "draft export preserves repeats and order");
        var before = StyleRecordingSession.snapshot(player);
        String damaged = (code.startsWith("A") ? "B" : "A") + code.substring(1);
        check(!StyleRecordingSession.importCode(player, damaged), "checksum damage refused");
        check(before.equals(StyleRecordingSession.snapshot(player)), "damaged compact code preserves draft");
        check(!StyleRecordingSession.importCode(player, "BTSTYLE1:corrupt"), "corrupt import refused");
        check(before.equals(StyleRecordingSession.snapshot(player)), "failed import leaves draft unchanged");
        var missing = new ResourceLocation("different_namespace", "missing_cut");
        CustomStyleDependencyResolver.registerProvider(missing,
                new CustomStyleDependencyResolver.Provider("not_installed_test_mod", "Trusted missing provider", ""));
        check(CustomStyleDependencyResolver.diagnose(missing).status() == CustomStyleDependencyResolver.Status.MISSING_MOD,
                "trusted provider mod ID differs from action namespace");
        check(CustomStyleDependencyResolver.diagnose(new ResourceLocation("slashblade", "not_registered")).status()
                == CustomStyleDependencyResolver.Status.UNREGISTERED_ACTION, "installed mod with absent action is not missing mod");
        var incompatible = new ResourceLocation("different_namespace", "incompatible_cut");
        CustomStyleDependencyResolver.registerProvider(incompatible,
                new CustomStyleDependencyResolver.Provider("slashblade", "SlashBlade", "[999999,)"));
        check(CustomStyleDependencyResolver.diagnose(incompatible).status() == CustomStyleDependencyResolver.Status.INCOMPATIBLE_VERSION,
                "only trusted adapter version constraints are authoritative");
        check(CustomStyleDependencyResolver.diagnose(ComboStateRegistry.JUDGEMENT_CUT.getId()).status()
                == CustomStyleDependencyResolver.Status.UNSAFE_ACTION, "registered SA remains forbidden");
        var unknown = CustomStyleDependencyResolver.diagnose(new ResourceLocation("no_mapping", "move"));
        check(unknown.provider().isEmpty() && unknown.modId().isEmpty() && unknown.status()
                == CustomStyleDependencyResolver.Status.UNREGISTERED_ACTION, "unknown namespace does not invent a provider");
        var missingCode = CustomStyleExchange.exportWithDependencies(new CustomStyleSpec("Missing", List.of(missing, missing)));
        var preview = CustomStyleDependencyResolver.inspect(missingCode);
        check(preview.style() != null && !preview.allowed(), "missing addon still permits content preview");
        check(preview.style().actions().equals(List.of(missing, missing))
                && preview.missingDeclaredMods().contains("not_installed_test_mod"), "external dictionary and provider hints preserved");
        check(!StyleRecordingSession.importCode(player, missingCode)
                && before.equals(StyleRecordingSession.snapshot(player)), "missing addon cannot replace a valid draft");
        check(StyleRecordingSession.finish(player).equals(spec), "imported draft finalizes as original style");
        LogUtils.getLogger().info("CUSTOM_EXCHANGE_SMOKE_PASS");
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

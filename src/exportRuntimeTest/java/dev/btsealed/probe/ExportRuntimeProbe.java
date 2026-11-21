package dev.btsealed.probe;

import com.mojang.logging.LogUtils;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod("bt_export_probe")
public final class ExportRuntimeProbe {
    public ExportRuntimeProbe() {
        MinecraftForge.EVENT_BUS.addListener(ExportRuntimeProbe::verify);
    }

    private static void verify(ServerStartedEvent event) {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) return;
        try {
            var modList = ModList.get();
            if (modList.isLoaded("tetra") || modList.isLoaded("blade_tetra")
                    || !modList.isLoaded("bt_export_runtime")) {
                throw new IllegalStateException("Expected only the standalone combat runtime");
            }
            var world = event.getServer().overworld();
            var player = FakePlayerFactory.getMinecraft(world);
            player.setOnGround(true);
            var input = player.getCapability(CapabilityInputState.INPUT_STATE).resolve().orElseThrow();
            input.getCommands().add(InputCommand.R_CLICK);

            var first = craft(world, "bt_export_custom");
            var savedBlade = ItemStack.of(first.save(new net.minecraft.nbt.CompoundTag()));
            if (!savedBlade.getCapability(ItemSlashBlade.BLADESTATE)
                    .map(data -> data.getComboRoot().equals(ResourceLocation.parse("bt_export_runtime:custom_root")))
                    .orElse(false)) {
                throw new IllegalStateException("Sealed blade lost its combo after saving and loading");
            }
            first = savedBlade;
            player.setItemInHand(InteractionHand.MAIN_HAND, first);
            var state = first.getCapability(ItemSlashBlade.BLADESTATE).resolve().orElseThrow();
            var modifiers = first.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED);
            if (modifiers.size() != 1 || Math.abs(modifiers.iterator().next().getAmount() + 2.3) > 0.001) {
                throw new IllegalStateException("Sealed attack speed was not loaded");
            }
            long now = world.getGameTime();
            verifyMove(state.progressCombo(player, true), "sequence_0_combo_a1");
            verifyMove(state.progressCombo(player, false), "sequence_0_combo_a1");
            if (!state.progressCombo(player, false).equals(ComboStateRegistry.NONE.getId())) {
                throw new IllegalStateException("Repeated click bypassed the attack window");
            }
            state.setLastActionTime(now - 3);
            verifyMove(state.progressCombo(player, false), "sequence_1_combo_a1");
            state.setLastActionTime(now - 3);
            verifyMove(state.progressCombo(player, false), "sequence_2_combo_c");

            var second = craft(world, "bt_export_custom_second");
            player.setItemInHand(InteractionHand.MAIN_HAND, second);
            ISlashBladeState secondState = second.getCapability(ItemSlashBlade.BLADESTATE).resolve().orElseThrow();
            verifyMove(secondState.progressCombo(player, false), "sequence_0_combo_c");
            secondState.setLastActionTime(now - 10);
            verifyMove(secondState.progressCombo(player, false), "sequence_6_combo_c");
            player.setShiftKeyDown(true);
            player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            var attacker = net.minecraft.world.entity.EntityType.ZOMBIE.create(world);
            var hit = new net.minecraftforge.event.entity.living.LivingHurtEvent(
                    player, world.damageSources().mobAttack(attacker), 10);
            int damageBefore = secondState.getDamage();
            MinecraftForge.EVENT_BUS.post(hit);
            if (hit.getAmount() != 7.5F || secondState.getDamage() != damageBefore + 1) {
                throw new IllegalStateException("Guard tsuba effect was not retained");
            }
            verifyPrecisionRank(world, player, second);
            verifyReinforcedKnockback(world, player);
            verifyDangaku(world, player);
            PortableRuntimeSelfTest.verifyPrecisionClock();
            PortableRuntimeSelfTest.verifySpiritSwordIsolation();
            PortableRuntimeSelfTest.verifyReinforcedArithmetic();
            PortableRuntimeSelfTest.verifyCapabilityContracts();
            LogUtils.getLogger().info("BLADE_EXPORT_RUNTIME_PROBE_PASS");
        } catch (RuntimeException error) {
            LogUtils.getLogger().error("BLADE_EXPORT_RUNTIME_PROBE_FAIL", error);
            throw error;
        } finally {
            event.getServer().halt(false);
        }
    }

    /** Compare actual native Rank balances with the original Blade Tetra precision-habaki acceptance fixture. */
    private static void verifyPrecisionRank(ServerLevel world, ServerPlayer player, ItemStack focusBlade) {
        var rank = player.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new);
        long originalTime = world.getGameTime();
        // Match the source-mod parity fixture: the pre-start zero tick is not
        // a real elapsed combat tick and must not influence Rank decay.
        long firstTick = Math.max(1L, originalTime);
        long initial = rank.getUnitCapacity() * 2L + 120L;
        try {
            rank.setRawRankPoint(initial);
            rank.setLastUpdte(firstTick);
            for (int tick = 0; tick <= 40; tick++) {
                long now = firstTick + tick;
                world.getServer().getWorldData().overworldData().setGameTime(now);
                player.setItemInHand(InteractionHand.MAIN_HAND,
                        tick >= 20 && tick < 30 ? ItemStack.EMPTY : focusBlade);
                playerTick(player);
                if (tick == 20 && rank.getRankPoint(now) != initial - 10L) {
                    throw new IllegalStateException("Precision Habaki did not halve native Rank decay: expected="
                            + (initial - 10L) + ", actual=" + rank.getRankPoint(now)
                            + ", lastUpdate=" + rank.getLastUpdate());
                }
                if (tick == 30 && rank.getRankPoint(now) != initial - 20L) {
                    throw new IllegalStateException("Precision Habaki swap parity failed: expected="
                            + (initial - 20L) + ", actual=" + rank.getRankPoint(now)
                            + ", lastUpdate=" + rank.getLastUpdate());
                }
            }
            if (rank.getRankPoint(firstTick + 40) != initial - 25L) {
                throw new IllegalStateException("Precision Habaki resumed with wrong native Rank decay");
            }
            rank.addRankPoint(player, -60L);
            if (rank.getRankPoint(firstTick + 40) != initial - 85L) {
                throw new IllegalStateException("Precision Habaki explicit Rank cost mismatch: expected="
                        + (initial - 85L) + ", actual=" + rank.getRankPoint(firstTick + 40));
            }
            LogUtils.getLogger().info("BLADE_EXPORT_PRECISION_RANK_PASS");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, focusBlade);
            world.getServer().getWorldData().overworldData().setGameTime(originalTime);
        }
    }

    /** Actual native slash/velocity parity with Blade Tetra's reinforced-habaki Forge smoke. */
    private static void verifyReinforcedKnockback(ServerLevel world, ServerPlayer player) {
        world.getChunk(0, 0);
        long originalTime = world.getGameTime();
        var originalHand = player.getMainHandItem().copy();
        try {
            world.getServer().getWorldData().overworldData().setGameTime(Math.max(1L, originalTime));
            player.moveTo(4, 200, 4, 0, 0);
            player.setYHeadRot(0);
            player.yBodyRot = 0;
            player.setShiftKeyDown(false);
            player.setPose(net.minecraft.world.entity.Pose.STANDING);
            player.setOnGround(true);

            var ordinary = slashMomentum(world, player, "bt_export_custom", KnockBacks.cancel);
            var strong = slashMomentum(world, player, "bt_export_reinforced", KnockBacks.cancel);
            var toss = slashMomentum(world, player, "bt_export_reinforced", KnockBacks.toss);
            if (Math.abs(ordinary.z - .12D) > .02D || Math.abs(ordinary.y - .04D) > .01D) {
                throw new IllegalStateException("Ordinary native slash momentum changed: " + ordinary);
            }
            if (!(strong.z > .60D) || Math.abs(strong.y - .04D) > .01D) {
                throw new IllegalStateException("Reinforced slash did not preserve source horizontal/y parity: "
                        + strong + " (ordinary=" + ordinary + ")");
            }
            if (toss.y < .75D) {
                throw new IllegalStateException("Reinforced Habaki altered native special toss: " + toss);
            }
            LogUtils.getLogger().info("BLADE_EXPORT_REINFORCED_KNOCKBACK_PASS: ordinary={}, strong={}, toss={}",
                    ordinary, strong, toss);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, originalHand);
            world.getServer().getWorldData().overworldData().setGameTime(originalTime);
        }
    }

    private static Vec3 slashMomentum(ServerLevel world, ServerPlayer player, String namespace,
            KnockBacks knockback) {
        var blade = craft(world, namespace);
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);
        var state = blade.getCapability(ItemSlashBlade.BLADESTATE).resolve().orElseThrow();
        state.setComboSeq(ComboStateRegistry.COMBO_A1.getId());
        state.setLastActionTime(world.getGameTime());
        var target = new Zombie(world);
        target.setNoAi(true);
        target.setPos(4, 200, 5.5);
        target.setOnGround(true);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        target.setDeltaMovement(0D, .04D, .12D);
        if (!world.addFreshEntity(target)) throw new IllegalStateException("Native knockback target was not registered");
        state.setTargetEntityId(target.getId());
        var slash = mods.flammpfeil.slashblade.util.AttackManager.doSlash(
                player, 0, Vec3.ZERO, false, true, 1, knockback);
        try {
            if (slash == null || slash.getShooter() != player) {
                throw new IllegalStateException("Failed to create a native SlashBlade slash for " + namespace);
            }
            slash.tickCount = 2;
            slash.tick();
            if (target.getHealth() >= target.getMaxHealth()) {
                throw new IllegalStateException("Native slash missed its test target: " + namespace);
            }
            return target.getDeltaMovement();
        } finally {
            if (slash != null) slash.discard();
            target.discard();
        }
    }

    private static void verifyDangaku(ServerLevel world, ServerPlayer player) {
        var blade = craft(world, "bt_export_dangaku");
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);
        player.setShiftKeyDown(false);
        player.setPose(net.minecraft.world.entity.Pose.STANDING);
        player.setOnGround(true);
        var input = player.getCapability(CapabilityInputState.INPUT_STATE).resolve().orElseThrow().getCommands();
        input.clear();
        input.add(InputCommand.R_CLICK);
        var state = blade.getCapability(ItemSlashBlade.BLADESTATE).resolve().orElseThrow();
        verifyMove(state.getComboRoot(), "dangaku_root");

        long now = world.getGameTime();
        verifyMove(state.progressCombo(player, false), "dangaku_sweep");
        state.setLastActionTime(now - 4);
        verifyMove(state.progressCombo(player, false), "dangaku_return");
        state.setLastActionTime(now - 4);
        verifyMove(state.progressCombo(player, false), "dangaku_finish");
        state.setLastActionTime(now - 14);
        verifyMove(state.progressCombo(player, false), "dangaku_circle_recovery");

        state.setComboSeq(ComboStateRegistry.NONE.getId());
        state.setLastActionTime(now);
        verifyMove(state.progressCombo(player, false), "dangaku_sweep");
        state.setLastActionTime(now - 4);
        verifyMove(state.progressCombo(player, false), "dangaku_return");
        state.setLastActionTime(now - 8);
        verifyMove(state.progressCombo(player, false), "dangaku_cleave");
        var slash = new SlashBladeEvent.DoSlashEvent(blade, state, player, 0, false, 10, KnockBacks.cancel);
        MinecraftForge.EVENT_BUS.post(slash);
        if (Math.abs(slash.getDamage() - 5.5) > .0001 || slash.getKnockback() != KnockBacks.smash) {
            throw new IllegalStateException("Dangaku heavy slash lost its damage or knockback");
        }

        var slashEntity = new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect, world);
        slashEntity.setShooter(player);
        MinecraftForge.EVENT_BUS.post(new EntityJoinLevelEvent(slashEntity, world));
        if (slashEntity.getBaseSize() != 1.55F) throw new IllegalStateException("Dangaku heavy slash range was lost");

        var attacker = net.minecraft.world.entity.EntityType.ZOMBIE.create(world);
        var hurt = new net.minecraftforge.event.entity.living.LivingHurtEvent(
                player, world.damageSources().mobAttack(attacker), 10);
        MinecraftForge.EVENT_BUS.post(hurt);
        if (hurt.getAmount() != 6.5F) throw new IllegalStateException("Dangaku heavy armor window was lost");
        var knockback = new net.minecraftforge.event.entity.living.LivingKnockBackEvent(player, 1, 1, 0);
        if (!MinecraftForge.EVENT_BUS.post(knockback)) throw new IllegalStateException("Dangaku armor allowed knockback");

        state.setComboSeq(ComboStateRegistry.NONE.getId());
        state.setLastActionTime(now);
        player.setOnGround(false);
        verifyMove(state.progressCombo(player, false), "dangaku_air_first");
        player.setOnGround(true);
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
        verifyMove(state.getComboSeq(), "dangaku_recovery");

        state.setComboSeq(ComboStateRegistry.NONE.getId());
        state.setLastActionTime(now);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        double normalSpeed = speed.getValue();
        input.clear();
        input.add(InputCommand.L_DOWN);
        for (int tick = 0; tick < 6; tick++) playerTick(player);
        verifyMove(state.getComboSeq(), "dangaku_spin");
        if (Math.abs(speed.getValue() / normalSpeed - .75) > .0001) {
            throw new IllegalStateException("Dangaku spin did not slow the player");
        }
        state.setLastActionTime(now - 12);
        playerTick(player);
        verifyMove(state.getComboSeq(), "dangaku_spin");
        if (state.getLastActionTime() != now) throw new IllegalStateException("Held spin did not start a new circle");
        input.clear();
        state.setLastActionTime(now - 5);
        playerTick(player);
        verifyMove(state.getComboSeq(), "dangaku_spin");
        state.setLastActionTime(now - 12);
        playerTick(player);
        verifyMove(state.getComboSeq(), "dangaku_spin_recovery");
        if (Math.abs(speed.getValue() - normalSpeed) > .0001) {
            throw new IllegalStateException("Dangaku recovery left behind a slowdown modifier");
        }
    }

    private static void playerTick(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
    }

    static ItemStack craft(ServerLevel world, String namespace) {
        var player = FakePlayerFactory.getMinecraft(world);
        var menu = new CraftingMenu(1, player.getInventory());
        var grid = new TransientCraftingContainer(menu, 3, 3);
        grid.setItem(4, new ItemStack(Items.IRON_INGOT));
        var recipeId = ResourceLocation.fromNamespaceAndPath(namespace, "sealed_blade");
        var recipe = (CraftingRecipe) world.getRecipeManager().byKey(recipeId).orElseThrow();
        if (!recipe.matches(grid, world)) throw new IllegalStateException("Exported recipe did not match");
        var result = recipe.assemble(grid, world.registryAccess());
        if (!result.getCapability(ItemSlashBlade.BLADESTATE).map(
                state -> state.getTranslationKey().equals("item." + namespace + ".sealed_blade")).orElse(false)) {
            throw new IllegalStateException("Crafting returned the wrong blade: " + result);
        }
        return result;
    }

    private static void verifyMove(ResourceLocation actual, String expected) {
        if (!ResourceLocation.fromNamespaceAndPath("bt_export_runtime", expected).equals(actual)) {
            throw new IllegalStateException("Expected combo " + expected + ", got " + actual);
        }
    }
}

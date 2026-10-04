package dev.bladetetra.combat;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import se.mickelus.tetra.items.modular.IModularItem;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import static dev.bladetetra.combat.StyleBranchRules.*;

/** Real Forge/native graph verification, opt-in and isolated from ordinary worlds. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class StyleBranchSmokeTest {
    private static LivingEntity actor;
    private static final Map<Phase, Integer> SLASHES = new EnumMap<>(Phase.class);
    private static final Map<Phase, Double> DAMAGE = new EnumMap<>(Phase.class);
    private static final List<EntitySlashEffect> CIRCLES = new ArrayList<>();
    private static double nativeBRushDamage;
    private static final List<EntitySlashEffect> SPIN_VISUALS = new ArrayList<>();
    private static int spinHitCount;
    private static double spinBudget;

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void slash(SlashBladeEvent.DoSlashEvent event) {
        if (event.getUser() != actor || actor == null) return;
        if (DangakuSpinCombos.SPIN.getId().equals(event.getSlashBladeState().getComboSeq()))
            spinBudget += event.getDamage() * (event.isCritical() ? 1.1F : 1);
        if (event.getSlashBladeState().getComboSeq().equals(ComboStateRegistry.COMBO_B2.getId())) {
            nativeBRushDamage = event.getDamage();
        }
        Phase phase = BranchingStyleCombos.phase(event.getSlashBladeState().getComboSeq());
        if (phase != null) {
            SLASHES.merge(phase, 1, Integer::sum);
            DAMAGE.merge(phase, event.getDamage(), Double::sum);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void circle(EntityJoinLevelEvent event) {
        if (actor != null && event.getEntity() instanceof EntitySlashEffect visual && visual.getShooter() == actor
                && actor.getMainHandItem().getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> DangakuSpinCombos.SPIN.getId().equals(state.getComboSeq())).orElse(false))
            SPIN_VISUALS.add(visual);
        if (actor == null || !(event.getEntity() instanceof EntitySlashEffect slash)
                || slash.getShooter() != actor) return;
        var combo = actor.getMainHandItem().getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq()).orElse(null);
        var phase = BranchingStyleCombos.phase(combo);
        if (DangakuCircleSlashRules.matches(phase)) CIRCLES.add(slash);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void spinHit(SlashBladeEvent.HitEvent event) {
        if (event.getUser() == actor && DangakuSpinCombos.SPIN.getId().equals(event.getSlashBladeState().getComboSeq()))
            spinHitCount++;
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.styleBranchSmoke")) return;
        try {
            var level = event.getServer().overworld();
            var player = FakePlayerFactory.getMinecraft(level);
            actor = player;
            level.addNewPlayer(player);
            var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            player.setItemSlot(EquipmentSlot.MAINHAND, blade);
            var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
            state.setBroken(false); state.setSealed(false);
            var input = player.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
            player.setOnGround(true);
            for (Phase phase : Phase.values()) {
                var node = ComboStateRegistry.REGISTRY.get().getValue(BranchingStyleCombos.id(phase));
                check(node != null, "registered: " + phase);
                check(node.getTimeoutMS() == phase.duration() * 50, "tick duration: " + phase);
                check(node.getMotionLoc().equals(BranchingStyleCombos.source(phase).get().getMotionLoc()), "native animation: " + phase);
            }
            for (BladeStyle style : new BladeStyle[]{BladeStyle.RENGEKI, BladeStyle.DANGAKU}) {
                blade.getOrCreateTag().putString(ModularSlashBladeItem.BLADE_SLOT, style == BladeStyle.RENGEKI
                        ? ModularSlashBladeItem.WAKIZASHI_MODULE : ModularSlashBladeItem.NODACHI_MODULE);
                state.setComboRoot(ModComboStates.getRoot(style));
                state.setComboSeq(ComboStateRegistry.NONE.getId());
                input.getCommands().clear(); input.getCommands().add(InputCommand.R_CLICK);
                check(state.progressCombo(player).equals(style == BladeStyle.RENGEKI
                        ? ComboStateRegistry.COMBO_B1.getId() : BranchingStyleCombos.id(Phase.D_SWEEP)),
                        "native right-click opener: " + style);
            }
            for (BladeStyle style : BladeStyle.values()) {
                state.setComboRoot(ModComboStates.getRoot(style));
                for (boolean grounded : new boolean[]{true, false}) {
                    player.setOnGround(grounded);
                    for (InputCommand direction : new InputCommand[]{InputCommand.FORWARD, InputCommand.BACK}) {
                        if (!grounded && direction == InputCommand.FORWARD) continue;
                        state.setComboSeq(ComboStateRegistry.NONE.getId());
                        input.getCommands().clear();
                        input.getCommands().addAll(EnumSet.of(InputCommand.R_CLICK, InputCommand.SNEAK, direction));
                        var expected = grounded ? (direction == InputCommand.FORWARD
                                ? ComboStateRegistry.RAPID_SLASH : ComboStateRegistry.UPPERSLASH) : ComboStateRegistry.AERIAL_CLEAVE;
                        check(state.progressCombo(player, true).equals(expected.getId()), "common opener: " + style + " " + direction + " " + grounded);
                    }
                }
            }
            player.setOnGround(true);
            input.getCommands().clear(); input.getCommands().add(InputCommand.R_CLICK);
            state.setComboRoot(ModComboStates.DANGAKU_ROOT.getId());
            state.setComboSeq(BranchingStyleCombos.id(Phase.D_RETURN));
            state.setLastActionTime(level.getGameTime() - 5);
            check(state.progressCombo(player, true).equals(BranchingStyleCombos.id(Phase.D_FINISH)), "quick return finishes with Circle Slash");
            state.setLastActionTime(level.getGameTime() - 8);
            check(state.progressCombo(player, true).equals(BranchingStyleCombos.id(Phase.D_HEAVY)), "pause returns heavy");
            state.setComboSeq(BranchingStyleCombos.id(Phase.D_HEAVY));
            state.setLastActionTime(level.getGameTime() - 14);
            input.getCommands().addAll(EnumSet.of(InputCommand.SNEAK, InputCommand.BACK));
            check(state.progressCombo(player, true).equals(ComboStateRegistry.NONE.getId()), "heavy lock defeats root override");
            input.getCommands().clear();
            state.setLastActionTime(level.getGameTime() - Phase.D_HEAVY.minimumTick());
            check(ComboStateRegistry.REGISTRY.get().getValue(state.getComboSeq()).getNext(player)
                    .equals(ComboStateRegistry.NONE.getId()), "long held SA not blocked after commitment");
            check(!state.doChargeAction(player, state.getFullChargeTicks(player) + 1)
                    .equals(ComboStateRegistry.NONE.getId()), "real native charged SA selection remains available");

            for (Phase phase : new Phase[]{Phase.R_FLURRY, Phase.D_HEAVY}) {
                var expected = phase.rengeki() ? ComboStateRegistry.RAPID_SLASH : ComboStateRegistry.UPPERSLASH;
                var direction = phase.rengeki() ? InputCommand.FORWARD : InputCommand.BACK;
                input.getCommands().clear(); input.getCommands().addAll(EnumSet.of(InputCommand.R_CLICK, InputCommand.SNEAK, direction));
                state.setComboSeq(BranchingStyleCombos.id(phase));
                state.setLastActionTime(level.getGameTime() - phase.commonMinimumTick());
                check(state.progressCombo(player, true).equals(expected.getId()), "common move has its own earlier safe cancel window: " + phase);
                state.setComboSeq(BranchingStyleCombos.id(phase));
                state.setLastActionTime(level.getGameTime());
                StyleInputBuffer.queueIfLocked(blade, player, true);
                event.getServer().getWorldData().overworldData().setGameTime(level.getGameTime() + phase.commonMinimumTick());
                StyleInputBuffer.onLivingTick(new LivingEvent.LivingTickEvent(player));
                check(state.getComboSeq().equals(expected.getId()), "early buffered direction resolves native node: " + phase);
            }

            player.setOnGround(false);
            for (BladeStyle style : new BladeStyle[]{BladeStyle.STANDARD, BladeStyle.IAIDO, BladeStyle.RENGEKI}) {
                input.getCommands().clear(); input.getCommands().add(InputCommand.R_CLICK);
                state.setComboRoot(ModComboStates.getRoot(style));
                state.setComboSeq(ComboStateRegistry.NONE.getId());
                check(state.progressCombo(player, true).equals(ComboStateRegistry.AERIAL_RAVE_A1.getId()), "unchanged native air tree: " + style);
            }
            player.setOnGround(true);
            state.setComboRoot(ModComboStates.DANGAKU_ROOT.getId());

            input.getCommands().clear();
            state.setComboSeq(BranchingStyleCombos.id(Phase.D_SWEEP));
            state.setLastActionTime(level.getGameTime());
            StyleInputBuffer.queueIfLocked(blade, player, true);
            state.setLastActionTime(level.getGameTime() - 4);
            // Changing start time cancels a queue even if its combo ID is reused.
            StyleInputBuffer.onLivingTick(new LivingEvent.LivingTickEvent(player));
            check(state.getComboSeq().equals(BranchingStyleCombos.id(Phase.D_SWEEP)), "stale combo start cannot replay queued input");

            // Exercise actual callbacks, not only the pure input selector.
            SLASHES.clear();
            for (Phase phase : new Phase[]{Phase.R_FIRST, Phase.R_SECOND, Phase.R_FLURRY, Phase.R_FINISH,
                    Phase.D_FINISH, Phase.D_HEAVY, Phase.R_RECOVERY, Phase.D_RECOVERY, Phase.D_CIRCLE_RECOVERY}) {
                state.setComboSeq(BranchingStyleCombos.id(phase));
                state.setLastActionTime(level.getGameTime());
                var node = ComboStateRegistry.REGISTRY.get().getValue(state.getComboSeq());
                node.clickAction(player);
                for (int tick = 0; tick < phase.minimumTick(); tick++) {
                    state.setLastActionTime(level.getGameTime() - tick);
                    node.tickAction(player);
                }
            }
            for (var entry : Map.of(Phase.R_FIRST, 1, Phase.R_SECOND, 1, Phase.R_FLURRY, 10,
                    Phase.R_FINISH, 2, Phase.D_FINISH, 4, Phase.D_HEAVY, 2).entrySet()) {
                check(SLASHES.getOrDefault(entry.getKey(), 0).equals(entry.getValue()), "native hit count: " + entry);
            }
            check(SLASHES.getOrDefault(Phase.R_RECOVERY, 0) == 0 && SLASHES.getOrDefault(Phase.D_RECOVERY, 0) == 0
                    && SLASHES.getOrDefault(Phase.D_CIRCLE_RECOVERY, 0) == 0,
                    "recovery has no free extra attacks");

            SLASHES.clear(); DAMAGE.clear();
            player.setOnGround(false);
            for (Phase phase : new Phase[]{Phase.R_AIR_FIRST, Phase.R_AIR_SECOND, Phase.R_AIR_FLURRY,
                    Phase.R_AIR_FINISH, Phase.D_AIR_FIRST, Phase.D_AIR_SECOND, Phase.D_AIR_FINISH,
                    Phase.D_AIR_HEAVY, Phase.R_AIR_RECOVERY, Phase.D_AIR_RECOVERY, Phase.D_AIR_CIRCLE_RECOVERY}) {
                state.setComboSeq(BranchingStyleCombos.id(phase));
                state.setLastActionTime(level.getGameTime());
                player.setDeltaMovement(.2, -.3, .1);
                var node = ComboStateRegistry.REGISTRY.get().getValue(state.getComboSeq());
                node.clickAction(player);
                for (int tick = 0; tick < phase.duration(); tick++) {
                    state.setLastActionTime(level.getGameTime() - tick);
                    node.tickAction(player);
                }
            }
            for (var entry : Map.of(Phase.R_AIR_FIRST, 1, Phase.R_AIR_SECOND, 1, Phase.R_AIR_FLURRY, 10,
                    Phase.R_AIR_FINISH, 2, Phase.D_AIR_FIRST, 1, Phase.D_AIR_SECOND, 1,
                    Phase.D_AIR_FINISH, 4, Phase.D_AIR_HEAVY, 2).entrySet())
                check(SLASHES.getOrDefault(entry.getKey(), 0).equals(entry.getValue()), "finite air hit count: " + entry);
            check(SLASHES.getOrDefault(Phase.R_AIR_RECOVERY, 0) == 0 && SLASHES.getOrDefault(Phase.D_AIR_RECOVERY, 0) == 0
                    && SLASHES.getOrDefault(Phase.D_AIR_CIRCLE_RECOVERY, 0) == 0,
                    "air recovery contains no bonus damage");

            // Real Item.use + inventory ticks, not just direct progressCombo or
            // manually installed nodes: covers repeated clicks and same-airtime restarts.
            for (BladeStyle style : new BladeStyle[]{BladeStyle.RENGEKI, BladeStyle.DANGAKU}) {
                String module = style == BladeStyle.RENGEKI ? "wakizashi_blade" : "nodachi_blade";
                IModularItem.putModuleInSlot(blade, ModularSlashBladeItem.BLADE_SLOT, "slashblade/" + module,
                        "slashblade/" + module + "_material", module + "/iron");
                IModularItem.updateIdentifier(blade);
                ModItems.MODULAR_SLASHBLADE.get().syncDerivedBladeState(blade);
                state.setBroken(false); state.setSealed(false);
                input.getCommands().clear();
                if (style == BladeStyle.RENGEKI) {
                    verifyRestoredRengeki(level, player, blade);
                    player.setOnGround(false);
                    continue;
                }
                Phase[] route = style == BladeStyle.RENGEKI
                        ? new Phase[]{Phase.R_AIR_FIRST, Phase.R_AIR_SECOND, Phase.R_AIR_FLURRY, Phase.R_AIR_FINISH}
                        : new Phase[]{Phase.D_AIR_FIRST, Phase.D_AIR_SECOND, Phase.D_AIR_FINISH};
                for (int round = 0; round < 2; round++) {
                    if (round == 0) state.setComboSeq(ComboStateRegistry.NONE.getId());
                    else check(state.getComboSeq().equals(ComboStateRegistry.NONE.getId()), "previous air chain recovered naturally: " + style);
                    for (Phase phase : route) {
                        rightClick(player, blade);
                        check(state.getComboSeq().equals(BranchingStyleCombos.id(phase)), "real air right-click: " + style + " round=" + round + " " + phase);
                        tickHeldBlade(level, player, blade, phase.minimumTick());
                    }
                    tickHeldBlade(level, player, blade, 40);
                }
                if (style == BladeStyle.DANGAKU) {
                    state.setComboSeq(ComboStateRegistry.NONE.getId());
                    rightClick(player, blade); tickHeldBlade(level, player, blade, Phase.D_AIR_FIRST.minimumTick());
                    rightClick(player, blade); tickHeldBlade(level, player, blade, HEAVY_PAUSE_TICK);
                    rightClick(player, blade);
                    check(state.getComboSeq().equals(BranchingStyleCombos.id(Phase.D_AIR_HEAVY)), "real paused air right-click selects heavy cleave");
                }
                state.setComboSeq(ComboStateRegistry.NONE.getId());
                rightClick(player, blade); tickHeldBlade(level, player, blade, 1);
                rightClick(player, blade);
                check(state.getComboSeq().equals(BranchingStyleCombos.id(route[0])), "early real air click preserves commitment: " + style);
                tickHeldBlade(level, player, blade, route[0].minimumTick() - 1);
                check(state.getComboSeq().equals(BranchingStyleCombos.id(route[1])), "early real air click buffers next cut: " + style);
                // Compare a single tick to the native aerial A1 using identical state.
                state.setComboSeq(BranchingStyleCombos.id(route[0]));
                state.setLastActionTime(level.getGameTime());
                state.setFallDecreaseRate(.4f); player.fallDistance = 10;
                player.setDeltaMovement(.2, -.4, .1);
                ComboStateRegistry.REGISTRY.get().getValue(state.getComboSeq()).tickAction(player);
                double airVelocity = player.getDeltaMovement().y;
                check(player.fallDistance == 1, "native falling protection: " + style);
                state.setComboSeq(ComboStateRegistry.AERIAL_RAVE_A1.getId());
                state.setLastActionTime(level.getGameTime());
                state.setFallDecreaseRate(.4f); player.setDeltaMovement(.2, -.4, .1);
                ComboStateRegistry.AERIAL_RAVE_A1.get().tickAction(player);
                check(Math.abs(airVelocity - player.getDeltaMovement().y) < 1e-9, "same slow falling as native air: " + style);

                Phase[][] pairs = style == BladeStyle.RENGEKI
                        ? new Phase[][]{{Phase.R_FIRST, Phase.R_AIR_FIRST}, {Phase.R_SECOND, Phase.R_AIR_SECOND},
                            {Phase.R_FLURRY, Phase.R_AIR_FLURRY}, {Phase.R_FINISH, Phase.R_AIR_FINISH}}
                        : new Phase[][]{{Phase.D_SWEEP, Phase.D_AIR_FIRST}, {Phase.D_RETURN, Phase.D_AIR_SECOND},
                            {Phase.D_FINISH, Phase.D_AIR_FINISH}, {Phase.D_HEAVY, Phase.D_AIR_HEAVY}};
                for (Phase[] pair : pairs) {
                    var ground = ComboStateRegistry.REGISTRY.get().getValue(BranchingStyleCombos.id(pair[0]));
                    var air = ComboStateRegistry.REGISTRY.get().getValue(BranchingStyleCombos.id(pair[1]));
                    check(ground.getStartFrame() == air.getStartFrame() && ground.getEndFrame() == air.getEndFrame()
                            && ground.getSpeed() == air.getSpeed(), "identical ground/air animation: " + pair[0]);
                    for (Phase phase : pair) {
                        player.setOnGround(!phase.airAttack());
                        DAMAGE.remove(phase);
                        state.setComboSeq(BranchingStyleCombos.id(phase)); state.setLastActionTime(level.getGameTime());
                        var node = ComboStateRegistry.REGISTRY.get().getValue(state.getComboSeq());
                        node.clickAction(player);
                        for (int tick = 0; tick < phase.minimumTick(); tick++) {
                            state.setLastActionTime(level.getGameTime() - tick);
                            node.tickAction(player);
                        }
                    }
                    check(Math.abs(DAMAGE.get(pair[0]) - DAMAGE.get(pair[1])) < 1e-6,
                            "identical ground/air damage tuning: " + pair[0]);
                }
            }
            verifyCircleFinish(level, player, blade);
            player.setOnGround(false);
            state.setComboSeq(ComboStateRegistry.NONE.getId());
            input.getCommands().clear(); input.getCommands().addAll(EnumSet.of(InputCommand.R_CLICK, InputCommand.SNEAK, InputCommand.BACK));
            state.setComboRoot(ModComboStates.DANGAKU_ROOT.getId());
            check(state.progressCombo(player).equals(ComboStateRegistry.AERIAL_CLEAVE.getId()), "native down cleave remains available");
            state.updateComboSeq(player, ComboStateRegistry.UPPERSLASH_JUMP.getId());
            check(state.getComboSeq().equals(ComboStateRegistry.UPPERSLASH_JUMP.getId()), "native follow jump is not canceled by custom budget");
            for (Phase phase : new Phase[]{Phase.R_AIR_FIRST, Phase.R_AIR_FLURRY, Phase.R_AIR_FINISH,
                    Phase.D_AIR_FIRST, Phase.D_AIR_FINISH, Phase.D_AIR_HEAVY}) {
                player.setOnGround(true);
                state.setComboSeq(BranchingStyleCombos.id(phase));
                state.setLastActionTime(level.getGameTime() - (phase == Phase.D_AIR_SECOND ? 5 : 2));
                int before = SLASHES.getOrDefault(phase, 0);
                ComboStateRegistry.REGISTRY.get().getValue(state.getComboSeq()).tickAction(player);
                check(state.getComboSeq().equals(BranchingStyleCombos.id(groundedRecovery(phase))), "landing cancels air windup: " + phase);
                check(SLASHES.getOrDefault(phase, 0) == before, "landing cannot spawn another air slash: " + phase);
            }
            StyleBranchRuntime.tick(new LivingEvent.LivingTickEvent(player));
            player.setOnGround(false);
            state.updateComboSeq(player, ComboStateRegistry.UPPERSLASH_JUMP.getId());
            input.getCommands().clear(); state.setComboSeq(ComboStateRegistry.NONE.getId());
            rightClick(player, blade);
            check(state.getComboSeq().equals(BranchingStyleCombos.id(Phase.D_AIR_FIRST)), "air right-click works after landing and native jump");
            verifyHeldDangaku(level, player, blade);
            LogUtils.getLogger().info("STYLE_BRANCH_SMOKE_PASS: registration, native progression, pause, commitment, real SA, ground/air hit counts, real repeated right-click, native slow falling, landing, ordinary Circle Slash budget and rear hits, restored native Rengeki and half-strength B rush sprint damage; held Dangaku continuous native circles, 40 percent budget, native food/reflection, no stun/knockback, damage does not interrupt, release/air/swap cleanup");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("STYLE_BRANCH_SMOKE_FAIL", failure);
            throw new IllegalStateException("Style branch diagnostic failed", failure);
        } finally {
            actor = null;
            CIRCLES.clear();
            event.getServer().halt(false);
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static void verifyHeldDangaku(ServerLevel level, ServerPlayer player, ItemStack blade) {
        blade.getOrCreateTag().putString(ModularSlashBladeItem.BLADE_SLOT, ModularSlashBladeItem.NODACHI_MODULE);
        var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
        state.setComboRoot(ModComboStates.getRoot(BladeStyle.DANGAKU));
        state.setComboSeq(ComboStateRegistry.NONE.getId()); state.setBroken(false); state.setSealed(false);
        var input = player.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
        input.getCommands().clear(); input.getCommands().add(InputCommand.L_DOWN);
        player.moveTo(0, 100, 0, 0, 0); player.setOnGround(true); player.stopUsingItem();
        DangakuSpinHandler.clear(player);
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        boolean creative = player.getAbilities().instabuild;
        player.getAbilities().instabuild = false;
        var front = circleTarget(level, player, 1.6);
        var rear = circleTarget(level, player, -1.6);
        var arrow = new net.minecraft.world.entity.projectile.Arrow(level, 0, 101, 1);
        arrow.setOwner(front); arrow.setDeltaMovement(.1, 0, -.1); level.addFreshEntity(arrow);
        var arrowMotion = arrow.getDeltaMovement();
        var frontMob = front.getCapability(mods.flammpfeil.slashblade.capability.mobeffect.CapabilityMobEffect.MOB_EFFECT).orElseThrow(IllegalStateException::new);
        long oldStun = level.getGameTime() - 1;
        frontMob.setStunTimeOut(oldStun);
        front.setDeltaMovement(.04, 0, .08);
        var targetMotion = front.getDeltaMovement();
        SPIN_VISUALS.clear(); spinHitCount = 0; spinBudget = 0;
        try {
            spinTicks(level, player, blade, 5);
            check(!DangakuSpinCombos.SPIN.getId().equals(state.getComboSeq()), "short left hold does not enter spin");
            spinTicks(level, player, blade, 1);
            check(DangakuSpinCombos.SPIN.getId().equals(state.getComboSeq()), "native synchronized L_DOWN enters held circle");
            var spinMotion = DangakuSpinCombos.SPIN.get();
            check(spinMotion.getLoop(), "held circle loops visually without an idle gap");
            double animationTicks = Math.abs(spinMotion.getEndFrame() - spinMotion.getStartFrame())
                    / (30D * spinMotion.getSpeed()) * 20D;
            check(Math.abs(animationTicks - DangakuSpinRules.CIRCLE_TICKS) < 1e-6, "held cadence matches native animation duration");
            check(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) / speed - .75) < 1e-6, "spin slows movement by 25 percent");
            float food = player.getFoodData().getExhaustionLevel();
            spinTicks(level, player, blade, 10);
            check(front.getHealth() < 1000 && rear.getHealth() < 1000, "held circle hits front and rear living targets");
            float after = front.getHealth();
            check(spinHitCount > 0 && Math.abs(player.getFoodData().getExhaustionLevel() - food - spinHitCount * .1F) < 1e-4,
                    "held circle consumes the normal native exhaustion per successful hit without extra charges");
            check(frontMob.getStunTimeOut() == oldStun, "held circle does not install or clear native stun");
            check(front.getDeltaMovement().equals(targetMotion), "held circle does not knock back its target");
            check(SPIN_VISUALS.size() == 4, "one held circle retains four native visual segments");
            for (var visual : SPIN_VISUALS)
                check(visual.getShooter() == player && visual.getDamage() > 0, "native circle slash retains its owner and damage path");
            check(Math.abs(spinBudget - DangakuSpinRules.DAMAGE_BUDGET) < 1e-6, "native four-segment circle shares 40 percent of ordinary finisher budget");
            check(!arrow.isAlive() || arrow.getOwner() != front || !arrow.getDeltaMovement().equals(arrowMotion),
                    "native circle still handles hostile arrows normally");
            check(blade.getItem().onLeftClickEntity(blade, player, front), "vanilla left hit suppressed during spin");
            check(blade.getItem().onEntitySwing(blade, player), "vanilla swing suppressed during spin");
            check(CombatBalanceRuntime.kind(player, null) == CombatBalanceRules.AttackKind.ORDINARY, "held circle is ordinary damage not SA");
            // Forge FakePlayer.hurt is always false; exercise the hurt-event route instead.
            var incoming = new net.minecraftforge.event.entity.living.LivingHurtEvent(player, player.damageSources().mobAttack(front), 2);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(incoming);
            check(!incoming.isCanceled() && incoming.getAmount() == 2, "spin grants no damage shield");
            check(DangakuSpinCombos.SPIN.getId().equals(state.getComboSeq()), "incoming damage does not cancel circle");
            spinTicks(level, player, blade, DangakuSpinRules.CIRCLE_TICKS - 10);
            check(DangakuSpinCombos.SPIN.getId().equals(state.getComboSeq()) && ComboState.getElapsed(player) == 0, "held circle repeats immediately at the animation boundary without recovery");
            spinTicks(level, player, blade, 4);
            check(front.getHealth() < after, "next circle uses normal native hit handling");
            input.getCommands().clear();
            spinTicks(level, player, blade, 1);
            check(DangakuSpinCombos.SPIN.getId().equals(state.getComboSeq()), "release finishes current circle rather than immediate animation cut");
            spinTicks(level, player, blade, DangakuSpinRules.CIRCLE_TICKS - 5);
            check(DangakuSpinCombos.RECOVERY.getId().equals(state.getComboSeq()), "release enters harmless recovery");
            check(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(DangakuSpinHandler.SLOW_ID) == null, "release removes movement penalty");
            spinTicks(level, player, blade, 10);
            input.getCommands().add(InputCommand.L_DOWN);
            spinTicks(level, player, blade, 6);
            check(DangakuSpinCombos.SPIN.getId().equals(state.getComboSeq()), "spin may restart after recovery");
            player.setOnGround(false);
            spinTicks(level, player, blade, DangakuSpinRules.CIRCLE_TICKS);
            check(!DangakuSpinCombos.SPIN.getId().equals(state.getComboSeq()), "airborne hold cannot repeat forever");
            player.setOnGround(true); state.setComboSeq(ComboStateRegistry.NONE.getId());
            spinTicks(level, player, blade, 6);
            check(DangakuSpinCombos.SPIN.getId().equals(state.getComboSeq()), "spin restarts after landing");
            input.getCommands().add(InputCommand.R_DOWN);
            spinTicks(level, player, blade, 1);
            check(!DangakuSpinCombos.SPIN.getId().equals(state.getComboSeq()), "right use exits spin for normal skills");
            input.getCommands().clear(); state.setComboSeq(ComboStateRegistry.NONE.getId());
            input.getCommands().add(InputCommand.L_DOWN); spinTicks(level, player, blade, 6);
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            DangakuSpinHandler.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
            check(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(DangakuSpinHandler.SLOW_ID) == null, "swapping away removes slowdown");
            player.setItemSlot(EquipmentSlot.MAINHAND, blade);
            spinTicks(level, player, blade, 1);
            check(!DangakuSpinCombos.SPIN.getId().equals(state.getComboSeq()), "old blade cannot resume unattended spin");
        } finally {
            DangakuSpinHandler.clear(player); input.getCommands().clear();
            front.discard(); rear.discard(); arrow.discard(); SPIN_VISUALS.forEach(EntitySlashEffect::discard); SPIN_VISUALS.clear();
            player.getAbilities().instabuild = creative; player.setItemSlot(EquipmentSlot.MAINHAND, blade);
        }
    }
    private static void spinTicks(ServerLevel level, ServerPlayer player, ItemStack blade, int count) {
        for (int tick = 0; tick < count; tick++) {
            level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime() + 1);
            DangakuSpinHandler.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
            int effectsBefore = SPIN_VISUALS.size();
            blade.getItem().inventoryTick(blade, level, player, 0, true);
            for (int index = effectsBefore; index < SPIN_VISUALS.size(); index++) {
                var slash = SPIN_VISUALS.get(index);
                for (int effectTick = 0; effectTick < 5; effectTick++) slash.tick();
            }
        }
    }
    private static void verifyRestoredRengeki(ServerLevel level, ServerPlayer player, ItemStack blade) {
        var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
        var input = player.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
        player.moveTo(0, 100, 0, 0, 0); player.setYHeadRot(0); player.yBodyRot = 0;
        player.setOnGround(true); player.setSprinting(false); player.stopUsingItem();
        ResourceLocation[] groundRoute = {ComboStateRegistry.COMBO_B1.getId(), ComboStateRegistry.COMBO_B2.getId(),
                ComboStateRegistry.COMBO_B3.getId(), ComboStateRegistry.COMBO_B4.getId(),
                ComboStateRegistry.COMBO_B5.getId(), ComboStateRegistry.COMBO_B6.getId(), ComboStateRegistry.COMBO_B7.getId()};
        state.setComboSeq(ComboStateRegistry.NONE.getId()); input.getCommands().clear();
        nativeBRushDamage = 0;
        for (int beat = 0; beat < groundRoute.length; beat++) {
            rightClick(player, blade);
            check(state.getComboSeq().equals(groundRoute[beat]), "restored native B right-click beat " + (beat + 1));
            tickHeldBlade(level, player, blade, beat == 0 ? 9 : 5);
            check(player.getX() == 0 && player.getZ() == 0, "native B combo never auto-chases a target");
        }
        check(nativeBRushDamage > 0, "native B2 emits its ordinary rush slashes");
        check(Math.abs(RengekiFlowRules.SPRINT_DAMAGE_RATIO - nativeBRushDamage * .5D) < 1e-6,
                "sprint ratio is half the actual native B2 base slash after style tuning");
        tickHeldBlade(level, player, blade, 80);
        player.setOnGround(false);
        for (int round = 0; round < 2; round++) {
            state.setComboSeq(ComboStateRegistry.NONE.getId());
            for (var airNode : new ResourceLocation[]{ComboStateRegistry.AERIAL_RAVE_A1.getId(),
                    ComboStateRegistry.AERIAL_RAVE_A2.getId(), ComboStateRegistry.AERIAL_RAVE_A3.getId()}) {
                rightClick(player, blade);
                check(state.getComboSeq().equals(airNode), "restored native aerial right-click: " + airNode);
                tickHeldBlade(level, player, blade, airNode.equals(ComboStateRegistry.AERIAL_RAVE_A3.getId()) ? 8 : 4);
            }
            tickHeldBlade(level, player, blade, 80);
        }
        state.setComboSeq(ComboStateRegistry.AERIAL_RAVE_A1.getId());
        state.setLastActionTime(level.getGameTime());
        player.setDeltaMovement(.2, -.4, .1); state.setFallDecreaseRate(.4F); player.fallDistance = 10;
        ComboStateRegistry.AERIAL_RAVE_A1.get().tickAction(player);
        check(player.fallDistance == 1 && player.getDeltaMovement().y > -.4, "restored native Rengeki air slow falling");
        // The fall comparison installed a fresh locked A1. Test charging from neutral,
        // not before native A1's own commitment window has elapsed.
        state.setComboSeq(ComboStateRegistry.NONE.getId()); input.getCommands().clear();
        check(!state.doChargeAction(player, state.getFullChargeTicks(player) + 1).equals(ComboStateRegistry.NONE.getId()),
                "restored Rengeki retains charged SA");

        // Exercise neutral sprint through actual player END ticks and native melee.
        state.setComboSeq(ComboStateRegistry.NONE.getId());
        CombatBalanceRuntime.ordinaryCombo(player);
        player.setOnGround(true); player.setSprinting(true); player.stopUsingItem();
        player.setDeltaMovement(.1, 0, .1);
        RengekiRuntimeState.clearMomentum(player.getUUID());
        Zombie target = circleTarget(level, player, 1.8);
        try {
            RengekiMomentumHandler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime() + 1);
            player.setPos(0, 100, .2);
            float before = target.getHealth();
            var momentum = player.getDeltaMovement();
            RengekiMomentumHandler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            check(target.getHealth() < before, "restored sprint actually damages its frontal target");
            check(player.isSprinting() && player.getDeltaMovement().equals(momentum), "sprint hit preserves momentum and sprint flag");
            check(state.getComboSeq().equals(ComboStateRegistry.NONE.getId()), "sprint does not install or advance a combo");
            check(CombatBalanceRuntime.kind(player, null) == CombatBalanceRules.AttackKind.ORDINARY, "sprint is ordinary damage, not previous SA");
            long nextHit = RengekiRuntimeState.sprintChains().get(player.getUUID()).nextHitAt;
            check(nextHit == level.getGameTime() + 4, "sprint preserves four-tick real hit cadence");
            float after = target.getHealth();
            level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime() + 1);
            player.setPos(0, 100, .4);
            RengekiMomentumHandler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            check(target.getHealth() == after, "visual flurry cannot multiply real hit frequency");
            check(player.getX() == 0 && player.getZ() == .4, "sprint never moves the player to a target");
        } finally {
            target.discard(); player.setSprinting(false);
            RengekiRuntimeState.clearMomentum(player.getUUID());
        }
    }
    private static void verifyCircleFinish(ServerLevel level, Player player, ItemStack blade) {
        var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
        player.moveTo(0, 100, 0, 0, 0);
        player.setYHeadRot(0); player.yBodyRot = 0;
        for (Phase phase : new Phase[]{Phase.D_FINISH, Phase.D_AIR_FINISH}) {
            CombatBalanceRuntime.ordinaryCombo(player);
            player.setOnGround(!phase.airAttack());
            CIRCLES.clear();
            state.setComboSeq(BranchingStyleCombos.id(phase));
            state.setLastActionTime(level.getGameTime());
            var node = ComboStateRegistry.REGISTRY.get().getValue(state.getComboSeq());
            check(BranchingStyleCombos.source(phase) == ComboStateRegistry.CIRCLE_SLASH, "native Circle Slash motion: " + phase);
            node.clickAction(player);
            for (int tick = 0; tick < phase.minimumTick(); tick++) {
                state.setLastActionTime(level.getGameTime() - tick);
                node.tickAction(player);
            }
            check(CIRCLES.size() == DangakuCircleSlashRules.SEGMENTS, "four Circle Slash entities: " + phase);
            double totalDamage = 0;
            float[] directions = {180, 90, 0, -90};
            for (int index = 0; index < CIRCLES.size(); index++) {
                EntitySlashEffect slash = CIRCLES.get(index);
                check(Math.abs(slash.getYRot() - (player.getYRot() - 22.5F + directions[index])) < 1e-6,
                        "native four-direction ring: " + phase);
                check(slash.getPersistentData().getString("blade_tetra_balance_kind").equals("ORDINARY"),
                        "borrowed SA is classified as ordinary damage: " + phase);
                totalDamage += slash.getDamage() * (slash.getIsCritical() ? 1.1F : 1.0D);
            }
            check(Math.abs(totalDamage - DangakuCircleSlashRules.FINISH_DAMAGE_BUDGET) < 1e-6,
                    "Circle Slash preserves the old finisher damage budget: " + phase);
            Zombie front = circleTarget(level, player, 1.5);
            Zombie rear = circleTarget(level, player, -1.5);
            try {
                check(!StyleTargeting.isInsideFrontArc(player, rear, 5.25, Math.cos(Math.toRadians(110))),
                        "rear diagnostic target is outside the old sweep gate");
                for (EntitySlashEffect slash : CIRCLES) {
                    for (int tick = 0; tick < 10 && !slash.isRemoved(); tick++) slash.tick();
                }
                check(front.getHealth() < front.getMaxHealth(), "Circle Slash actually hits in front: " + phase);
                check(rear.getHealth() < rear.getMaxHealth(), "Circle Slash actually hits behind: " + phase);
            } finally {
                front.discard(); rear.discard();
                CIRCLES.forEach(EntitySlashEffect::discard);
            }
        }
        CIRCLES.clear();
    }
    private static Zombie circleTarget(ServerLevel level, Player player, double zOffset) {
        Zombie target = new Zombie(level);
        target.moveTo(player.getX(), player.getY(), player.getZ() + zOffset, 0, 0);
        target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000);
        level.addFreshEntity(target);
        return target;
    }
    private static void rightClick(Player player, ItemStack blade) {
        var result = blade.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        check(result.getResult().consumesAction(), "real Item.use accepted right-click");
        player.stopUsingItem();
    }
    private static void tickHeldBlade(ServerLevel level, Player player, ItemStack blade, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime() + 1);
            StyleBranchRuntime.tick(new LivingEvent.LivingTickEvent(player));
            StyleInputBuffer.onLivingTick(new LivingEvent.LivingTickEvent(player));
            blade.getItem().inventoryTick(blade, level, player, 0, true);
        }
    }
    private StyleBranchSmokeTest() {}
}

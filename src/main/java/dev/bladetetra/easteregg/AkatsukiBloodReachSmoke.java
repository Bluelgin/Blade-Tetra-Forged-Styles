package dev.bladetetra.easteregg;

import com.mojang.logging.LogUtils;
import dev.bladetetra.combat.*;
import dev.bladetetra.customstyle.CustomStyleSpec;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;

/** Native melee and Forge style filters with real blood escrow; only used by the server smoke. */
final class AkatsukiBloodReachSmoke {
    static void verify(ServerLevel level, ServerPlayer player, ItemStack blade, int stage) {
        var tag = blade.getTag().copy();
        var data = player.getPersistentData().copy();
        var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
        var combo = state.getComboSeq();
        long entered = state.getLastActionTime();
        float yaw = player.getYRot(), pitch = player.getXRot();
        try {
            player.setYRot(0); player.setXRot(0);
            for (var phase : new StyleBranchRules.Phase[]{StyleBranchRules.Phase.D_SWEEP,
                    StyleBranchRules.Phase.D_RETURN, StyleBranchRules.Phase.D_AIR_FIRST,
                    StyleBranchRules.Phase.D_AIR_SECOND, StyleBranchRules.Phase.D_LAND,
                    StyleBranchRules.Phase.D_HEAVY, StyleBranchRules.Phase.D_AIR_HEAVY,
                    StyleBranchRules.Phase.D_FINISH}) {
                double base = phase == StyleBranchRules.Phase.D_HEAVY
                        || phase == StyleBranchRules.Phase.D_AIR_HEAVY ? 6 : 5.25;
                verifyCase(level, player, blade, ModularSlashBladeItem.NODACHI_MODULE,
                        BranchingStyleCombos.id(phase), base, stage, phase != StyleBranchRules.Phase.D_FINISH);
            }
            verifyCase(level, player, blade, ModularSlashBladeItem.BLADE_MODULE,
                    ModComboStates.IAIDO_FOLLOW.getId(), 3, stage, true);
            verifyCase(level, player, blade, ModularSlashBladeItem.WAKIZASHI_MODULE,
                    ComboStateRegistry.COMBO_B1.getId(), 4.5, stage, false);
            verifyCase(level, player, blade, ModularSlashBladeItem.ORTHODOX_BLADE_MODULE,
                    ComboStateRegistry.COMBO_A1.getId(), 4.5, stage, false);
            for (String module : new String[]{ModularSlashBladeItem.CUSTOM_SHORT_BLADE_MODULE,
                    ModularSlashBladeItem.CUSTOM_STANDARD_BLADE_MODULE, ModularSlashBladeItem.CUSTOM_LONG_BLADE_MODULE}) {
                blade.getOrCreateTag().put(CustomStyleSpec.STACK_KEY, new CustomStyleSpec("Blood reach",
                        java.util.List.of(ModComboStates.IAIDO_FOLLOW.getId(), ModComboStates.DANGAKU_SWEEP.getId())).encode());
                for (var action : new ResourceLocation[]{ModComboStates.IAIDO_FOLLOW.getId(), ModComboStates.DANGAKU_SWEEP.getId()}) {
                    double base = module.equals(ModularSlashBladeItem.CUSTOM_SHORT_BLADE_MODULE) ? 3
                            : module.equals(ModularSlashBladeItem.CUSTOM_LONG_BLADE_MODULE) ? 6 : 4.5;
                    verifyCase(level, player, blade, module, action, base, stage, false);
                }
            }
            verifySprint(level, player, blade, stage);
            LogUtils.getLogger().info("AKATSUKI_BLOOD_REACH_PASS: stage={}, native melee, Dangaku phases, Iaido, Rengeki, standard, custom lengths and recorded actions, angle and SA exclusion", stage);
        } finally {
            var currentTag = blade.getOrCreateTag();
            for (String key : java.util.Set.copyOf(currentTag.getAllKeys())) currentTag.remove(key);
            currentTag.merge(tag); ((ModularSlashBladeItem)blade.getItem()).syncDerivedBladeState(blade);
            state.setComboSeq(combo); state.setLastActionTime(entered);
            player.setYRot(yaw); player.setXRot(pitch);
            var persistent = player.getPersistentData();
            for (String key : java.util.Set.copyOf(persistent.getAllKeys())) persistent.remove(key);
            persistent.merge(data);
        }
    }

    private static void verifyCase(ServerLevel level, ServerPlayer player, ItemStack blade,
            String module, ResourceLocation combo, double base, int stage, boolean frontal) {
        blade.getOrCreateTag().putString(ModularSlashBladeItem.BLADE_SLOT, module);
        ((ModularSlashBladeItem)blade.getItem()).syncDerivedBladeState(blade);
        var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
        state.setComboSeq(combo); state.setLastActionTime(level.getGameTime()); CombatBalanceRuntime.ordinaryCombo(player);
        player.getPersistentData().remove("blade_tetra_iaido_target_until");
        var reach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        var modifiers = blade.getAttributeModifiers(EquipmentSlot.MAINHAND).get(ForgeMod.ENTITY_REACH.get());
        var previous = new java.util.ArrayList<net.minecraft.world.entity.ai.attributes.AttributeModifier>();
        for (var modifier : modifiers) {
            var old = reach.getModifier(modifier.getId());
            if (old != null) previous.add(old);
            reach.removeModifier(modifier); reach.addTransientModifier(modifier);
        }
        var target = new Zombie(level); target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
        level.addFreshEntity(target);
        String context = module + "/" + combo + " stage=" + stage;
        try {
            double distance = base + stage * .75 - .1;
            target.setPos(player.position().add(0, 0, distance));
            check(hit(player, target), "extended native melee hits " + context);
            target.setPos(player.position().add(0, 0, TargetSelector.getResolvedReach(player) + .5));
            check(!hit(player, target), "outside reach misses " + context);
            if (frontal) {
                target.setPos(player.position().add(0, 0, -distance));
                check(!hit(player, target), "rear angle remains excluded " + context + " actualStyle=" + StyleResolver.resolve(player.getMainHandItem()) + " actualCombo=" + state.getComboSeq() + " yaw=" + player.getYRot() + " held=" + (blade == player.getMainHandItem()));
            }
            if (stage > 0) CombatBalanceRuntime.slashArt(player, () -> {
                check(AkatsukiBloodBlade.extraReach(player) == 0, "SA has no blood reach " + context);
                return true;
            });
        } finally {
            target.discard(); modifiers.forEach(reach::removeModifier); previous.forEach(reach::addTransientModifier);
        }
    }

    private static void verifySprint(ServerLevel level, ServerPlayer player, ItemStack blade, int stage) {
        var position = player.position(); var motion = player.getDeltaMovement();
        boolean sprinting = player.isSprinting(), grounded = player.onGround();
        var tick = new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END, player);
        var target = new Zombie(level); target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
        level.addFreshEntity(target);
        try {
            for (boolean inside : new boolean[]{true, false}) {
                // Leaving the style clears its previous movement sample and hit cadence.
                blade.getOrCreateTag().putString(ModularSlashBladeItem.BLADE_SLOT, ModularSlashBladeItem.ORTHODOX_BLADE_MODULE);
                RengekiMomentumHandler.onPlayerTick(tick);
                blade.getOrCreateTag().putString(ModularSlashBladeItem.BLADE_SLOT, ModularSlashBladeItem.WAKIZASHI_MODULE);
                blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new)
                        .setComboSeq(ComboStateRegistry.NONE.getId());
                CombatBalanceRuntime.ordinaryCombo(player);
                player.setOnGround(true); player.setSprinting(true); player.stopUsingItem();
                player.setPos(position); RengekiMomentumHandler.onPlayerTick(tick);
                level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime() + 1);
                player.setPos(position.add(0, 0, .2));
                double range = 1.35 + (2.75 - 1.35) * (.2 - .12) / (.36 - .12) + stage * .75;
                target.setPos(player.position().add(0, 0, range + (inside ? .15 : .75)));
                target.invulnerableTime = 0; float before = target.getHealth();
                RengekiMomentumHandler.onPlayerTick(tick);
                check((target.getHealth() < before) == inside, "sprint actual hit boundary stage=" + stage + " inside=" + inside);
                check(player.isSprinting(), "sprint hit preserves sprinting");
            }
        } finally {
            target.discard(); player.setPos(position); player.setDeltaMovement(motion);
            player.setSprinting(sprinting); player.setOnGround(grounded);
            blade.getOrCreateTag().putString(ModularSlashBladeItem.BLADE_SLOT, ModularSlashBladeItem.ORTHODOX_BLADE_MODULE);
            RengekiMomentumHandler.onPlayerTick(tick);
        }
    }

    private static boolean hit(ServerPlayer player, Zombie target) {
        player.getMainHandItem().getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(state -> state.setLastActionTime(player.level().getGameTime()));
        target.invulnerableTime = 0; float before = target.getHealth();
        AttackManager.areaAttack(player, ignored -> {}, 1F, true, true, true);
        return target.getHealth() < before;
    }
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    private AkatsukiBloodReachSmoke() {}
}

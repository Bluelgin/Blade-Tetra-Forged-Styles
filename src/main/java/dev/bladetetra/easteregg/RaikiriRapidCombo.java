package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.ModComboStates;
import mods.flammpfeil.slashblade.ability.StunManager;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumSet;

/**
 * Native Shift+Forward+Right Click upgrade for an awakened Raikiri.
 *
 * This is ONE combo node, not a second artificial Slash Art. It inherits
 * SlashBlade's rapid-slash frames, hold/hit callbacks and native hit rhythm,
 * replacing only the built-in unrestricted forward movement with one
 * server-authoritative, collision-checked Thunder Step.
 *
 * If there is no affordable/valid step, do not intercept the vanilla node.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RaikiriRapidCombo {
    public static final RegistryObject<ComboState> THUNDER_RAPID =
            ModComboStates.COMBOS.register("awakened_raikiri_rapid", RaikiriRapidCombo::create);

    public static void register() { /* Force deferred registration before mod bus attaches. */ }

    public static boolean isThunderCombo(ResourceLocation combo) {
        return THUNDER_RAPID.getId().equals(combo);
    }

    public static boolean directionalIntent(LivingEntity user) {
        return user.onGround() && user.getCapability(CapabilityInputState.INPUT_STATE)
                .map(state -> state.getCommands(user).containsAll(EnumSet.of(
                        InputCommand.SNEAK, InputCommand.FORWARD, InputCommand.R_CLICK)))
                .orElse(false);
    }

    @SubscribeEvent
    public static void onNextCombo(SlashBladeEvent.NextComboEvent event) {
        if (!(event.getUser() instanceof ServerPlayer player)
                || !AwakenedBladeCombat.replacesLegacy()
                || !ComboStateRegistry.RAPID_SLASH.getId().equals(event.getNextCombo())
                || !directionalIntent(player)
                || !SoulLegacyState.isActive(event.getBlade(), SoulLegacyState.Legacy.RAIKIRI)
                || SoulLegacyState.active(event.getBlade()) != SoulLegacyState.Legacy.RAIKIRI)
            return;
        // Method performs validation, native Rank debit, collision-safe movement
        // and step VFX once, before the combo state is committed.
        if (AwakenedBladeCombat.beginRaikiriRapid(player, event.getBlade()))
            event.setNextCombo(THUNDER_RAPID.getId());
    }

    private static ComboState create() {
        ComboState original = ComboStateRegistry.RAPID_SLASH.get();
        return ComboState.Builder.newInstance()
                .startAndEnd(original.getStartFrame(), original.getEndFrame())
                .priority(original.getPriority())
                .speed(original.getSpeed())
                .timeout(original.timeout)
                .motionLoc(original.getMotionLoc())
                .next(entity -> ComboStateRegistry.RAPID_SLASH.getId())
                .nextOfTimeout(entity -> ComboStateRegistry.RAPID_SLASH_END.getId())
                .addHoldAction(original::holdAction)
                .addHitEffect(StunManager::setStun)
                .clickAction(original::clickAction)
                .releaseAction(original::releaseAction)
                .addTickAction(entity -> {
                    long elapsed = ComboState.getElapsed(entity);
                    if (elapsed == 0) {
                        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                                SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.8F, 1.3F);
                    }
                    // Identical native attack cadence, without the native 0-3t
                    // uncontrolled moveRelative dash. The server already moved
                    // once in beginRaikiriRapid before entering this node.
                    if (2 <= elapsed && elapsed < 6) {
                        float roll = -45 + 90 * entity.getRandom().nextFloat();
                        if (elapsed % 2 == 0) roll += 180;
                        AttackManager.doSlash(entity, roll, AttackManager.genRushOffset(entity),
                                false, AttackManager.isPowered(entity), 0.44F);
                    }
                    if (elapsed == 7) {
                        AttackManager.doSlash(entity, -30, AttackManager.genRushOffset(entity),
                                false, true, 0.44F);
                    }
                })
                .build();
    }

    private RaikiriRapidCombo() {}
}

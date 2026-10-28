package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.ModComboStates;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
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
import net.minecraftforge.eventbus.api.EventPriority;
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
    /** Marks only native slashes actually authored by this specialized combo. */
    public static final String THUNDER_HIT = "blade_tetra_raikiri_rapid_hit";

    public static final RegistryObject<ComboState> THUNDER_RAPID =
            ModComboStates.COMBOS.register("awakened_raikiri_rapid", RaikiriRapidCombo::create);

    public static final RegistryObject<ComboState> THUNDER_RAPID_SECOND =
            ModComboStates.COMBOS.register("awakened_raikiri_rapid_second", RaikiriRapidCombo::create);

    public static void register() { /* Force deferred registration before mod bus attaches. */ }

    public static boolean isThunderCombo(ResourceLocation combo) {
        return THUNDER_RAPID.getId().equals(combo) || THUNDER_RAPID_SECOND.getId().equals(combo);
    }

    public static boolean directionalIntent(LivingEntity user) {
        return user.onGround() && user.getCapability(CapabilityInputState.INPUT_STATE)
                .map(state -> state.getCommands(user).containsAll(EnumSet.of(
                        InputCommand.SNEAK, InputCommand.FORWARD, InputCommand.R_CLICK)))
                .orElse(false);
    }

    // Let other style routers decide first; upgrade only the final native
    // Rapid Slash candidate rather than overwriting their explicit choices.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onNextCombo(SlashBladeEvent.NextComboEvent event) {
        if (!(event.getUser() instanceof ServerPlayer player)
                || !AwakenedBladeCombat.replacesLegacy()
                || !directionalIntent(player)
                || !SoulLegacyState.isActive(event.getBlade(), SoulLegacyState.Legacy.RAIKIRI)
                || SoulLegacyState.active(event.getBlade()) != SoulLegacyState.Legacy.RAIKIRI)
            return;
        ResourceLocation candidate=event.getNextCombo();
        if (!ComboStateRegistry.RAPID_SLASH.getId().equals(candidate)) {
            // On the very first input 1.9.63 posts NONE.next == NONE, then
            // resolves the weapon root AFTER this event. Upgrade only when
            // that exact native fallback is Rapid Slash; preserve explicit
            // style nodes and native self-next locks.
            if (!ComboStateRegistry.NONE.getId().equals(candidate)
                    || !ComboStateRegistry.NONE.getId().equals(
                            event.getSlashBladeState().resolvCurrentComboState(player))) return;
            ComboState root=ComboStateRegistry.REGISTRY.get().getValue(
                    event.getSlashBladeState().getComboRoot());
            if (root==null || !ComboStateRegistry.RAPID_SLASH.getId().equals(root.getNext(player))) return;
        }
        // Virtual progression and rejected/mutated transitions have no side effects.
        if (RaikiriStepController.canRoute(player, event.getBlade())) {
            ResourceLocation current = event.getBlade().getCapability(
                    dev.bladetetra.item.ModularSlashBladeItem.BLADESTATE)
                    .map(state -> state.getComboSeq()).orElse(null);
            // Respect the native self-next guard while allowing real second/third steps.
            event.setNextCombo(THUNDER_RAPID.getId().equals(current)
                    ? THUNDER_RAPID_SECOND.getId() : THUNDER_RAPID.getId());
        }
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
                .addHitEffect(original::hitEffect)
                .clickAction(entity -> {
                    original.clickAction(entity);
                    if (entity instanceof ServerPlayer player) RaikiriStepController.committed(player);
                })
                .releaseAction(original::releaseAction)
                .addTickAction(entity -> {
                    long elapsed = ComboState.getElapsed(entity);
                    if (elapsed == 0) {
                        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                                SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.8F, 1.3F);
                    }
                    // Audited 1.9.63 native cadence; only unrestricted movement is replaced.
                    if (2 <= elapsed && elapsed < 6) {
                        float roll = -45 + 90 * entity.getRandom().nextFloat();
                        if (elapsed % 2 == 0) roll += 180;
                        EntitySlashEffect slash = AttackManager.doSlash(entity, roll,
                                AttackManager.genRushOffset(entity),
                                false, AttackManager.isPowered(entity), 0.44F);
                        if (entity instanceof ServerPlayer player) RaikiriStepController.tag(slash, player);
                    }
                    if (elapsed == 7) {
                        EntitySlashEffect slash = AttackManager.doSlash(entity, -30,
                                AttackManager.genRushOffset(entity), false, true, 0.44F);
                        if (entity instanceof ServerPlayer player) RaikiriStepController.tag(slash, player);
                    }
                    if (7 <= elapsed && elapsed <= 10) UserPoseOverrider.setRot(entity, 90, true);
                    if (elapsed > 10) UserPoseOverrider.setRot(entity, 0, false);
                })
                .build();
    }

    private RaikiriRapidCombo() {}
}

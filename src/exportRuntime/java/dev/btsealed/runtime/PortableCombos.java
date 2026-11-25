package dev.btsealed.runtime;

import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Uses Resharped's own attack callbacks and motion timelines for supported styles. */
final class PortableCombos {
    static final DeferredRegister<ComboState> COMBOS =
            DeferredRegister.create(ComboState.REGISTRY_KEY, PortableBladeRuntime.ID);
    private static final Map<ResourceLocation, List<ResourceLocation>> STEPS = new HashMap<>();
    private static final Map<ResourceLocation, Integer> POSITIONS = new HashMap<>();

    static final RegistryObject<ComboState> RENGEKI = COMBOS.register("rengeki_root",
            () -> root(user -> open(user, ComboStateRegistry.COMBO_B1.getId())));
    static final RegistryObject<ComboState> IAIDO = COMBOS.register("iaido_root",
            () -> root(user -> open(user, id("iaido_draw"))));
    static final RegistryObject<ComboState> DANGAKU = COMBOS.register("dangaku_root",
            () -> root(PortableDangaku::opener));
    static final RegistryObject<ComboState> CUSTOM = COMBOS.register("custom_root",
            () -> root(PortableCombos::customOpener));

    static {
        PortableDangaku.register();
        iaido("iaido_draw", ComboStateRegistry.COMBO_C, 15, "iaido_return", ComboStateRegistry.COMBO_C_END.getId());
        iaido("iaido_follow", ComboStateRegistry.COMBO_A1, 5, "iaido_return", ComboStateRegistry.COMBO_A1_END.getId());
        iaido("iaido_return", ComboStateRegistry.COMBO_A2, 5, "iaido_sheathe", id("iaido_sheathe"));
        iaido("iaido_finish", ComboStateRegistry.COMBO_A3, 9, "iaido_draw", ComboStateRegistry.COMBO_A3_END.getId());
        iaido("iaido_sheathe", ComboStateRegistry.COMBO_C_END, 7, "iaido_draw", ComboStateRegistry.NONE.getId());
        for (var move : List.of(ComboStateRegistry.COMBO_A1, ComboStateRegistry.COMBO_A2,
                ComboStateRegistry.COMBO_A3, ComboStateRegistry.COMBO_A4, ComboStateRegistry.COMBO_A4_EX,
                ComboStateRegistry.COMBO_A5, ComboStateRegistry.COMBO_C, ComboStateRegistry.COMBO_B1,
                ComboStateRegistry.COMBO_B2, ComboStateRegistry.COMBO_B3, ComboStateRegistry.COMBO_B4,
                ComboStateRegistry.COMBO_B5, ComboStateRegistry.COMBO_B6, ComboStateRegistry.COMBO_B7,
                ComboStateRegistry.UPPERSLASH, ComboStateRegistry.RAPID_SLASH, ComboStateRegistry.RAPID_SLASH_QUICK)) {
            var positions = new java.util.ArrayList<ResourceLocation>(7);
            for (int index = 0; index < 7; index++) {
                String name = "sequence_" + index + "_" + move.getId().getPath();
                var entry = COMBOS.register(name, () -> copy(move.get(), PortableCombos::noNext,
                        move.get()::getNextOfTimeout));
                positions.add(entry.getId());
                POSITIONS.put(entry.getId(), index);
            }
            STEPS.put(move.getId(), List.copyOf(positions));
        }
    }

    private static void iaido(String name, RegistryObject<ComboState> motion,
            int gateFrame, String target, ResourceLocation timeout) {
        COMBOS.register(name, () -> copy(motion.get(), ComboState.TimeoutNext.buildFromFrame(
                gateFrame, user -> open(user, id(target))), user -> timeout));
    }

    static boolean isIaidoDraw(ResourceLocation combo) {
        return id("iaido_draw").equals(combo);
    }

    static boolean hasMove(ResourceLocation action) {
        return STEPS.containsKey(action);
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(PortableBladeRuntime.ID, name);
    }

    private static ComboState root(Function<LivingEntity, ResourceLocation> next) {
        return ComboState.Builder.newInstance().startAndEnd(0, 1).loop()
                .motionLoc(DefaultResources.ExMotionLocation).next(next)
                .nextOfTimeout(PortableCombos::noNext).build();
    }

    private static ComboState copy(ComboState nativeState, Function<LivingEntity, ResourceLocation> next,
            Function<LivingEntity, ResourceLocation> timeout) {
        var builder = ComboState.Builder.newInstance()
                .startAndEnd(nativeState.getStartFrame(), nativeState.getEndFrame())
                .priority(nativeState.getPriority()).speed(nativeState.getSpeed()).timeout(nativeState.timeout)
                .motionLoc(nativeState.getMotionLoc()).next(next).nextOfTimeout(timeout)
                .addHoldAction(nativeState::holdAction).addTickAction(nativeState::tickAction)
                .addHitEffect(nativeState::hitEffect).clickAction(nativeState::clickAction)
                .releaseAction(nativeState::releaseAction);
        if (nativeState.getLoop()) builder.loop();
        if (nativeState.isAerial()) builder.aerial();
        return builder.build();
    }

    private static ResourceLocation noNext(LivingEntity user) {
        return ComboStateRegistry.NONE.getId();
    }

    private static ResourceLocation open(LivingEntity user, ResourceLocation ordinary) {
        EnumSet<InputCommand> commands = user.getCapability(CapabilityInputState.INPUT_STATE)
                .map(state -> state.getCommands(user)).orElseGet(() -> EnumSet.noneOf(InputCommand.class));
        boolean right = commands.contains(InputCommand.R_CLICK);
        boolean sneak = commands.contains(InputCommand.SNEAK);
        boolean back = commands.contains(InputCommand.BACK);
        if (commands.contains(InputCommand.ON_AIR)) {
            return right && sneak && back ? ComboStateRegistry.AERIAL_CLEAVE.getId()
                    : ComboStateRegistry.AERIAL_RAVE_A1.getId();
        }
        if (right && sneak && commands.contains(InputCommand.FORWARD)) return ComboStateRegistry.RAPID_SLASH.getId();
        if (right && sneak && back) return ComboStateRegistry.UPPERSLASH.getId();
        return right || commands.contains(InputCommand.L_CLICK) ? ordinary : noNext(user);
    }

    private static ResourceLocation customOpener(LivingEntity user) {
        var special = open(user, noNext(user));
        if (!special.equals(noNext(user))) return special;
        var profile = profile(user);
        var state = user.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        if (profile == null || state == null || profile.moves().isEmpty()
                || POSITIONS.containsKey(state.resolvCurrentComboState(user))) return noNext(user);
        return open(user, step(profile, 0));
    }

    static void next(SlashBladeEvent.NextComboEvent event) {
        ISlashBladeState state = event.getSlashBladeState();
        if (!CUSTOM.getId().equals(state.getComboRoot())) return;
        LivingEntity user = event.getUser();
        var current = state.resolvCurrentComboState(user);
        var position = POSITIONS.get(current);
        var profile = profile(user);
        if (position == null || profile == null || profile.moves().isEmpty()) return;
        var special = open(user, noNext(user));
        if (!special.equals(noNext(user))) {
            event.setNextCombo(special);
            return;
        }
        int count = profile.moves().size();
        int move = position == 6 ? 0 : position;
        if (move >= count) return;
        long elapsed = user.level().getGameTime() - state.getLastActionTime();
        if (elapsed < profile.moves().get(move).minimumTicks()) {
            event.setNextCombo(current);
            return;
        }
        int next = count == 1 && position == 0 ? 6 : (move + 1) % count;
        event.setNextCombo(open(user, step(profile, next)));
    }

    private static ResourceLocation step(PortableProfiles.Profile profile, int position) {
        var action = profile.moves().get(position == 6 ? 0 : position).action();
        return STEPS.get(action).get(position);
    }

    private static PortableProfiles.Profile profile(LivingEntity user) {
        return PortableProfiles.forBlade(user.getMainHandItem(),
                user.level().isClientSide() ? LogicalSide.CLIENT : LogicalSide.SERVER);
    }

    private PortableCombos() {}
}

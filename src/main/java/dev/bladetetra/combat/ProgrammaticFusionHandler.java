package dev.bladetetra.combat;

import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import java.util.function.Function;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;

/** Tetra plan adapter for the shared native fusion lifecycle. */
public final class ProgrammaticFusionHandler {
    private static volatile Function<ItemStack, List<ResourceLocation>> sealedPlans = stack -> List.of();
    private static final NativeFusionEngine ENGINE = new NativeFusionEngine(ProgrammaticFusionHandler::plan,
            Map.of(SealedFusionSources.SOURCE_DISPATCHER, ModSlashBladeAbilities.PROGRAMMATIC_FUSION,
                   SealedFusionSources.DISPATCHER,
                   () -> SlashArtsRegistry.REGISTRY.get().getValue(SealedFusionSources.DISPATCHER)));

    private ProgrammaticFusionHandler() {}

    public static void processInterop(InterModProcessEvent event) {
        event.getIMCStream(SealedFusionSources.INTEROP::equals)
                .filter(message -> "bt_export_runtime".equals(message.senderModId()))
                .forEach(message -> installSealedPlans(message.messageSupplier().get()));
    }

    @SuppressWarnings("unchecked")
    private static void installSealedPlans(Object resolver) {
        if (!(resolver instanceof Function<?, ?> function)) {
            throw new IllegalArgumentException("Invalid sealed fusion profile resolver");
        }
        sealedPlans = (Function<ItemStack, List<ResourceLocation>>) function;
    }

    private static NativeFusionEngine.Pair plan(ItemStack blade) {
        boolean sealed = blade.getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> SealedFusionSources.DISPATCHER.equals(state.getSlashArtsKey())).orElse(false);
        if (sealed) {
            var ids = sealedPlans.apply(blade);
            return ids.size() == 2 ? new NativeFusionEngine.Pair(ids.get(0), ids.get(1)) : null;
        }
        var plan = ProgrammaticFusionPlan.from(blade);
        return plan == null ? null : new NativeFusionEngine.Pair(plan.releaseAbility(), plan.responseAbility());
    }

    public static boolean available(ResourceLocation id) { return NativeFusionEngine.available(id); }
    public static ResourceLocation normalCombo(LivingEntity user) { return ENGINE.normalCombo(user); }
    public static ResourceLocation justCombo(LivingEntity user) { return ENGINE.justCombo(user); }

    public static ResourceLocation charge(ISlashBladeState state, LivingEntity user, int elapsed,
                                          Supplier<ResourceLocation> nativeCharge) {
        return ENGINE.charge(state, user, elapsed, nativeCharge);
    }

    public static ResourceLocation releaseSuper(SlashArts art, SlashArts.ArtsType type, LivingEntity user) {
        return ENGINE.releaseSuper(art, type, user);
    }

    public static SlashArts contextualArt(ISlashBladeState state) {
        SlashArts source = ENGINE.contextualArt(state);
        if (source != null) return source;
        // With both mods installed the Tetra hook owns the native release. Both registered
        // dispatchers have the same cost and selectors, and resolve against this session.
        if (SealedFusionSources.DISPATCHER.equals(state.getSlashArtsKey()) && ENGINE.releasing(state)) {
            return ModSlashBladeAbilities.PROGRAMMATIC_FUSION.get();
        }
        return null;
    }

    public static Map.Entry<Integer, ResourceLocation> resolveTimeout(ISlashBladeState state, LivingEntity user,
                                                                      Supplier<Map.Entry<Integer, ResourceLocation>> resolve) {
        return ENGINE.resolveTimeout(state, user, resolve);
    }

    public static void motion(ISlashBladeState state, LivingEntity user, ResourceLocation requested, Runnable nativeMotion) {
        ENGINE.motion(state, user, requested, nativeMotion);
    }

    public static void tickAction(ComboState combo, LivingEntity user, Runnable nativeTick) {
        ENGINE.tickAction(combo, user, nativeTick);
    }

    public static int timelineFrame(LivingEntity user) { return ENGINE.timelineFrame(user); }
    static void observeRelease(SlashBladeEvent.PerformSlashArtEvent event) { ENGINE.observeRelease(event); }
    static void observeTimeout(SlashBladeEvent.NextOfTimeOutComboEvent event) { ENGINE.observeTimeout(event); }
    static void observeMotion(BladeMotionEvent event) { ENGINE.observeMotion(event); }
    static void tick(TickEvent.ServerTickEvent event) { ENGINE.tick(event); }
    static void removePlayer(ServerPlayer player, String reason) { ENGINE.removePlayer(player, reason); }
    static void onLevelUnload(ServerLevel level) { ENGINE.onLevelUnload(level); }
    static void clear() { ENGINE.clear(); }
}

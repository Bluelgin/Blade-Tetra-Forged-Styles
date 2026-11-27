package dev.btsealed.runtime;

import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class PortableFusion {
    private static final DeferredRegister<SlashArts> ARTS =
            DeferredRegister.create(SlashArts.REGISTRY_KEY, PortableBladeRuntime.ID);
    private static final RegistryObject<SlashArts> DISPATCHER = ARTS.register(SealedFusionSources.DISPATCHER.getPath(),
            () -> new SlashArts(PortableFusion::normal).setComboStateJust(PortableFusion::just)
                    .setComboStateSuper(user -> ComboStateRegistry.NONE.getId())
                    .setProudSoulCost(SealedFusionSources.RELEASE_COST));
    public static final NativeFusionEngine ENGINE = new NativeFusionEngine(PortableFusion::plan,
            Map.of(SealedFusionSources.DISPATCHER, DISPATCHER));

    private PortableFusion() {}

    private static ResourceLocation normal(net.minecraft.world.entity.LivingEntity user) {
        return ENGINE.normalCombo(user);
    }

    private static ResourceLocation just(net.minecraft.world.entity.LivingEntity user) {
        return ENGINE.justCombo(user);
    }

    private static NativeFusionEngine.Pair plan(ItemStack stack) {
        var profile = PortableProfiles.forBlade(stack, LogicalSide.SERVER);
        return profile == null ? null : profile.fusion();
    }

    static void register(IEventBus modBus) {
        ARTS.register(modBus);
        if (ModList.get().isLoaded("blade_tetra")) {
            modBus.addListener(PortableFusion::shareProfiles);
            return;
        }
        var events = MinecraftForge.EVENT_BUS;
        events.addListener(EventPriority.HIGHEST, true, SlashBladeEvent.PerformSlashArtEvent.class, ENGINE::observeRelease);
        events.addListener(EventPriority.HIGHEST, true, SlashBladeEvent.NextOfTimeOutComboEvent.class, ENGINE::observeTimeout);
        events.addListener(EventPriority.HIGHEST, true, BladeMotionEvent.class, ENGINE::observeMotion);
        events.addListener(PortableFusion::tick);
        events.addListener(PortableFusion::logout);
        events.addListener(PortableFusion::dimension);
        events.addListener(EventPriority.LOWEST, PortableFusion::death);
        events.addListener(PortableFusion::unload);
        events.addListener(PortableFusion::stop);
    }

    private static void shareProfiles(InterModEnqueueEvent event) {
        Function<ItemStack, List<ResourceLocation>> resolver = stack -> {
            var pair = plan(stack);
            return pair == null ? List.of() : List.of(pair.releaseAbility(), pair.responseAbility());
        };
        InterModComms.sendTo("blade_tetra", SealedFusionSources.INTEROP, () -> resolver);
    }

    private static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ENGINE.tick(event);
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ENGINE.removePlayer(player, "logged out");
    }

    private static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ENGINE.removePlayer(player, "changed dimension");
    }

    private static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ENGINE.removePlayer(player, "died");
    }

    private static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) ENGINE.onLevelUnload(level);
    }

    private static void stop(ServerStoppedEvent event) {
        ENGINE.clear();
    }
}

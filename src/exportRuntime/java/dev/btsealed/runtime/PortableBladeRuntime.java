package dev.btsealed.runtime;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(PortableBladeRuntime.ID)
public final class PortableBladeRuntime {
    public static final String ID = "bt_export_runtime";

    public PortableBladeRuntime(FMLJavaModLoadingContext context) {
        PortableCombos.COMBOS.register(context.getModEventBus());
        PortableFusion.register(context.getModEventBus());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> PortableBackplateClient::register);
        var eventBus = MinecraftForge.EVENT_BUS;
        eventBus.addListener(PortableBladeRuntime::reload);
        eventBus.addListener(PortableBladeCommands::register);
        eventBus.addListener(PortableBladeRuntime::attributes);
        eventBus.addListener(PortableCombos::next);
        eventBus.addListener(PortableDangaku::slash);
        eventBus.addListener(PortableDangaku::slashGeometry);
        eventBus.addListener(EventPriority.HIGHEST, PortableDangaku::landing);
        eventBus.addListener(EventPriority.HIGH, PortableDangaku::target);
        eventBus.addListener(EventPriority.HIGH, PortableDangaku::armor);
        eventBus.addListener(EventPriority.HIGH, PortableDangaku::knockback);
        eventBus.addListener(EventPriority.HIGHEST, PortableDangakuSpin::tick);
        eventBus.addListener(EventPriority.HIGHEST, PortableDangakuSpin::attack);
        eventBus.addListener(PortableDangakuSpin::logout);
        eventBus.addListener(PortableDangakuSpin::dimension);
        eventBus.addListener(PortableDangakuSpin::clone);
        eventBus.addListener(PortableDangakuSpin::stop);
        eventBus.addListener(EventPriority.LOW, PortableComponentEffects::slash);
        eventBus.addListener(PortableComponentEffects::arts);
        eventBus.addListener(PortableComponentEffects::hit);
        eventBus.addListener(PortableComponentEffects::guard);
        eventBus.addListener(EventPriority.LOWEST, PortableComponentEffects::fishFeedback);
        eventBus.addListener(PortableRankRetention::tick);
        eventBus.addListener(EventPriority.LOWEST, PortableHabakiKnockback::slashSpawn);
    }

    private static void reload(AddReloadListenerEvent event) {
        event.addListener(PortableProfiles.serverListener());
    }

    private static void attributes(ItemAttributeModifierEvent event) {
        if (event.getSlotType() != EquipmentSlot.MAINHAND) return;
        var profile = PortableProfiles.forAttributes(event.getItemStack());
        if (profile == null || profile.modifiers().isEmpty()) return;

        // Remove exactly the modifiers supplied by the native item itself.
        event.getOriginalModifiers().entries().forEach(entry ->
                event.removeModifier(entry.getKey(), entry.getValue()));
        profile.modifiers().forEach(entry -> event.addModifier(entry.attribute(), entry.modifier()));
    }
}

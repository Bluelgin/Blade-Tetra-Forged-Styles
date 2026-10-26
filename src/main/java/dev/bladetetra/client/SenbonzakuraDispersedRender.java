package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.easteregg.SoulLegacyState;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The original Tetra sword and sheath are never destroyed or changed in NBT.
 * Only their combat render groups are replaced by a small neutral hilt mesh.
 * Native item icons, durability overlays and all other render targets survive.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SenbonzakuraDispersedRender {
    private static final String STANCE = "blade_tetra_awakened_v2_sakura_dispersed";
    private static final ResourceLocation HILT = new ResourceLocation(
            BladeTetra.MOD_ID, "model/sakura_hilt.obj");

    @SubscribeEvent
    public static void onRenderOverride(RenderOverrideEvent event) {
        if (!GameplayConfig.ENABLE_AWAKENED_V2.get()) return;
        ItemStack blade = event.getStack();
        CompoundTag tag = blade.getTag();
        if (tag == null || !tag.getBoolean(STANCE)
                || StyleResolver.resolve(blade) != BladeStyle.RENGEKI
                || !SoulLegacyState.isActive(blade, SoulLegacyState.Legacy.SENBONZAKURA))
            return;
        String target = event.getTarget();
        if ("blade".equals(target) || "blade_damaged".equals(target)) {
            // Render a physical handle instead of hiding the entire sword;
            // the native model/texture return automatically in condensed form.
            event.setModel(BladeModelManager.getInstance().getModel(HILT));
            event.setTarget("blade");
        } else if ("blade_luminous".equals(target)
                || "blade_damaged_luminous".equals(target)
                || "sheath".equals(target) || "sheath_luminous".equals(target)) {
            event.setCanceled(true);
        }
    }

    private SenbonzakuraDispersedRender() {}
}

package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.easteregg.SoulLegacyState;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Real Tetra/SlashBlade body transformation using SlashBlade's own render
 * override hook. The native sheath and item inventory icon remain visible.
 * Only the sword blade geometry is suppressed in dispersed combat stance;
 * its detached petals are rendered by AwakenedBladeVfxClient.
 *
 * This works with custom blade materials and the original OBJ model: no
 * global replacement texture and no fixed third-party model assumptions.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SenbonzakuraDispersedRender {
    private static final String STANCE = "blade_tetra_awakened_v2_sakura_dispersed";

    @SubscribeEvent
    public static void onRenderOverride(RenderOverrideEvent event) {
        if (!GameplayConfig.ENABLE_AWAKENED_V2.get()) return;
        ItemStack blade = event.getStack();
        CompoundTag tag = blade.getTag();
        if (tag == null || !tag.getBoolean(STANCE)
                || !SoulLegacyState.isActive(blade, SoulLegacyState.Legacy.SENBONZAKURA))
            return;
        String target = event.getTarget();
        // Do not hide a blade's item icon, sheath, broken/durability overlay
        // or special GUI model. Only suppress combat body and luminous group.
        if ("blade".equals(target) || "blade_luminous".equals(target)
                || "blade_damaged".equals(target)
                || "blade_damaged_luminous".equals(target)) {
            event.setCanceled(true);
        }
    }

    private SenbonzakuraDispersedRender() {}
}

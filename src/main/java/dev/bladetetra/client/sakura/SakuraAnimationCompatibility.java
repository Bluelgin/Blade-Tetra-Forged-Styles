package dev.bladetetra.client.sakura;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.easteregg.SakuraSixthCombo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Optional public upstream animation-map alias; native MMD works without Player Animator. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SakuraAnimationCompatibility {
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(()-> {if(ModList.get().isLoaded("playeranimator")) Native.install();});
    }
    private static final class Native {
        static void install() {
            var animations=mods.flammpfeil.slashblade.compat.playerAnim.PlayerAnimationOverrider
                    .getInstance().getAnimation();
            var original=animations.get(mods.flammpfeil.slashblade.registry.ComboStateRegistry.COMBO_B6.getId());
            if(original!=null) animations.put(SakuraSixthCombo.SIXTH.getId(),original);
        }
    }
    private SakuraAnimationCompatibility() {}
}

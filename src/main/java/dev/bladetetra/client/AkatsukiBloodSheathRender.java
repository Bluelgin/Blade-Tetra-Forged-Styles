package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.client.awakening.*;
import dev.bladetetra.easteregg.AkatsukiBloodBlade;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Alpha wraps the existing material renderer; inventory/ritual/nested passes remain independent. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID,value=Dist.CLIENT)
public final class AkatsukiBloodSheathRender {
    private static boolean fading;
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void render(RenderOverrideEvent e) {
        if(fading||MaterialTextureManager.internalPass()||FloatingBladeRenderer.isRendering()
                ||!AwakeningVisibility.isSheathTarget(e.getOriginalTarget()))return;
        var p=SenbonzakuraDispersedRender.owner(e.getStack());
        if(p==null||AwakeningVisualController.presenting(p))return;
        float alpha=AkatsukiBloodBlade.sheathAlpha(p,e.getStack(),Minecraft.getInstance().getPartialTick());
        if(alpha>=.999F)return;
        e.setCanceled(true);if(alpha<=.001F)return;
        fading=true;
        try {
            BladeRenderState.setCol(new java.awt.Color(1F,1F,1F,alpha));
            BladeRenderState.renderOverrided(e.getStack(),e.getOriginalModel(),e.getOriginalTarget(),e.getOriginalTexture(),
                    e.getPoseStack(),e.getBuffer(),e.getPackedLightIn(),BladeRenderState::getSlashBladeBlend,false);
        }finally {BladeRenderState.resetCol();fading=false;}
    }
    private AkatsukiBloodSheathRender() {}
}

package dev.bladetetra.mixin.client;

import dev.bladetetra.client.reflection.ReflectionPass;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve vanilla's underwater biome-transition history as well as GPU fog uniforms. */
@Mixin(FogRenderer.class)
public abstract class ReflectionFogMixin {
    @Shadow(aliases={"f_109010_"},remap=false) private static float fogRed;
    @Shadow(aliases={"f_109011_"},remap=false) private static float fogGreen;
    @Shadow(aliases={"f_109012_"},remap=false) private static float fogBlue;
    @Shadow(aliases={"f_109013_"},remap=false) private static int targetBiomeFog;
    @Shadow(aliases={"f_109014_"},remap=false) private static int previousBiomeFog;
    @Shadow(aliases={"f_109015_"},remap=false) private static long biomeChangedTime;
    @Inject(method={"setupColor","m_109018_"},at=@At("HEAD"),remap=false)
    private static void bladeTetra$saveFog(CallbackInfo ci) {
        if(!ReflectionPass.active()) return;
        float r=fogRed,g=fogGreen,b=fogBlue;int target=targetBiomeFog,previous=previousBiomeFog;long time=biomeChangedTime;
        ReflectionPass.saveFog(()->{fogRed=r;fogGreen=g;fogBlue=b;targetBiomeFog=target;previousBiomeFog=previous;biomeChangedTime=time;});
    }
}

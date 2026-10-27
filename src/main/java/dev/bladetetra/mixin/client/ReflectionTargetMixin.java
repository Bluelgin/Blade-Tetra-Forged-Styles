package dev.bladetetra.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.bladetetra.client.reflection.ReflectionRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class ReflectionTargetMixin {
    @Inject(method={"getMainRenderTarget","m_91385_"},at=@At("HEAD"),cancellable=true,remap=false)
    private void bladeTetra$secondaryTarget(CallbackInfoReturnable<RenderTarget> ci) {
        RenderTarget target=ReflectionRenderer.redirectTarget();
        if(target!=null) ci.setReturnValue(target);
    }
}

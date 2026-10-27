package dev.bladetetra.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.bladetetra.client.reflection.ReflectionPass;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
public abstract class ReflectionDistanceMixin {
    @Inject(method={"isVisible(Lnet/minecraft/world/phys/AABB;)Z","m_113029_(Lnet/minecraft/world/phys/AABB;)Z"},
            at=@At("HEAD"),cancellable=true,remap=false)
    private void bladeTetra$boundedReflection(AABB box,CallbackInfoReturnable<Boolean> ci) {
        if(ReflectionPass.active()&&RenderSystem.isOnRenderThread()&&ReflectionPass.outside(box)) ci.setReturnValue(false);
    }
}

package dev.bladetetra.mixin.client;

import dev.bladetetra.client.reflection.ReflectionPass;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A secondary world pass must not recursively execute mods' main-frame stage effects. */
@Mixin(value=ForgeHooksClient.class, remap=false)
public abstract class ReflectionRenderStageMixin {
    @Inject(method="dispatchRenderStage(Lnet/minecraftforge/client/event/RenderLevelStageEvent$Stage;Lnet/minecraft/client/renderer/LevelRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V",
            at=@At("HEAD"), cancellable=true)
    private static void bladeTetra$skipSecondaryStages(CallbackInfo ci) {
        if(ReflectionPass.active()) ci.cancel();
    }
}

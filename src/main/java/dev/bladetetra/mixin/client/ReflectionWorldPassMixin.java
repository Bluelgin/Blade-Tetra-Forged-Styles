package dev.bladetetra.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.bladetetra.client.reflection.MirrorRitualResources;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HEAD injection leaves the ordinary invocation intact; no competing world-render redirect. */
@Mixin(LevelRenderer.class)
public abstract class ReflectionWorldPassMixin {
    // The project has no generated refmap: name both official userdev and stable production SRG.
    @Inject(method={"renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            "m_109599_(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V"},
            at=@At("HEAD"),remap=false)
    private void bladeTetra$reflectionBeforeWorld(PoseStack pose,float partial,long deadline,boolean outline,
            Camera camera,GameRenderer game,LightTexture light,Matrix4f projection,CallbackInfo ci) {
        MirrorRitualResources.beforeWorld((LevelRenderer)(Object)this,pose,partial,deadline,camera,game,light,projection);
    }
}

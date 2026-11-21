package dev.bladetetra.mixin;

import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.InputStream;

/** Resharped shares mutable regex Matchers across resource and runtime OBJ loads. */
@Mixin(value = WavefrontObject.class, remap = false)
abstract class SlashBladeWavefrontParseMixin {
    @Shadow
    private void loadObjModel(InputStream input) {
        throw new AssertionError();
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target =
            "Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;loadObjModel(Ljava/io/InputStream;)V"),
            require = 0)
    private void bladeTetra$serializeObjParsing(WavefrontObject model, InputStream input) {
        // Resource reload workers and the render thread use the same lock. It is
        // released before constructor-return hooks bake the resulting geometry.
        synchronized (WavefrontObject.class) {
            loadObjModel(input);
        }
    }
}

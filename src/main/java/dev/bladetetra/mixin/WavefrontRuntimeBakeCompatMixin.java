package dev.bladetetra.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.flammpfeil.slashblade.client.renderer.model.obj.GroupObject;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;

/**
 * Keeps Blade Tetra's runtime Wavefront views compatible with render optimizers
 * that snapshot SlashBlade geometry during model construction.
 *
 * <p>Blade Tetra creates a tiny {@link WavefrontObject} named
 * {@code blade_tetra_runtime_part} and replaces its group list after the
 * constructor returns. Optimizers such as A Belated Gift can therefore cache
 * the intentionally empty constructor-time geometry and never see the live
 * selected/clipped faces installed by Blade Tetra.</p>
 *
 * <p>Do not try to mutate or rebuild another mod's private cache here. Instead,
 * mark only Blade Tetra's synthetic runtime objects at construction time and
 * reproduce SlashBlade's vanilla {@code tessellateOnly} loop for those objects.
 * The callback is then cancelled before lower-priority optimizer hooks can use
 * their stale snapshot. Normal SlashBlade models are untouched and keep using
 * the optimizer normally.</p>
 */
@Mixin(value = WavefrontObject.class, remap = false, priority = 2000)
public abstract class WavefrontRuntimeBakeCompatMixin {
    @Unique
    private boolean bladeTetra$runtimeView;

    @Inject(
            method = "<init>(Ljava/lang/String;Ljava/io/InputStream;)V",
            at = @At("RETURN"),
            require = 0)
    private void bladeTetra$markRuntimeView(
            String filename,
            InputStream inputStream,
            CallbackInfo callback) {
        bladeTetra$runtimeView = "blade_tetra_runtime_part".equals(filename);
    }

    @Inject(
            method = "tessellateOnly",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void bladeTetra$renderRuntimeViewFromLiveGroups(
            VertexConsumer consumer,
            PoseStack poses,
            int packedLight,
            int packedOverlay,
            String[] groupNames,
            CallbackInfo callback) {
        if (!bladeTetra$runtimeView) {
            return;
        }

        WavefrontObject self = (WavefrontObject) (Object) this;
        if (self.groupObjects != null && groupNames != null) {
            for (GroupObject group : self.groupObjects) {
                if (group == null || group.name == null) {
                    continue;
                }
                for (String requested : groupNames) {
                    if (requested != null && requested.equalsIgnoreCase(group.name)) {
                        // This is the original SlashBlade tessellateOnly behavior,
                        // deliberately reading Blade Tetra's post-construction groups.
                        group.render(consumer, poses, packedLight, packedOverlay);
                    }
                }
            }
        }

        // Runtime views are fully handled above, including the no-matching-group case.
        // Returning now prevents A Belated Gift from substituting its stale baked copy.
        callback.cancel();
    }
}

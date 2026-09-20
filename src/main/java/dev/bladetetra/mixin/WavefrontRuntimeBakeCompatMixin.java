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

import java.lang.reflect.Method;

/**
 * Keeps Blade Tetra's runtime Wavefront views compatible with render optimizers
 * that bake SlashBlade geometry at model construction time.
 *
 * <p>Blade Tetra creates a tiny {@link WavefrontObject} and then replaces its
 * group list with selected/clipped faces. A Belated Gift 1.0.x snapshots that
 * list from a constructor-return mixin, before Blade Tetra installs those
 * runtime groups. Its optimized render path therefore sees an empty baked
 * model and falls back to {@code tessellateOnly}, where the stale snapshot
 * would otherwise render nothing.</p>
 *
 * <p>This hook runs before the optimizer's own {@code tessellateOnly} injector.
 * If the requested group really exists in the live Wavefront object, it asks
 * the optional optimizer to rebuild its private baked snapshot once. There is
 * no compile-time dependency on A Belated Gift, and ordinary SlashBlade clients
 * only pay one failed reflective lookup for a model that reaches this fallback.</p>
 */
@Mixin(value = WavefrontObject.class, remap = false, priority = 2000)
public abstract class WavefrontRuntimeBakeCompatMixin {
    @Unique
    private boolean bladeTetra$runtimeBakeSynchronized;

    @Inject(method = "tessellateOnly", at = @At("HEAD"), require = 0)
    private void bladeTetra$refreshOptionalRuntimeBake(
            VertexConsumer consumer,
            PoseStack poses,
            int packedLight,
            int packedOverlay,
            String[] groupNames,
            CallbackInfo callback) {
        if (bladeTetra$runtimeBakeSynchronized) {
            return;
        }

        WavefrontObject self = (WavefrontObject) (Object) this;
        if (!bladeTetra$containsRenderableGroup(self, groupNames)) {
            return;
        }

        // The optional optimization mod is fixed for the lifetime of this class,
        // so do not repeat reflection on every frame after the first relevant draw.
        bladeTetra$runtimeBakeSynchronized = true;
        try {
            Method rebake = self.getClass().getDeclaredMethod("abg$bake");
            if (rebake.trySetAccessible()) {
                rebake.invoke(self);
            }
        } catch (NoSuchMethodException ignored) {
            // A Belated Gift is optional. Vanilla/Resharped rendering needs no work.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Compatibility must fail open. SlashBlade's normal fallback still runs.
        }
    }

    @Unique
    private static boolean bladeTetra$containsRenderableGroup(
            WavefrontObject model, String[] groupNames) {
        if (model.groupObjects == null || groupNames == null || groupNames.length == 0) {
            return false;
        }
        for (GroupObject group : model.groupObjects) {
            if (group == null || group.name == null || group.faces == null || group.faces.isEmpty()) {
                continue;
            }
            for (String requested : groupNames) {
                if (requested != null && group.name.equalsIgnoreCase(requested)) {
                    return true;
                }
            }
        }
        return false;
    }
}

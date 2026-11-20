package dev.bladetetra.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * INTERNAL TEST BUILD ONLY: inspect entity renderer map at the exact crash site.
 * Do not ship in production: missing renderers are logged and their render call
 * is skipped to let the player export logs instead of immediately crashing.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class KyoukaRendererProbeMixin {
    @Unique private static final Logger bladeTetra$vLogger =
            LoggerFactory.getLogger("BladeTetra-KyoukaVProbe");
    @Unique private static final Set<String> bladeTetra$vMissingTypes = new HashSet<>();
    @Unique private static final Set<UUID> bladeTetra$vSeenEchoes = new HashSet<>();

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void bladeTetra$vCheck(Entity entity, Frustum frustum,
            double cameraX, double cameraY, double cameraZ, CallbackInfoReturnable<Boolean> cir) {
        var dispatcher = (EntityRenderDispatcher)(Object)this;
        var renderer = dispatcher.getRenderer(entity);
        var type = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        String typeName = String.valueOf(type);
        if (renderer == null) {
            if (bladeTetra$vMissingTypes.add(typeName)) {
                bladeTetra$vLogger.error(
                        "[KYOUKA-V][MISSING_RENDERER] type={} class={} entityId={} uuid={} dimension={} pos={} camera={},{},{}; render skipped (test build only)",
                        typeName, entity.getClass().getName(), entity.getId(), entity.getUUID(),
                        entity.level().dimension().location(), entity.position(), cameraX, cameraY, cameraZ);
            }
            cir.setReturnValue(false);
            return;
        }
        if ("blade_tetra:kyouka_afterimage".equals(typeName)
                && bladeTetra$vSeenEchoes.add(entity.getUUID())) {
            bladeTetra$vLogger.info(
                    "[KYOUKA-V][RENDERER_OK] type={} entityId={} uuid={} renderer={} dimension={} pos={}",
                    typeName, entity.getId(), entity.getUUID(), renderer.getClass().getName(),
                    entity.level().dimension().location(), entity.position());
            if (bladeTetra$vSeenEchoes.size() > 256) bladeTetra$vSeenEchoes.clear();
        }
    }
}

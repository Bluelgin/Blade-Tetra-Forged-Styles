package dev.bladetetra.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Mojang's RenderSystem member names are unchanged in the production client (verified in client.jar). */
@Mixin(value=RenderSystem.class,remap=false)
public interface ReflectionLightingAccessor {
    @Accessor("shaderLightDirections")
    static Vector3f[] bladeTetra$lightDirections() {throw new AssertionError("Mixin accessor not applied");}
}

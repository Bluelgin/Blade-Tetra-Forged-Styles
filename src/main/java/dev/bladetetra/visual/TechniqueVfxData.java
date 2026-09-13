package dev.bladetetra.visual;

import net.minecraft.resources.ResourceLocation;

/** Renderer-facing technique data, independent from the network transport. */
public record TechniqueVfxData(
        ResourceLocation effectId,
        double startX, double startY, double startZ,
        double endX, double endY, double endZ,
        float yaw, float intensity,
        int sourceEntityId, int targetEntityId,
        int duration, int seed) {
}

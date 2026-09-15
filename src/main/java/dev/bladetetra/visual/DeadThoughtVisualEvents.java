package dev.bladetetra.visual;

import dev.bladetetra.BladeTetra;
import net.minecraft.resources.ResourceLocation;

public final class DeadThoughtVisualEvents {
    public static final ResourceLocation START = id("start");
    public static final ResourceLocation LEFT = id("left");
    public static final ResourceLocation RIGHT = id("right");
    public static final ResourceLocation FINAL = id("final");
    public static final ResourceLocation EROSION = id("erosion");
    public static final ResourceLocation STATE = id("state");
    public static final ResourceLocation END = id("end");
    public static final ResourceLocation[] ALL = {START, LEFT, RIGHT, FINAL, EROSION, STATE, END};

    private static ResourceLocation id(String stage) {
        return new ResourceLocation(BladeTetra.MOD_ID, "dead_thought/" + stage);
    }

    private DeadThoughtVisualEvents() {}
}

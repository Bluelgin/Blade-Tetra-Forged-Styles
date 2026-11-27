package dev.bladetetra.export;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bladetetra.BladeTetra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Renders the deposited stack without creating entities or changing its ownership. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BladeWorkbenchRenderer implements BlockEntityRenderer<BladeSealWorkbenchStorage> {
    private static final java.util.Map<mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject, float[]> FRAMES = new java.util.IdentityHashMap<>();
    static void resourcesReloaded() { FRAMES.clear(); }
    private static float[] bounds(mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject model) {
        float minX=Float.POSITIVE_INFINITY, minY=minX, minZ=minX;
        float maxX=Float.NEGATIVE_INFINITY, maxY=maxX, maxZ=maxX;
        for (var group : model.groupObjects) {
            if (!group.name.equals("blade") && !group.name.equals("sheath")) continue;
            for (var face : group.faces) for (var v : face.vertices) {
                minX=Math.min(minX,v.x); minY=Math.min(minY,v.y); minZ=Math.min(minZ,v.z);
                maxX=Math.max(maxX,v.x); maxY=Math.max(maxY,v.y); maxZ=Math.max(maxZ,v.z);
            }
        }
        float length=Math.max(maxX-minX,Math.max(maxY-minY,maxZ-minZ));
        if (!Float.isFinite(length) || length < .001f) return new float[]{0,0,0,100};
        return new float[]{(minX+maxX)/2, (minY+maxY)/2, (minZ+maxZ)/2,length};
    }
    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BladeSealWorkbench.STORAGE.get(), context -> new BladeWorkbenchRenderer());
    }

    @Override
    public void render(BladeSealWorkbenchStorage table, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        var blade = table.inventory().getItem(0);
        if (blade.isEmpty()) return;
        pose.pushPose();
        try {
            var state = blade.getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE).orElse(null);
            if (state == null) return;
            var id = state.getModel().orElse(null);
            var texture = state.getTexture().orElse(null);
            if (id == null || texture == null) return;
            var model = mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager.getInstance().getModel(id);
            var bounds = FRAMES.computeIfAbsent(model, BladeWorkbenchRenderer::bounds);
            pose.translate(.5, 1.03, .5);
            pose.mulPose(Axis.YP.rotationDegrees(-table.getBlockState().getValue(BladeSealWorkbench.FACING).toYRot()));
            float scale = 1.35f / bounds[3];
            pose.scale(scale, scale, scale);
            pose.translate(-bounds[0], -bounds[1], -bounds[2]);
            mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState.resetCol();
            String group = state.isBroken() ? "blade_damaged" : "blade";
            mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState.renderOverrided(blade, model, group, texture, pose, buffers, light);
            if (!state.isBroken()) mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState.renderOverrided(blade, model, "sheath", texture, pose, buffers, light);

        } finally {
            pose.popPose();
        }
    }
}

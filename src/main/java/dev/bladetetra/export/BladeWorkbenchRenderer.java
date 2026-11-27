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
            pose.translate(.5, 1.05, .5);
            pose.mulPose(Axis.YP.rotationDegrees(-table.getBlockState().getValue(BladeSealWorkbench.FACING).toYRot()));
            pose.mulPose(Axis.ZP.rotationDegrees(-45));
            pose.scale(.8f, .8f, .8f);
            Minecraft.getInstance().getItemRenderer().renderStatic(blade, ItemDisplayContext.FIXED,
                    light, overlay, pose, buffers, table.getLevel(), 0);
        } finally {
            pose.popPose();
        }
    }
}

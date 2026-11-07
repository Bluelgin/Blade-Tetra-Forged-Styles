package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.bladetetra.kyouka.KyoukaAfterimageEntity;
import dev.bladetetra.registry.ModEntities;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * A completely model-independent water illusion. Always draw the same simple
 * translucent humanoid, regardless of player skin, model, held item or installed
 * player-rendering mods. Its server-side targetability, aggro, lifetime and
 * shatter damage are unaffected by this presentation.
 *
 * No PlayerRenderer, PlayerModel, LivingEntityRenderer, skin lookup, layer,
 * animation controller or player entity is accessed by this render path.
 */
public final class KyoukaAfterimageRenderer extends EntityRenderer<KyoukaAfterimageEntity> {
    private static final ResourceLocation WATER =
            new ResourceLocation("blade_tetra", "textures/effect/kyouka/water_film.png");

    public KyoukaAfterimageRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(KyoukaAfterimageEntity entity, float yaw, float partial,
            PoseStack pose, MultiBufferSource buffers, int packedLight) {
        // 60 ticks is the existing server-side lifetime. Never draw a ghost
        // after the local expiration boundary, even before removal is received.
        float age = entity.tickCount + partial;
        float remaining = 60F - age;
        if (remaining <= 0F) return;
        float fade = Math.min(1F, remaining / 12F);
        float emergence = Math.min(1F, (age + 1F) / 4F);
        int alpha = Mth.clamp(Math.round(115F * fade * emergence), 0, 115);
        if (alpha == 0) return;

        pose.pushPose();
        try {
            float wave = Mth.sin(age * .16F);
            pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            pose.translate(0, .023F * wave, 0);
            VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());

            // A real volume, not a player-shaped billboard: the torso and
            // limbs are visible from every direction. Only 3 decoys per owner.
            box(vertices, pose, -.215F, .84F, -.105F, .215F, 1.46F, .105F,
                    64, 178, 222, alpha);
            box(vertices, pose, -.115F, 1.45F, -.085F, .115F, 1.56F, .085F,
                    91, 211, 235, alpha);
            box(vertices, pose, -.18F, 1.56F, -.17F, .18F, 1.88F, .17F,
                    116, 225, 242, alpha);
            // Arms, with a slight flowing pose rather than player animations.
            box(vertices, pose, -.38F, .89F + .025F * wave, -.09F,
                    -.235F, 1.40F + .025F * wave, .09F, 57, 171, 226, alpha);
            box(vertices, pose, .235F, .94F - .018F * wave, -.09F,
                    .38F, 1.40F - .018F * wave, .09F, 57, 171, 226, alpha);
            box(vertices, pose, -.20F, .09F, -.085F, -.025F, .84F, .085F,
                    48, 153, 215, alpha);
            box(vertices, pose, .025F, .09F, -.085F, .20F, .84F, .085F,
                    48, 153, 215, alpha);

            // An original water blade, not a copied equipped item or a
            // SlashBlade model. Neither equipment capability nor item renderer
            // is ever consulted.
            pose.pushPose();
            try {
                pose.translate(.325F, 1.05F, .06F);
                pose.mulPose(Axis.ZP.rotationDegrees(-24F + 2F * wave));
                box(vertices, pose, -.023F, -.13F, -.024F, .023F, .13F, .024F,
                        36, 125, 191, alpha);
                box(vertices, pose, -.125F, .095F, -.035F, .125F, .135F, .035F,
                        100, 223, 244, alpha);
                box(vertices, pose, -.046F, .135F, -.026F, .046F, .75F, .026F,
                        143, 238, 253, alpha);
                bladeTip(vertices, pose, .75F, .89F, alpha);
            } finally {
                pose.popPose();
            }
        } finally {
            pose.popPose();
        }
    }

    /** All 6 faces are emitted, so the silhouette is volumetric from all angles. */
    private static void box(VertexConsumer out, PoseStack pose,
            float x0, float y0, float z0, float x1, float y1, float z1,
            int red, int green, int blue, int alpha) {
        Matrix4f matrix = pose.last().pose();
        quad(out, matrix, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1,
                red, green, blue, alpha);
        quad(out, matrix, x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0,
                red, green, blue, alpha);
        quad(out, matrix, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0,
                red, green, blue, alpha);
        quad(out, matrix, x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1,
                red, green, blue, alpha);
        quad(out, matrix, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0,
                red, green, blue, alpha);
        quad(out, matrix, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1,
                red, green, blue, alpha);
    }

    private static void bladeTip(VertexConsumer out, PoseStack pose,
            float base, float tip, int alpha) {
        Matrix4f matrix = pose.last().pose();
        // Four triangles are represented as four degenerate quads, which is
        // compatible with the RenderType.lightning POSITION_COLOR quad pipeline.
        triangle(out, matrix, -.046F,base,-.026F, .046F,base,-.026F,
                0,tip,0, alpha);
        triangle(out, matrix, .046F,base,.026F, -.046F,base,.026F,
                0,tip,0, alpha);
        triangle(out, matrix, -.046F,base,.026F, -.046F,base,-.026F,
                0,tip,0, alpha);
        triangle(out, matrix, .046F,base,-.026F, .046F,base,.026F,
                0,tip,0, alpha);
    }

    private static void triangle(VertexConsumer out, Matrix4f matrix,
            float ax,float ay,float az, float bx,float by,float bz,
            float cx,float cy,float cz, int alpha) {
        quad(out, matrix, ax,ay,az, bx,by,bz, cx,cy,cz, cx,cy,cz,
                159, 244, 255, alpha);
    }

    private static void quad(VertexConsumer out, Matrix4f matrix,
            float ax,float ay,float az, float bx,float by,float bz,
            float cx,float cy,float cz, float dx,float dy,float dz,
            int red,int green,int blue,int alpha) {
        out.vertex(matrix, ax,ay,az).color(red,green,blue,alpha).endVertex();
        out.vertex(matrix, bx,by,bz).color(red,green,blue,alpha).endVertex();
        out.vertex(matrix, cx,cy,cz).color(red,green,blue,alpha).endVertex();
        out.vertex(matrix, dx,dy,dz).color(red,green,blue,alpha).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(KyoukaAfterimageEntity entity) {
        // The actual renderer is untextured; this fulfills the base API only.
        return WATER;
    }

    @Mod.EventBusSubscriber(modid = "blade_tetra",
            bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.KYOUKA_AFTERIMAGE.get(),
                    KyoukaAfterimageRenderer::new);
        }
    }
}

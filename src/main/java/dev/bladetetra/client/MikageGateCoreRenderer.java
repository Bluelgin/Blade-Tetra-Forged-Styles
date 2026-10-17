package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.bladetetra.challenge.MikageGateCoreEntity;
import dev.bladetetra.challenge.mikage.GateBarrageSequence;
import dev.bladetetra.client.vfx.render.MikageToriiMesh;
import dev.bladetetra.config.ClientVisualConfig;
import mods.flammpfeil.slashblade.client.renderer.entity.JudgementCutRenderer;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.*;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.color;

/** The objective remains readable when decorative combat VFX are disabled, including for late joiners. */
public final class MikageGateCoreRenderer extends EntityRenderer<MikageGateCoreEntity> {
    private final JudgementCutRenderer<EntityJudgementCut> nativeCore;
    public MikageGateCoreRenderer(EntityRendererProvider.Context context) {
        super(context); nativeCore = new JudgementCutRenderer<>(context);
    }
    @Override public void render(MikageGateCoreEntity core, float yaw, float partial, PoseStack poses,
            MultiBufferSource buffers, int light) {
        float age = core.level().getGameTime() - core.gateStart() + partial;
        boolean breaking = core.gateState() == 2;
        float progress = breaking ? Mth.clamp((core.level().getGameTime() - core.breakAt() + partial)
                / GateBarrageSequence.BREAK_TICKS, 0, 1) : 0;
        float spread = smooth(breaking ? 1 - progress : Mth.clamp((age - 4) / 6, 0, 1));
        float fade = breaking ? 1 - progress : 1;
        double angle = Math.toRadians(core.getYRot());
        Vec3 right = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .subtract(core.getPosition(partial));
        var vertices = buffers.getBuffer(RenderType.lightning());
        int red = color(1, .08F, .22F, fade * .8F), white = color(1, .94F, .9F, fade * .8F);
        MikageToriiMesh.draw(vertices, poses.last().pose(), new Vec3(0, -.8, 0), right,
                camera, 5.7, spread, red, white, color(.42F, .65F, 1, fade * .13F));
        if (spread > .01F) {
            poses.pushPose(); poses.translate(0, .7, 0);
            float size = .75F * spread; poses.scale(size, size, size);
            nativeCore.render(core.nativeVisual(), core.getYRot(), partial, poses, buffers, 15728880);
            poses.popPose();
            fractures(buffers.getBuffer(RenderType.lightning()), poses, camera, right, 3 - core.remainingHits(), spread, fade);
        }
        if (!breaking && spread > .9F && ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get())
            ammunition(core, poses, buffers, right, age);
    }
    private static float smooth(float value) { return value * value * (3 - 2 * value); }
    private static void fractures(com.mojang.blaze3d.vertex.VertexConsumer vertices, PoseStack poses,
            Vec3 camera, Vec3 right, int cracks, float spread, float fade) {
        Vec3 middle = new Vec3(0, .7, 0);
        Vec3 front = middle.add(camera.subtract(middle).normalize().scale(.74 * spread));
        int tint = color(1, .83F, .55F, fade);
        for (int crack = 0; crack < Math.min(3, cracks); crack++) {
            double sign = crack == 1 ? -1 : 1;
            Vec3 a = front.add(0, (crack - 1) * .12, 0);
            for (int segment = 1; segment <= 3; segment++) {
                Vec3 b = front.add(right.scale(sign * segment * .2 * spread))
                        .add(0, ((segment % 2 == 0 ? -.13 : .16) + (crack - 1) * .2) * spread, 0);
                Vec3 width = b.subtract(a).cross(camera.subtract(front)).normalize().scale(.018);
                for (Vec3 p : new Vec3[]{a.subtract(width), b.subtract(width), b.add(width), a.add(width)})
                    vertices.vertex(poses.last().pose(), (float) p.x, (float) p.y, (float) p.z)
                            .color(tint >> 16 & 255, tint >> 8 & 255, tint & 255, tint >>> 24).endVertex();
                a = b;
            }
        }
    }
    private static void ammunition(MikageGateCoreEntity core, PoseStack poses, MultiBufferSource buffers,
            Vec3 right, float age) {
        int rows = ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get() <= 0 ? 1 : 3;
        double angle = Math.toRadians(core.getYRot());
        Vec3 forward = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
        for (int row = 0; row < rows; row++) for (int lane = -2; lane <= 2; lane++) {
            Vec3 at = right.scale(lane * 2.2).add(forward.scale(-.25))
                    .add(0, 2.2 + row * 2.3 + Math.sin(age * .06 + lane + row) * .12, 0);
            poses.pushPose(); poses.translate(at.x, at.y, at.z);
            MikagePhantomSwordRenderer.renderRideSword(poses, buffers, 15728880,
                    core.getYRot(), 0xD51F3F, .7F);
            poses.popPose();
        }
    }
    @Override public boolean shouldRender(MikageGateCoreEntity core, Frustum frustum,
            double cameraX, double cameraY, double cameraZ) {
        // The small, attackable core's hitbox must not cull a fourteen-block doorway.
        return core.shouldRenderAtSqrDistance(core.distanceToSqr(cameraX, cameraY, cameraZ))
                && frustum.isVisible(new AABB(core.getX() - 7.4, core.getY() - .8, core.getZ() - 7.4,
                core.getX() + 7.4, core.getY() + 10.6, core.getZ() + 7.4));
    }
    @Override public ResourceLocation getTextureLocation(MikageGateCoreEntity core) {
        return new ResourceLocation("slashblade", "model/util/slashdim.png");
    }
}

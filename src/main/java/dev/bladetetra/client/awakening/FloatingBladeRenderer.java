package dev.bladetetra.client.awakening;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bladetetra.client.AwakeningBladeGeometry;
import dev.bladetetra.easteregg.AwakeningPresentationTimeline;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

/** Only the motion/placement layer is new. Native render events still own appearance. */
@Mod.EventBusSubscriber(modid = "blade_tetra", value = Dist.CLIENT)
public final class FloatingBladeRenderer {
    public interface RendererAdapter {
        /** Return null to decline an unsupported blade; never silently substitute a default mesh. */
        Prepared prepare(ItemStack stack);
    }
    public interface Prepared {
        void render(ItemStack appearance, PoseStack pose, MultiBufferSource buffers, int light, boolean sheath);
    }
    private static final List<RendererAdapter> ADAPTERS = new ArrayList<>();
    private static int rendering;
    private static boolean ghostPass;
    private static float fraction = 1;
    private static boolean energy;
    private static float overlayAlpha;
    private static int overlayTint;
    private static final ResourceLocation VEINS = new ResourceLocation("blade_tetra",
            "textures/effect/awakened/ritual/energy_veins.png");
    private static final ResourceLocation WHITE = new ResourceLocation("blade_tetra",
            "textures/effect/awakened/ritual/white_energy.png");
    public static boolean isRendering() { return rendering > 0; }
    public static void registerAdapter(RendererAdapter adapter) { ADAPTERS.add(0, adapter); }

    public static Prepared prepare(ItemStack stack) {
        for (var adapter : ADAPTERS) {
            Prepared result = adapter.prepare(stack);
            if (result != null) return result;
        }
        var state = stack.getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        if (state == null) return null;
            var modelId = state.getModel().orElse(null);
            var texture = state.getTexture().orElse(null);
            var resources = Minecraft.getInstance().getResourceManager();
            if (modelId == null || texture == null || resources.getResource(modelId).isEmpty()
                    || resources.getResource(texture).isEmpty()) return null;
            var model = BladeModelManager.getInstance().getModel(modelId);
            String target = state.isBroken() ? "blade_damaged" : "blade";
            if (model.groupObjects.stream().noneMatch(g -> target.equals(g.name) && !g.faces.isEmpty()))
                return null;
            return (Prepared) (appearance, pose, buffers, light, sheath) -> {
                String group = sheath ? "sheath" : target;
                BladeRenderState.renderOverrided(appearance, model, group, texture, pose, buffers, light);
                BladeRenderState.renderOverridedLuminous(appearance, model, group + "_luminous",
                        texture, pose, buffers, light);
            };
    }

    /** Stage-local geometry filter runs after the existing material/legacy adapters. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void filter(RenderOverrideEvent event) {
        if (!isRendering()) return;
        if (ghostPass && overlayAlpha <= 0) {
            event.setEnableEffect(false);
            BladeRenderState.setCol(0x559AD9FF);
        }
        if (overlayAlpha > 0) {
            event.setPackedLightIn(LightTexture.FULL_BRIGHT);
            event.setTexture(energy ? WHITE : VEINS);
            event.setGetRenderType(BladeRenderState::getSlashBladeBlendLuminous);
            event.setEnableEffect(false);
            BladeRenderState.setCol((Math.round(overlayAlpha * 255) << 24) | overlayTint);
        }
        if (fraction >= .999F || !event.getTarget().startsWith("blade")) return;
        var model = AwakeningBladeGeometry.remaining(event.getModel(), event.getTarget(), fraction);
        if (model == null) event.setCanceled(true); else event.setModel(model);
    }

    public static Vec3 root(AwakeningSession s, float partial) {
        var p = s.owner;
        return new Vec3(net.minecraft.util.Mth.lerp(partial,p.xOld,p.getX()),
                net.minecraft.util.Mth.lerp(partial,p.yOld,p.getY()),
                net.minecraft.util.Mth.lerp(partial,p.zOld,p.getZ()));
    }
    public static Vec3 forward(AwakeningSession s, float partial) {
        double yaw = Math.toRadians(net.minecraft.util.Mth.rotLerp(partial,s.owner.yRotO,s.owner.getYRot()));
        return new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
    }
    public static Vec3 hand(AwakeningSession s, float partial) {
        Vec3 f = forward(s, partial), right = new Vec3(f.z,0,-f.x);
        double arm = s.owner.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        return root(s, partial).add(0,s.owner.getEyeHeight() - .38,0)
                .add(f.scale(.38)).add(right.scale(.32*arm));
    }
    public static Vec3 position(AwakeningSession s, float partial, float tick) {
        Vec3 f = forward(s, partial);
        var profile = AwakeningVisualProfile.get(s.data.soul());
        float flight = AwakeningPresentationTimeline.flight(tick,s.data.soul());
        Vec3 altar = root(s,partial).add(f.scale(profile.forward()))
                .add(0,s.owner.getEyeHeight() + profile.height() - 1.62,0);
        float hover = AwakeningPresentationTimeline.smooth(
                AwakeningPresentationTimeline.progress(tick,15,25))
                * (1-AwakeningPresentationTimeline.progress(tick,100,120));
        return hand(s,partial).lerp(altar,flight)
                .add(0, Math.sin(tick*.14)*.055*hover + Math.sin(flight*Math.PI)*.22,0);
    }

    public static boolean localHandPass(AwakeningSession s) {
        var mc=Minecraft.getInstance();
        return s.owner==mc.player && mc.options.getCameraType().isFirstPerson()
                && !mc.options.hideGui && !s.owner.isSpectator();
    }

    /** Replace the local native pass in its own projection, blending from the exact live MMD pose. */
    public static void renderFirstPerson(AwakeningSession s, RenderOverrideEvent event,
            Matrix4f worldView, Matrix4f worldProjection) {
        String target=event.getOriginalTarget();
        if (!"blade".equals(target) && !"blade_damaged".equals(target) && !"sheath".equals(target)) return;
        var mc=Minecraft.getInstance(); float partial=mc.getPartialTick();
        float tick=s.age(mc.level,partial);
        boolean sheath=target.equals("sheath");
        var capture=sheath?s.sheathPose:s.bladePose;
        capture.capture(event.getPoseStack().last().pose());
        PoseStack pose=new PoseStack();
        Matrix4f projection=com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix();
        pose.mulPoseMatrix(new Matrix4f().scaling(worldProjection.m00()/projection.m00(),
                worldProjection.m11()/projection.m11(),1).mul(worldView));
        Vec3 eye=mc.gameRenderer.getMainCamera().getPosition();
        pose.translate(-eye.x,-eye.y,-eye.z);
        Vec3 pos=position(s,partial,tick);
        pose.translate(pos.x,pos.y,pos.z);
        Vec3 f=forward(s,partial);
        pose.mulPose(Axis.YP.rotation((float)Math.atan2(-f.x,f.z)));
        float flight=AwakeningPresentationTimeline.flight(tick,s.data.soul());
        float angle=AwakeningVisualProfile.get(s.data.soul()).angle();
        float spin=tick<85?(float)Math.sin(tick*.045)*8:0;
        pose.mulPose(Axis.ZP.rotationDegrees(-25+(angle+25+spin)*flight));
        pose.scale(.0078125F,.0078125F,.0078125F); pose.translate(-20,0,0);
        if(sheath) pose.translate(0,-13,10);
        capture.blend(pose,flight);
        rendering++;
        float oldFraction=fraction, oldAlpha=overlayAlpha;
        boolean oldEnergy=energy; int oldTint=overlayTint;
        try {
            fraction=AwakeningPresentationTimeline.bladeFraction(tick,s.data.soul());
            energy=tick>=85&&tick<89;
            s.model.render(s.appearance,pose,event.getBuffer(),event.getPackedLightIn(),sheath);
            if(tick<100) {
                overlayAlpha=.35F; overlayTint=energy?0xFFFFFF:AwakeningPresentationTimeline.tint(s.data.soul());
                s.model.render(s.appearance,pose,event.getBuffer(),LightTexture.FULL_BRIGHT,sheath);
            }
        } finally {
            fraction=oldFraction; overlayAlpha=oldAlpha; energy=oldEnergy; overlayTint=oldTint;
            rendering--; BladeRenderState.resetCol();
        }
    }

    public static void render(AwakeningSession s, PoseStack pose, MultiBufferSource buffers,
            float partial, float tick) {
        Vec3 pos = position(s,partial,tick);
        if (!localHandPass(s)) renderAt(s, pose, buffers, pos, tick, partial, false);
        // Kyouka's ghosts use the exact same snapshot/native model, in real 3D space.
        if (s.data.soul() == 2 && tick >= 45 && tick < 91) {
            int count = dev.bladetetra.config.ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get() == 0 ? 2 : 4;
            for (int i=0;i<count;i++) {
                if (tick < 46+i*5) continue;
                double a = i*Math.PI*2/count;
                float draw = AwakeningPresentationTimeline.smooth(
                        AwakeningPresentationTimeline.progress(tick,46+i*5,51+i*5));
                Vec3 f = forward(s,partial), side = new Vec3(f.z,0,-f.x);
                Vec3 ghost = pos.add(side.scale(Math.cos(a)*1.3)).add(0,-.2,0)
                        .add(f.scale(.4+Math.sin(a)*.6));
                renderAt(s,pose,buffers,ghost,Math.min(tick-i*6,80),partial,true);
            }
            // Pseudo reflection beneath the real sword, no framebuffer/shader dependency.
            pose.pushPose();
            try {
                Vec3 floor = root(s,partial);
                pose.translate(0, 2*(floor.y+.04),0); pose.scale(1,-1,1);
                renderAt(s,pose,buffers,pos,tick,partial,true);
            } finally { pose.popPose(); }
        }
    }
    private static void renderAt(AwakeningSession s, PoseStack pose, MultiBufferSource buffers,
            Vec3 pos, float tick, float partial, boolean ghost) {
        pose.pushPose(); rendering++;
        float oldFraction = fraction, oldAlpha = overlayAlpha; boolean oldEnergy = energy;
        int oldTint = overlayTint; boolean oldGhost = ghostPass;
        try {
            ghostPass = ghost;
            fraction = AwakeningPresentationTimeline.bladeFraction(tick,s.data.soul());
            energy = tick >= 85 && tick < 89;
            pose.translate(pos.x,pos.y,pos.z);
            Vec3 f = forward(s,partial);
            pose.mulPose(Axis.YP.rotation((float)Math.atan2(-f.x,f.z)));
            float flight = AwakeningPresentationTimeline.flight(tick,s.data.soul());
            float angle = AwakeningVisualProfile.get(s.data.soul()).angle();
            float spin = tick < 85 ? (float)Math.sin(tick*.045)*8 : 0;
            pose.mulPose(Axis.ZP.rotationDegrees(-25+(angle+25+spin)*flight));
            if (ghost) pose.mulPose(Axis.ZP.rotationDegrees(-30F*(float)Math.sin(
                    AwakeningPresentationTimeline.progress(tick,52,58)*Math.PI)));
            pose.scale(.0078125F,.0078125F,.0078125F);
            // Existing SlashBlade source coordinates, grip pivot rather than an icon pivot.
            pose.translate(-20,0,0);
            pose.pushPose();
            try {
            if (!ghost) s.bladePose.blend(pose, flight);
            if (ghost) BladeRenderState.setCol(0x559AD9FF);
            s.model.render(s.appearance,pose,buffers,LightTexture.FULL_BRIGHT,false);
            if (!ghost && tick < 100) {
                overlayAlpha = .25F + .3F * (float)Math.pow(Math.sin(tick * .2), 2);
                overlayTint = energy ? 0xFFFFFF : AwakeningPresentationTimeline.tint(s.data.soul());
                s.model.render(s.appearance,pose,buffers,LightTexture.FULL_BRIGHT,false);
                overlayAlpha = 0;
            }
            } finally { pose.popPose(); }
            // The sheath flies beside the same blade, using its own original model group.
            if (!ghost) {
                pose.translate(0,-13,10);
                s.sheathPose.blend(pose,flight);
                s.model.render(s.appearance,pose,buffers,LightTexture.FULL_BRIGHT,true);
                if (tick < 85) {
                    overlayAlpha = .28F; overlayTint = AwakeningPresentationTimeline.tint(s.data.soul());
                    s.model.render(s.appearance,pose,buffers,LightTexture.FULL_BRIGHT,true);
                    overlayAlpha = 0;
                }
            }
        } finally {
            fraction = oldFraction; energy = oldEnergy; overlayAlpha = oldAlpha; overlayTint = oldTint; ghostPass = oldGhost; rendering--;
            BladeRenderState.resetCol(); pose.popPose();
        }
    }
    private FloatingBladeRenderer() {}
}

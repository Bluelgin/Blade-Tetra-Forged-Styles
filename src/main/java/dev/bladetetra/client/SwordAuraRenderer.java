package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.aura.SwordAuraDriveEntity;
import dev.bladetetra.aura.SwordAuraStyleProfile;
import dev.bladetetra.config.ClientVisualConfig;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/** Native Drive mesh/texture and luminous hook, with both layers owned by one projectile. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class SwordAuraRenderer extends EntityRenderer<SwordAuraDriveEntity> {
    private static final ResourceLocation TEXTURE = SlashBlade.prefix("model/util/ss.png");
    private static final ResourceLocation MODEL = SlashBlade.prefix("model/util/drive.obj");
    private static int decorations;

    public SwordAuraRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @SubscribeEvent
    public static void beginFrame(RenderLevelStageEvent event) {
        // Forge fires this even in dimensions without a rendered sky, before entities.
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) decorations = 0;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(SwordAuraDriveEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(SwordAuraDriveEntity entity, float yaw, float partialTick,
            PoseStack pose, @NotNull MultiBufferSource buffers, int packedLight) {
        int alpha = SwordAuraRenderPolicy.alpha(entity.getLifetime(), entity.tickCount);
        // Do not tessellate OBJ faces after the native fade has become fully transparent.
        if (alpha == 0) return;
        float heading = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F;
        float pitch = Mth.rotLerp(partialTick, entity.xRotO, entity.getXRot());
        var profile = SwordAuraStyleProfile.forStyle(entity.getVisualStyle());
        // The manager caches the model and handles resource reloads. Share it across layers.
        var model = BladeModelManager.getInstance().getModel(MODEL);
        try {
            BladeRenderState.setCol((entity.getColor() & 0xFFFFFF) | alpha << 24);
            draw(model, pose, buffers, packedLight, entity.getBaseSize(), heading, pitch,
                    entity.getRotationRoll(), 0.0D);
            if (profile.hasDecoration() && SwordAuraRenderPolicy.allowDecoration(
                    entity.position().distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()),
                    ClientVisualConfig.SWORD_AURA_DETAIL.get(), ClientVisualConfig.SWORD_AURA_DECORATION_DISTANCE.get(),
                    ClientVisualConfig.SWORD_AURA_MAX_DECORATIONS.get(), decorations)) {
                decorations++;
                draw(model, pose, buffers, packedLight, entity.getBaseSize(), heading, pitch,
                        entity.getRotationRoll() + profile.additionalRoll(), profile.additionalHeight());
            }
        } finally {
            BladeRenderState.resetCol();
        }
    }

    private static void draw(WavefrontObject model, PoseStack pose, MultiBufferSource buffers, int light,
            float width, float yaw, float pitch, float roll, double height) {
        pose.pushPose();
        try {
            // Same transform order as the previous native renderer, including world-Y layer offset.
            pose.translate(0.0D, height, 0.0D);
            pose.scale(width, 1.0F, 1.0F);
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.mulPose(Axis.ZP.rotationDegrees(pitch));
            pose.mulPose(Axis.XP.rotationDegrees(roll));
            pose.scale(0.015F, 0.015F, 0.015F);
            pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            // Preserve native render-override events and the shared luminous buffer. No per-aura flush.
            BladeRenderState.renderOverridedLuminous(ItemStack.EMPTY, model, "base", TEXTURE, pose, buffers, light);
        } finally {
            pose.popPose();
        }
    }
}

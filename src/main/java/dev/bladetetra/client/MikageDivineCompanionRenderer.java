package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.challenge.MikageDivineCompanionEntity;
import dev.bladetetra.registry.ModEntities;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class MikageDivineCompanionRenderer extends LivingEntityRenderer<MikageDivineCompanionEntity,PlayerModel<MikageDivineCompanionEntity>> {
    private static final ResourceLocation SKIN=new ResourceLocation(BladeTetra.MOD_ID,"textures/entity/mikage.png");
    private static final ResourceLocation BLINK=new ResourceLocation(BladeTetra.MOD_ID,"textures/entity/mikage_blink.png");
    public MikageDivineCompanionRenderer(EntityRendererProvider.Context context) {
        super(context,new CompanionModel(context.bakeLayer(ModelLayers.PLAYER_SLIM)),.5F);
        addLayer(new LayerMainBlade<>(this));
    }
    @Override public ResourceLocation getTextureLocation(MikageDivineCompanionEntity entity) {
        return (entity.tickCount+entity.getId()*17)%94<4?BLINK:SKIN;
    }
    private static final class CompanionModel extends PlayerModel<MikageDivineCompanionEntity> {
        CompanionModel(ModelPart root) { super(root,true); }
        @Override public void setupAnim(MikageDivineCompanionEntity e,float walk,float amount,float age,float yaw,float pitch) {
            body.xRot=body.yRot=rightArm.yRot=leftArm.yRot=0;
            super.setupAnim(e,walk,amount,age,yaw,pitch);
            rightArm.xRot=-.25F;
            switch(e.pose()) {
                case 1,3 -> { leftArm.xRot=-1.25F; leftArm.yRot=.3F; rightArm.xRot=-.6F; body.xRot=.08F; }
                case 2 -> { rightArm.xRot=-1.1F; rightArm.yRot=-.45F; body.yRot=-.15F; }
                case 4 -> { body.xRot=.22F; head.xRot=.3F; }
                default -> { }
            }
            hat.copyFrom(head); jacket.copyFrom(body); leftSleeve.copyFrom(leftArm); rightSleeve.copyFrom(rightArm);
            leftPants.copyFrom(leftLeg); rightPants.copyFrom(rightLeg);
        }
    }
    @Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class Registration {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(ModEntities.MIKAGE_DIVINE_COMPANION.get(),MikageDivineCompanionRenderer::new);
        }
    }
}

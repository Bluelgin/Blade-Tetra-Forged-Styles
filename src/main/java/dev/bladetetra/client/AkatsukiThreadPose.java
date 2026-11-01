package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.easteregg.AkatsukiThreadCombos;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Reuse the matching native body preparation when Player Animator is present. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID,value=Dist.CLIENT)
public final class AkatsukiThreadPose {
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void motion(BladeMotionEvent e) {
        if(AkatsukiThreadCombos.THRUST_ID.equals(e.getCombo())&&ModList.get().isLoaded("playeranimator"))Native.start(e);
    }
    private static final class Native {
        static void start(BladeMotionEvent e) {
            if(!(e.getEntity() instanceof net.minecraft.client.player.AbstractClientPlayer p))return;
            var layers=dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.getPlayerAnimLayer(p);
            layers.removeLayer(0);layers.addAnimLayer(0,new Preparation(p));
        }
        private static final class Preparation implements dev.kosmx.playerAnim.api.layered.IAnimation {
            final net.minecraft.client.player.AbstractClientPlayer owner;
            final mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation intro=new mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation(
                    mods.flammpfeil.slashblade.init.DefaultResources.testPLLocation,1,33,false);
            final mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation hold=new mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation(
                    mods.flammpfeil.slashblade.init.DefaultResources.testPLLocation,33,34,false);
            Preparation(net.minecraft.client.player.AbstractClientPlayer owner){this.owner=owner;}
            @Override public void tick(){if(intro.isActive())intro.tick();}
            @Override public void setupAnim(float partial){if(intro.isActive())intro.setupAnim(partial);else hold.setupAnim(0);}
            @Override public boolean isActive(){return owner.isAlive()&&owner.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                    .map(s->AkatsukiThreadCombos.THRUST_ID.equals(s.getComboSeq())).orElse(false);}
            @Override public dev.kosmx.playerAnim.core.util.Vec3f get3DTransform(String bone,dev.kosmx.playerAnim.api.TransformType type,float partial,dev.kosmx.playerAnim.core.util.Vec3f base){
                if(bone.equals("leftLeg")||bone.equals("rightLeg"))return base;
                return intro.isActive()?intro.get3DTransform(bone,type,partial,base):hold.get3DTransform(bone,type,0,base);
            }
        }
    }
    private AkatsukiThreadPose() {}
}

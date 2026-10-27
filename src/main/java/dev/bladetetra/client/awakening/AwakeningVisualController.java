package dev.bladetetra.client.awakening;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.client.AwakeningIntroClient;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.easteregg.AwakeningPresentationTimeline;
import dev.bladetetra.network.AwakeningPresentationPacket;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Coordinates bounded world-local sessions, never combat or persistent progress. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class AwakeningVisualController {
    public static final net.minecraft.resources.ResourceLocation BACKDROP =
            new net.minecraft.resources.ResourceLocation("blade_tetra","textures/effect/awakened/ritual/blood_moon.png");
    private static final int MAX_SESSIONS=8;
    private static final Map<UUID,AwakeningSession> SESSIONS=new LinkedHashMap<>();
    private static final Map<UUID,AwakeningPresentationPacket> PENDING=new LinkedHashMap<>();
    private static ClientLevel world;
    private static final int[] QUADS={0};
    private static final Matrix4f WORLD_VIEW = new Matrix4f();
    private static final Matrix4f WORLD_PROJECTION = new Matrix4f();
    private static boolean worldReady;
    private static boolean worldEntityPass;
    private static Player renderingPlayer;

    @SubscribeEvent public static void playerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
        renderingPlayer=e.getEntity();
    }
    @SubscribeEvent public static void playerPost(net.minecraftforge.client.event.RenderPlayerEvent.Post e) {
        renderingPlayer=null;
    }

    public static void clear() {
        dev.bladetetra.client.reflection.MirrorRitualResources.clear();
        SESSIONS.clear(); PENDING.clear(); worldReady=false; worldEntityPass=false; renderingPlayer=null; AwakeningIntroClient.cancel();
    }
    private static boolean enabled() {
        return ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()
                && ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get()>0;
    }
    private static void syncWorld() {
        ClientLevel current=Minecraft.getInstance().level;
        if(current!=world) { clear(); world=current; }
    }
    public static void begin(AwakeningPresentationPacket p) {
        syncWorld();
        if(world==null||!enabled()||!p.valid()||!p.dimension().equals(world.dimension().location())) return;
        long age=world.getGameTime()-p.startedAt();
        if(age < -5 || age>=AwakeningPresentationTimeline.DEPART_END) return;
        var old=SESSIONS.get(p.player());
        if(old!=null&&old.data.startedAt()>=p.startedAt()) return;
        var pending=PENDING.get(p.player());
        if(pending!=null&&pending.startedAt()>=p.startedAt()) return;
        if(SESSIONS.size()+PENDING.size()>=MAX_SESSIONS&&!SESSIONS.containsKey(p.player())) return;
        SESSIONS.remove(p.player()); PENDING.put(p.player(),p);
        resolve();
    }
    private static void resolve() {
        if(world==null) return;
        var iter=PENDING.values().iterator();
        while(iter.hasNext()) {
            var p=iter.next(); long age=world.getGameTime()-p.startedAt();
            // Equipment synchronization may arrive just after the one-off presentation packet.
            if(age<0) continue;
            if(age>=20) {iter.remove();continue;}
            Player owner=world.getPlayerByUUID(p.player());
            if(owner==null||!AwakeningSession.matches(p,owner)) continue;
            iter.remove();
            try {
                var model=FloatingBladeRenderer.prepare(owner.getMainHandItem());
                if(model==null) continue; // safe ordinary held rendering
                var session=new AwakeningSession(p,owner,model);
                SESSIONS.put(p.player(),session);
                if(owner==Minecraft.getInstance().player)
                    AwakeningIntroClient.begin(p.soul(),(int)age);
            } catch(RuntimeException ex) {
                LogUtils.getLogger().warn("Cannot prepare awakening model; keeping native blade",ex);
            }
        }
    }
    public static boolean presenting(Player player) {
        var s=SESSIONS.get(player.getUUID());
        return world!=null&&s!=null&&s.valid(world)&&s.visible;
    }
    /** Keeps the native sheath during a visible ritual, including a Sakura dispersed preview. */
    public static boolean keepMountedSheath(net.minecraft.world.item.ItemStack stack) {
        if(world==null || !enabled()) return false;
        for(var session:SESSIONS.values()) {
            if(session.visible && session.valid(world) && stack==session.owner.getMainHandItem())
                return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        syncWorld();
        if(world==null||Minecraft.getInstance().isPaused()) return;
        if(!enabled()) {clear();return;}
        resolve();
        var iter=SESSIONS.values().iterator();
        boolean mirrorLive=false;
        while(iter.hasNext()) {
            var s=iter.next();
            if(!s.valid(world)) {
                if(s.owner==Minecraft.getInstance().player) AwakeningIntroClient.cancel();
                iter.remove(); continue;
            }
            if(s.data.soul()==2) mirrorLive=true;
            int age=(int)s.age(world,0);
            if(age==20||age==45||age==65||age==85||age==113||age==119) sound(s,age);
        }
        if(!mirrorLive)
            dev.bladetetra.client.reflection.MirrorRitualResources.clear();
    }
    private static void sound(AwakeningSession s,int beat) {
        SoundEvent sound;
        if(beat==119) sound=SoundEvents.ARMOR_EQUIP_IRON;
        else if(beat==85) sound=switch(s.data.soul()) {
            case 1->SoundEvents.RESPAWN_ANCHOR_CHARGE;
            case 2->SoundEvents.GLASS_BREAK;
            case 3->SoundEvents.AMETHYST_BLOCK_RESONATE;
            default->SoundEvents.LIGHTNING_BOLT_THUNDER;
        };
        else sound=switch(s.data.soul()) {
            case 1->SoundEvents.AMETHYST_BLOCK_HIT;
            case 2->SoundEvents.AMETHYST_BLOCK_CHIME;
            case 3->SoundEvents.CHERRY_LEAVES_FALL;
            default->SoundEvents.BEACON_POWER_SELECT;
        };
        // Positional, local-only sound, respects the Players volume slider and distance.
        var p=s.owner.position();
        world.playLocalSound(p.x,p.y+1,p.z,sound,SoundSource.PLAYERS,
                beat==85?.65F:.26F,beat==85?.8F:1.1F+s.data.soul()*.07F,false);
    }

    /** Existing bounded session list; local player's mirror wins the single capture budget. */
    public static AwakeningSession mirrorCandidate(float partial) {
        if(world==null||!enabled()) return null;
        var mc=Minecraft.getInstance();AwakeningSession best=null;double distance=Double.MAX_VALUE;
        for(var s:SESSIONS.values()) {
            float age=s.age(world,partial);
            if(s.data.soul()!=2||!s.valid(world)||age<12||age>=112) continue;
            if(s.owner==mc.player) return s;
            double d=s.owner.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition());
            if(d<distance&&d<48*48) {distance=d;best=s;}
        }
        return best;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hideOriginal(RenderOverrideEvent e) {
        if(dev.bladetetra.client.reflection.ReflectionPass.active()) {
            if(!FloatingBladeRenderer.isRendering()) for(var s:SESSIONS.values()) {
                if(dev.bladetetra.client.reflection.MirrorRitualResources.reflecting(s)
                        &&AwakeningVisibility.hide(true,e.getStack()==s.owner.getMainHandItem(),true,false,e.getOriginalTarget()))
                    e.setCanceled(true);
            }
            return;
        }
        if(world==null||!enabled()||FloatingBladeRenderer.isRendering()
                || (!worldEntityPass && renderingPlayer!=null)) return;
        for(var s:SESSIONS.values()) {
            if(AwakeningVisibility.hide(s.valid(world),e.getStack()==s.owner.getMainHandItem(),
                    s.visible,false,e.getOriginalTarget())) {
                if(worldReady && renderingPlayer==s.owner) {
                    if(!e.getOriginalTarget().endsWith("luminous"))
                        s.bladePose.capture(e.getPoseStack().last().pose());
                }
                e.setCanceled(true);
                if(worldReady && renderingPlayer==null && FloatingBladeRenderer.localHandPass(s)) {
                    try { FloatingBladeRenderer.renderFirstPerson(s,e,WORLD_VIEW,WORLD_PROJECTION); }
                    catch(RuntimeException ex) {
                        s.fail(); e.setCanceled(false); AwakeningIntroClient.cancel();
                        LogUtils.getLogger().warn("First-person awakening failed; restoring native pass",ex);
                    }
                }
                return;
            }
        }
    }
    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        syncWorld();
        if(world==null||!enabled()) return;
        if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY) {
            WORLD_VIEW.set(e.getPoseStack().last().pose());
            WORLD_PROJECTION.set(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
            worldReady=true; worldEntityPass=true; renderingPlayer=null;
            int quality=ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get();
            QUADS[0]=quality==0?512:quality==1?1536:3072;
            double max=Math.min(64,ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get());
            for(var s:SESSIONS.values()) {
                s.bladePose.reset();
                Vec3 p=FloatingBladeRenderer.position(s,e.getPartialTick(),s.age(world,e.getPartialTick()));
                s.visible=s.valid(world)&&p.distanceToSqr(e.getCamera().getPosition())<max*max
                        && e.getFrustum().isVisible(new AABB(p.x-4,p.y-4,p.z-4,p.x+4,p.y+8,p.z+4));
            }
            return;
        }
        if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            for(var s:SESSIONS.values()) if(s.data.soul()==2&&s.visible&&s.valid(world)) {
                try {dev.bladetetra.client.reflection.MirrorRitualResources.draw(s,e.getPoseStack().last().pose(),e.getCamera().getPosition(),e.getPartialTick());}
                catch(RuntimeException failure) {
                    s.fail();dev.bladetetra.client.reflection.MirrorRitualResources.clear();
                    if(s.owner==Minecraft.getInstance().player) AwakeningIntroClient.cancel();
                    LogUtils.getLogger().warn("Mirror surface failed; restoring native held blade",failure);
                }
            }
            return;
        }
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        worldEntityPass=false; renderingPlayer=null;
        var mc=Minecraft.getInstance(); var buffers=mc.renderBuffers().bufferSource();
        PoseStack pose=e.getPoseStack(); Vec3 eye=e.getCamera().getPosition();
        pose.pushPose();
        try {
            pose.translate(-eye.x,-eye.y,-eye.z);
            for(var s:SESSIONS.values()) {
                if(!s.visible||!s.valid(world)) continue;
                try {
                    float tick=s.age(world,e.getPartialTick());
                    FloatingBladeRenderer.render(s,pose,buffers,e.getPartialTick(),tick);
                    // World name for observers; the owner already has a dedicated HUD.
                    if(s.owner!=mc.player&&tick>=85&&tick<100) {
                        Vec3 at=FloatingBladeRenderer.position(s,e.getPartialTick(),tick);
                        pose.pushPose();
                        try {
                            pose.translate(at.x,at.y+1.05,at.z); pose.mulPose(e.getCamera().rotation());
                            pose.scale(-.018F,-.018F,.018F);
                            var name=Component.translatable(AwakeningPresentationTimeline.nameKey(s.data.soul()));
                            mc.font.drawInBatch(name,-mc.font.width(name)/2F,0,
                                    0xEE000000|AwakeningPresentationTimeline.tint(s.data.soul()),false,
                                    pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,0xF000F0);
                        } finally {pose.popPose();}
                    }
                } catch(RuntimeException ex) {
                    s.fail();
                    if(s.owner==mc.player) AwakeningIntroClient.cancel();
                    LogUtils.getLogger().warn("Awakening render failed; restoring native held blade",ex);
                }
            }
            buffers.endBatch();
        } finally {pose.popPose();}
    }
    /** One optional full-moon texture batch, sharing the existing renderer's GL state and budget. */
    public static void drawBackdrop(BufferBuilder buffer,Matrix4f matrix,Vec3 eye,
            float partial,float intensity) {
        if(world==null||!enabled()) return;
        for(var s:SESSIONS.values()) {
            if(s.data.soul()!=1||!s.visible||!s.valid(world)||QUADS[0]<=0) continue;
            var frame=new RitualEffectBatch(buffer,matrix,eye,s,partial,intensity,
                    ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get(),QUADS);
            float alpha=frame.alpha*frame.window(20,45,85,100);
            if(alpha<=0) continue;
            --QUADS[0];
            Vec3 at=frame.local(0,.3,.7), dx=frame.right.scale(1.3), dy=new Vec3(0,1.3,0);
            dev.bladetetra.client.vfx.render.VfxPrimitives.texturedQuad(buffer,matrix,
                    at.subtract(dx).add(dy),at.subtract(dx).subtract(dy),
                    at.add(dx).subtract(dy),at.add(dx).add(dy),0,0,1,1,alpha,0xFFFFFF);
        }
    }

    /** Invoked inside AwakenedBladeVfxClient's already-open atlas pass. */
    public static void drawBatch(BufferBuilder buffer,Matrix4f matrix,Vec3 eye,
            float partial,float intensity,int soul) {
        if(world==null||!enabled()) return;
        int quality=ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get();
        for(var s:SESSIONS.values()) {
            if(s.data.soul()!=soul||!s.visible||!s.valid(world)) continue;
            var frame=new RitualEffectBatch(buffer,matrix,eye,s,partial,intensity,quality,QUADS);
            AwakeningVisualProfile.get(soul).draw(frame);
        }
    }
    private AwakeningVisualController() {}
}

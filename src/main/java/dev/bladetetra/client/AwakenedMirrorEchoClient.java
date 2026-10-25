package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.network.AwakenedMirrorEchoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Archived real PlayerModel bone poses, not fake player entities.
 * This samples a hit-frame from the actual player renderer, then displays
 * that same skinned articulated pose at the server-authorized past position.
 * It never changes the real player's animation or game state.
 *
 * A full multi-frame SlashBlade/player-animation clip recorder is separate.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AwakenedMirrorEchoClient {
    private static final int MAX_ARCHIVED = 48;
    private static final int MAX_ECHOES = 12;
    private static final Map<Integer, Capture> ARCHIVE = new HashMap<>();
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static ClientLevel world;
    private static int ticks;

    public static void receive(AwakenedMirrorEchoPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()
                || packet.action() < 0 || packet.action() > 1
                || !Double.isFinite(packet.x()) || !Double.isFinite(packet.y())
                || !Double.isFinite(packet.z()) || !Float.isFinite(packet.yaw()))
            return;
        double range = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        if (mc.player != null && mc.player.distanceToSqr(
                packet.x(), packet.y(), packet.z()) > range * range) return;
        if (mc.level != world) {
            ARCHIVE.clear();
            GHOSTS.clear();
            world = mc.level;
        }
        Entity entity = mc.level.getEntity(packet.entityId());
        if (!(entity instanceof AbstractClientPlayer player)) return;

        if (packet.action() == AwakenedMirrorEchoPacket.CAPTURE) {
            // Local first-person players do not always emit RenderPlayerEvent.
            // Take the currently cached renderer pose immediately, then replace
            // it with the live post-render snapshot if available this frame.
            var renderer = mc.getEntityRenderDispatcher().getRenderer(player);
            if (!(renderer instanceof PlayerRenderer playerRenderer)) return;
            ARCHIVE.put(packet.cutId(), new Capture(player.getId(),
                    BonePose.copyOf(playerRenderer.getModel()), ticks + 120, true));
            if (ARCHIVE.size() > MAX_ARCHIVED) evictOldest();
            return;
        }
        Capture sample = ARCHIVE.get(packet.cutId());
        if (sample == null || sample.expires < ticks
                || sample.owner != packet.entityId()) return;
        GHOSTS.add(new Ghost(player.getId(), sample.pose,
                new Vec3(packet.x(), packet.y(), packet.z()), packet.yaw()));
        while (GHOSTS.size() > MAX_ECHOES) GHOSTS.remove(0);
    }

    @SubscribeEvent
    public static void onPlayerRendered(RenderPlayerEvent.Post event) {
        if (ARCHIVE.isEmpty()) return;
        int playerId = event.getEntity().getId();
        PlayerModel<?> model = event.getRenderer().getModel();
        for (Capture snapshot : ARCHIVE.values()) {
            if (snapshot.owner == playerId && snapshot.preferLive && snapshot.expires > ticks) {
                snapshot.pose = BonePose.copyOf(model);
                snapshot.preferLive = false;
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) {
            world = mc.level;
            ARCHIVE.clear();
            GHOSTS.clear();
            ticks = 0;
        }
        if (mc.isPaused()) return;
        ++ticks;
        ARCHIVE.values().removeIf(value -> value.expires <= ticks);
        Iterator<Ghost> iter = GHOSTS.iterator();
        while (iter.hasNext()) {
            if (++iter.next().age >= 19) iter.remove();
        }
    }

    @SubscribeEvent
    public static void onWorldRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || GHOSTS.isEmpty()
                || !ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world) return;
        PoseStack matrices = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float intensity = ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();

        for (Ghost ghost : GHOSTS) {
            Entity entity = mc.level.getEntity(ghost.owner);
            if (!(entity instanceof AbstractClientPlayer player)) continue;
            if (player.distanceToSqr(ghost.position) > 576D) continue;
            var renderer = mc.getEntityRenderDispatcher().getRenderer(player);
            if (!(renderer instanceof PlayerRenderer playerRenderer)) continue;
            PlayerModel<?> model = playerRenderer.getModel();
            BonePose live = BonePose.copyOf(model);
            try {
                ghost.pose.apply(model);
                matrices.pushPose();
                matrices.translate(ghost.position.x - camera.x,
                        ghost.position.y - camera.y + ghost.age * 0.012D,
                        ghost.position.z - camera.z);
                matrices.mulPose(Axis.YP.rotationDegrees(180.0F - ghost.yaw));
                matrices.scale(-1, -1, 1);
                matrices.translate(0, -1.501F, 0);
                float fade = Math.min(1.0F, (ghost.age + event.getPartialTick()) / 3.0F)
                        * Math.min(1.0F, (19 - ghost.age - event.getPartialTick()) / 7.0F);
                RenderType renderType = RenderType.entityTranslucent(
                        player.getSkinTextureLocation());
                model.renderToBuffer(matrices,
                        buffers.getBuffer(renderType), 0x00F000F0,
                        OverlayTexture.NO_OVERLAY,
                        0.55F, 0.78F, 1.0F, fade * 0.5F * intensity);
                buffers.endBatch(renderType);
                matrices.popPose();
            } finally {
                live.apply(model);
            }
        }
    }

    private static void evictOldest() {
        Integer oldest = null;
        int deadline = Integer.MAX_VALUE;
        for (var pair : ARCHIVE.entrySet()) {
            if (pair.getValue().expires < deadline) {
                oldest = pair.getKey();
                deadline = pair.getValue().expires;
            }
        }
        if (oldest != null) ARCHIVE.remove(oldest);
    }

    private static final class Capture {
        final int owner;
        BonePose pose;
        final int expires;
        boolean preferLive;

        Capture(int owner, BonePose pose, int expires, boolean preferLive) {
            this.owner = owner;
            this.pose = pose;
            this.expires = expires;
            this.preferLive = preferLive;
        }
    }

    private static final class Ghost {
        final int owner;
        final BonePose pose;
        final Vec3 position;
        final float yaw;
        int age;

        Ghost(int owner, BonePose pose, Vec3 position, float yaw) {
            this.owner = owner;
            this.pose = pose;
            this.position = position;
            this.yaw = yaw;
        }
    }

    /** Includes all visible Humanoid and PlayerModel clothing bones. */
    private record BonePose(PartPose head, PartPose hat, PartPose torso,
            PartPose rightArm, PartPose leftArm, PartPose rightLeg, PartPose leftLeg,
            PartPose jacket, PartPose rightSleeve, PartPose leftSleeve,
            PartPose rightPants, PartPose leftPants) {
        static BonePose copyOf(PlayerModel<?> model) {
            return new BonePose(model.head.storePose(), model.hat.storePose(),
                    model.body.storePose(), model.rightArm.storePose(),
                    model.leftArm.storePose(), model.rightLeg.storePose(),
                    model.leftLeg.storePose(), model.jacket.storePose(),
                    model.rightSleeve.storePose(), model.leftSleeve.storePose(),
                    model.rightPants.storePose(), model.leftPants.storePose());
        }

        void apply(PlayerModel<?> model) {
            model.head.loadPose(head);
            model.hat.loadPose(hat);
            model.body.loadPose(torso);
            model.rightArm.loadPose(rightArm);
            model.leftArm.loadPose(leftArm);
            model.rightLeg.loadPose(rightLeg);
            model.leftLeg.loadPose(leftLeg);
            model.jacket.loadPose(jacket);
            model.rightSleeve.loadPose(rightSleeve);
            model.leftSleeve.loadPose(leftSleeve);
            model.rightPants.loadPose(rightPants);
            model.leftPants.loadPose(leftPants);
        }
    }

    private AwakenedMirrorEchoClient() {}
}

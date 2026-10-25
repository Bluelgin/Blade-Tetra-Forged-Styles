package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.network.AwakenedMirrorEchoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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
 * Records a bounded six-frame post-hit skeletal clip from the actual renderer,
 * then plays back the original skinned articulated action at the server-
 * authorized past position, with no new damage-authoritative entity.
 * It never changes the real player's animation or game state.
 *
 * The full player-animation library's underlying animation timeline is not
 * serialized; short visible render poses are sampled in the original scene.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AwakenedMirrorEchoClient {
    private static final int MAX_ARCHIVED = 48;
    private static final int MAX_ECHOES = 12;
    private static final int CLIP_FRAMES = 6;
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
                    BonePose.copyOf(playerRenderer.getModel()),
                    player.getMainHandItem().copy(), ticks + 120));
            if (ARCHIVE.size() > MAX_ARCHIVED) evictOldest();
            return;
        }
        Capture sample = ARCHIVE.get(packet.cutId());
        if (sample == null || sample.expires < ticks
                || sample.owner != packet.entityId()) return;
        GHOSTS.add(new Ghost(player.getId(), List.copyOf(sample.frames),
                sample.weapon.copy(), new Vec3(packet.x(), packet.y(), packet.z()),
                packet.yaw()));
        while (GHOSTS.size() > MAX_ECHOES) GHOSTS.remove(0);
    }

    @SubscribeEvent
    public static void onPlayerRendered(RenderPlayerEvent.Post event) {
        if (ARCHIVE.isEmpty()) return;
        int playerId = event.getEntity().getId();
        PlayerModel<?> model = event.getRenderer().getModel();
        for (Capture snapshot : ARCHIVE.values()) {
            if (snapshot.owner == playerId && snapshot.expires > ticks
                    && snapshot.lastFrameTick != ticks
                    && snapshot.frames.size() < CLIP_FRAMES) {
                snapshot.frames.add(BonePose.copyOf(model));
                snapshot.lastFrameTick = ticks;
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
                int frame = Math.min(ghost.frames.size() - 1,
                        (int) ((ghost.age + event.getPartialTick()) / 3.0F));
                ghost.frames.get(frame).apply(model);
                matrices.pushPose();
                try {
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
                    // The captured blade belongs to the historical clip, not
                    // the player's *current* main hand. Follow real right/left
                    // arm bones, reusing native ItemRenderer and Tetra material.
                    if (!ghost.weapon.isEmpty() && intensity > 0.0F) {
                        matrices.pushPose();
                        try {
                            HumanoidArm arm = player.getMainArm();
                            model.translateToHand(arm, matrices);
                            matrices.mulPose(Axis.XP.rotationDegrees(-90.0F));
                            matrices.mulPose(Axis.YP.rotationDegrees(180.0F));
                            matrices.translate((arm == HumanoidArm.LEFT ? -1 : 1) / 16.0F,
                                    0.125F, -0.625F);
                            mc.getItemRenderer().renderStatic(ghost.weapon,
                                    arm == HumanoidArm.LEFT
                                            ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                                            : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                                    0x00F000F0, OverlayTexture.NO_OVERLAY,
                                    matrices, buffers, mc.level, player.getId());
                        } finally {
                            matrices.popPose();
                        }
                    }
                } finally {
                    matrices.popPose();
                }
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
        final List<BonePose> frames = new ArrayList<>(CLIP_FRAMES);
        final int expires;
        final ItemStack weapon;
        int lastFrameTick = -1;

        Capture(int owner, BonePose initial, ItemStack weapon, int expires) {
            this.owner = owner;
            this.weapon = weapon;
            this.frames.add(initial);
            this.expires = expires;
        }
    }

    private static final class Ghost {
        final int owner;
        final List<BonePose> frames;
        final ItemStack weapon;
        final Vec3 position;
        final float yaw;
        int age;

        Ghost(int owner, List<BonePose> frames, ItemStack weapon,
                Vec3 position, float yaw) {
            this.owner = owner;
            this.frames = frames;
            this.weapon = weapon;
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

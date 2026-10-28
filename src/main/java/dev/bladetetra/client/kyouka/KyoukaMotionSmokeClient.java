package dev.bladetetra.client.kyouka;

import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import dev.bladetetra.client.awakening.FloatingBladeRenderer;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import org.joml.Matrix4f;

/** Real resource/MMD/OBJ client smoke, without a world, player entity or RenderPlayerEvent. */
@Mod.EventBusSubscriber(modid = "blade_tetra", value = Dist.CLIENT)
public final class KyoukaMotionSmokeClient {
    private static boolean opened;
    private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (!Boolean.getBoolean("blade_tetra.kyoukaMotionSmoke") || e.phase != TickEvent.Phase.END || opened) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() == null && mc.screen != null && ++ticks > 40) {
            opened = true; mc.setScreen(new Review());
        }
    }
    private static final class Review extends Screen {
        final NativeBladeMotionSampler sampler = new NativeBladeMotionSampler();
        final net.minecraft.world.item.ItemStack blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        FloatingBladeRenderer.Prepared model;
        int frames, checks;
        Review() { super(Component.literal("Independent VMD sampler smoke")); }
        @Override protected void init() {
            model = FloatingBladeRenderer.prepare(blade);
            require(model != null, "real Tetra native OBJ appearance prepares");
            for (String name : new String[]{"combo_a1","combo_a2","combo_a3","combo_b1","combo_b2",
                    "combo_b3","combo_b4","combo_b5","combo_b6","combo_b7"}) {
                var id = new ResourceLocation("slashblade",name);
                require(ComboStateRegistry.REGISTRY.get().containsKey(id), "native combo registered: "+name);
                var first = sampler.sample(id, 1);
                require(first != null && first.isFinite(), "independent hardpointA samples: "+name);
                var second = sampler.sample(id, 1);
                require(first.equals(second, .0001F), "deterministic repeated native sampling: "+name);
            }
            require(sampler.sample(new ResourceLocation("blade_tetra","missing_motion"),1)==null,
                    "unknown custom motion safely declines");
            var before = sampler.sample(ComboStateRegistry.COMBO_A1.getId(),3);
            sampler.reset();
            require(before.equals(sampler.sample(ComboStateRegistry.COMBO_A1.getId(),3), .0001F),
                    "sampler rebuild preserves native pose");
        }
        private void require(boolean condition, String message) {
            if (!condition) throw new IllegalStateException(message);
            checks++;
        }
        @Override public void render(GuiGraphics g, int mx, int my, float partial) {
            g.fill(0,0,width,height,0xFF142334);
            g.drawCenteredString(font,"Native VMD / hardpointA - one sample, three Tetra blades",width/2,15,0xE0EEFF);
            g.drawCenteredString(font,"Off-world automated render - world/multiplayer acceptance remains pending",width/2,29,0xAFC2DD);
            var matrix = sampler.sample(ComboStateRegistry.COMBO_A1.getId(),3);
            require(matrix!=null,"render-frame native sample");
            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            for(int i=0;i<3;i++) {
                var pose=g.pose();pose.pushPose();
                try {
                    pose.translate(width*(i+1)/4.0,height*.78,100);
                    pose.scale(60,-60,60);pose.mulPose(Axis.YP.rotationDegrees(180));
                    pose.mulPoseMatrix(matrix);pose.last().normal().set(pose.last().pose()).invert().transpose();
                    FloatingBladeRenderer.renderMirror(model,blade,pose,buffers);
                } finally {pose.popPose();}
            }
            buffers.endBatch();
            if(++frames==20) {
                try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                    Path out=Minecraft.getInstance().gameDirectory.toPath().resolve("three-mirrors.png");
                    image.writeToFile(out);
                    LogUtils.getLogger().info("KYOUKA_MOTION_SMOKE_PASS: {} checks; real client resources, VMD hardpointA and shared three-blade OBJ pass; {}",checks,out);
                } catch(Exception failure) {throw new IllegalStateException(failure);}
                Minecraft.getInstance().stop();
            }
        }
    }
    private KyoukaMotionSmokeClient() {}
}

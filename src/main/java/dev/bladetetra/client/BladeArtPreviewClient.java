package dev.bladetetra.client;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Opt-in, isolated real-render gallery. Normal releases never auto-open it. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class BladeArtPreviewClient {
    private static boolean started, opened;
    private static int readyTicks;
    private static List<BladeArtPreviewSamples.Sample> samples;
    private static final String WORLD = "blade-art-review";

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("blade_tetra.artPreview") || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true;
            if (Files.exists(mc.gameDirectory.toPath().resolve("saves/"+WORLD+"/level.dat"))) {
                mc.createWorldOpenFlows().loadLevel(mc.screen, WORLD);
            } else {
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings("拔刀剑美术验收", GameType.CREATIVE, false,
                                Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(158L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
        }
        if (!opened && mc.level != null && mc.player != null && mc.screen == null && ++readyTicks > 40) {
            opened = true;
            try {
                samples = BladeArtPreviewSamples.create();
                // Place the exact gallery stacks in this isolated world's inventory.
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                if (server != null) server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(id);
                    if (player != null) {
                        // InventoryScreen redirects creative players to a different screen.
                        // Use the real 36-slot survival inventory for this opt-in benchmark.
                        if (Boolean.getBoolean("blade_tetra.artInventoryBenchmark"))
                            player.setGameMode(GameType.SURVIVAL);
                        for (int i=0; i<samples.size() && i<36; i++) player.getInventory().setItem(i, samples.get(i).stack().copy());
                        player.inventoryMenu.broadcastChanges();
                    }
                });
                mc.setScreen(new Gallery(true));
                LogUtils.getLogger().info("BLADE_ART_PREVIEW_READY: {} actual inventory samples", samples.size());
            } catch (RuntimeException error) {
                LogUtils.getLogger().error("BLADE_ART_PREVIEW_FAIL", error);
                throw error;
            }
        }
    }

    @SubscribeEvent public static void key(InputEvent.Key event) {
        if (Boolean.getBoolean("blade_tetra.artPreview") && event.getKey()==297 && event.getAction()==1
                && samples!=null && Minecraft.getInstance().screen==null) Minecraft.getInstance().setScreen(new Gallery(false));
    }

    private static final class Gallery extends Screen {
        int page, frames;
        boolean autoCapture;
        Gallery(boolean capture) { super(Component.literal("拔刀剑 · 美术验收")); autoCapture=capture; }
        @Override protected void init() {
            addRenderableWidget(Button.builder(Component.literal("上一页"), b -> {page=Math.floorMod(page-1,samples.size()/12); frames=0;})
                    .bounds(width/2-106, height-25, 64, 20).build());
            addRenderableWidget(Button.builder(Component.literal("下一页"), b -> {page=(page+1)%(samples.size()/12); frames=0;})
                    .bounds(width/2-32, height-25, 64, 20).build());
            addRenderableWidget(Button.builder(Component.literal("进入验收"), b -> onClose())
                    .bounds(width/2+42, height-25, 80, 20).build());
            addRenderableWidget(Button.builder(Component.literal("刀镡细看"),b->minecraft.setScreen(new GuardDetails(false)))
                    .bounds(width-105,height-25,90,20).build());
        }
        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.fill(0,0,width,height,0xFF10151C);
            String heading=switch(page) {case 0->"改装可视化 · 同刀对照";
                case 1->"部件轮廓 / 特殊材料 / 赤月整套外观";default->"通用刀镡重制 · 三种轮廓与材料对照";};
            g.drawCenteredString(font,heading,width/2,10,0xF2DFC0);
            g.drawCenteredString(font, "实际物品栏渲染 | 大图 + 原尺寸图标 | ESC进入世界，F8重新打开", width/2, 25, 0xA2AFBC);
            int cellW=(width-32)/4, cellH=(height-76)/3;
            for(int i=0;i<12;i++) {
                var sample=samples.get(page*12+i);
                int x=16+(i%4)*cellW, y=43+(i/4)*cellH;
                g.fill(x+2,y+2,x+cellW-4,y+cellH-4,0xFF202933);
                g.drawCenteredString(font,sample.label(),x+cellW/2,y+7,0xF6F0E5);
                int size=Math.min(cellH-44,cellW-30);
                // Nodachi's native icon extends past the usual 16px item box.
                if (sample.label().startsWith("断岳")) size=Math.round(size*.86F);
                var poses=g.pose();
                g.flush(); poses.pushPose();
                poses.translate(x+(cellW-size)/2F,y+20,0);
                poses.scale(size/16F,size/16F,1);
                g.renderItem(sample.stack(),0,0); g.flush(); poses.popPose();
                g.renderItem(sample.stack(),x+cellW-24,y+cellH-24);
                g.drawCenteredString(font,sample.detail(),x+cellW/2,y+cellH-15,0xAEB9C3);
            }
            super.render(g,mouseX,mouseY,delta);
            g.flush();
            if(autoCapture && ++frames==35) {
                try {
                    Path out=Path.of(System.getProperty("blade_tetra.artPreviewOutput")); Files.createDirectories(out);
                    try(var image=Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                        image.writeToFile(out.resolve("inventory-page-"+(page+1)+".png"));
                    }
                    LogUtils.getLogger().info("BLADE_ART_PREVIEW_CAPTURED: page {}",page+1);
                    if(page+1<samples.size()/12) {page++; frames=0;}
                    else {autoCapture=false; page=2; minecraft.setScreen(new GuardDetails(true));}
                } catch(java.io.IOException error) {throw new IllegalStateException("Art preview export failed",error);}
            }
        }
        @Override public boolean isPauseScreen() { return true; }
    }

    /** Front view of the exact world guard meshes, rendered through the normal material hook. */
    private static final class GuardDetails extends Screen {
        private final boolean capture;
        private int frames;
        private final java.util.Map<Integer,mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject> models=new java.util.HashMap<>();
        GuardDetails(boolean capture) {super(Component.literal("刀镡正面验收"));this.capture=capture;}
        @Override protected void init() {
            addRenderableWidget(Button.builder(Component.literal("返回物品栏预览"),b->minecraft.setScreen(new Gallery(false)))
                    .bounds(width/2-75,height-25,150,20).build());
        }
        @Override public void render(GuiGraphics g,int mx,int my,float delta) {
            g.fill(0,0,width,height,0xFF10151C);
            g.drawCenteredString(font,"通用刀镡正面 · 游戏中的实际世界模型",width/2,10,0xF2DFC0);
            g.drawCenteredString(font,"椭圆 / 四叶镂空 / 切角八角 | 铁 / 黄金 / 下界合金",width/2,25,0xA2AFBC);
            int cellW=(width-32)/3,cellH=(height-76)/3;
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
            for(int i=0;i<9;i++) {
                int index=24+(i/3)*4+(i%3);
                var sample=samples.get(index);
                int x=16+(i%3)*cellW,y=43+(i/3)*cellH;
                g.fill(x+2,y+2,x+cellW-4,y+cellH-4,0xFF202933);
                g.drawCenteredString(font,sample.label(),x+cellW/2,y+7,0xF6F0E5);
                var model=models.computeIfAbsent(index,n->worldGuard(sample.stack()));
                float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
                for(var f:model.groupObjects.get(0).faces) for(var v:f.vertices) {
                    minX=Math.min(minX,v.x);maxX=Math.max(maxX,v.x);
                    minY=Math.min(minY,v.y);maxY=Math.max(maxY,v.y);minZ=Math.min(minZ,v.z);maxZ=Math.max(maxZ,v.z);
                }
                float scale=(cellH-36)/Math.max(maxY-minY,maxZ-minZ);
                g.flush();g.pose().pushPose();g.pose().translate(x+cellW/2F,y+cellH/2F+5,150);
                g.pose().scale(scale,-scale,scale);
                g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
                g.pose().translate(-(minX+maxX)/2,-(minY+maxY)/2,-(minZ+maxZ)/2);
                mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState.resetCol();
                mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState.renderOverrided(
                        sample.stack(),model,"blade",mods.flammpfeil.slashblade.init.DefaultResources.resourceDefaultTexture,
                        g.pose(),g.bufferSource(),net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                        mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState::getSlashBladeBlend,false);
                g.flush();g.pose().popPose();
            }
            super.render(g,mx,my,delta);g.flush();
            if(capture && ++frames==35) {
                try(var image=Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                    image.writeToFile(Path.of(System.getProperty("blade_tetra.artPreviewOutput")).resolve("guard-fronts.png"));
                    LogUtils.getLogger().info("BLADE_ART_GUARD_FRONTS_CAPTURED");
                }catch(java.io.IOException e){throw new IllegalStateException(e);}
                if(Boolean.getBoolean("blade_tetra.artInventoryBenchmark"))
                    minecraft.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(minecraft.player));
                else {Gallery gallery=new Gallery(false);gallery.page=2;minecraft.setScreen(gallery);}
            }
        }
        private static mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject worldGuard(net.minecraft.world.item.ItemStack stack) {
            var location=stack.getCapability(dev.bladetetra.item.ModularSlashBladeItem.BLADESTATE)
                    .map(s->s.getModel().orElseThrow()).orElseThrow();
            var source=mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager.getInstance().getModel(location);
            var model=ForgedGuardModel.forProfile(source,dev.bladetetra.visual.MaterialAppearance.fromStack(stack).tsubaProfile());
            var group=model.groupObjects.stream().filter(t->t.name.equals("blade")).findFirst().orElseThrow();
            return RuntimeWavefrontViewFactory.create("blade",group.faces.stream().filter(InventoryGuardGeometry::isGuard).toList());
        }
        @Override public boolean isPauseScreen(){return true;}
    }
}

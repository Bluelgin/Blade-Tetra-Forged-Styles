package dev.bladetetra.client.reflection;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;

/** Reproducible real-client gate. No behavior unless explicitly launched with -PmirrorPrototype. */
public final class MirrorPrototypeScene {
    private static boolean started,prepared;
    private static int ticks,capture,waiting,compatCaptures;
    private static boolean reloadStarted,reloadDone;
    private static boolean review() {return Boolean.getBoolean("blade_tetra.mirrorRitualReview");}
    private static boolean enabled() {return review()||Boolean.getBoolean("blade_tetra.mirrorPrototype");}
    public static boolean ready() { return prepared; }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(!enabled()||e.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance();
        if(!started&&mc.screen instanceof TitleScreen) {
            started=true; String name="mirror-prototype";
            if(Files.exists(mc.gameDirectory.toPath().resolve("saves/"+name+"/level.dat")))
                mc.createWorldOpenFlows().loadLevel(mc.screen,name);
            else {
                var rules=new GameRules(); rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel(name,
                        new LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                        new WorldOptions(158L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
        }
        if(started&&!prepared&&mc.screen!=null
                &&mc.screen.getTitle().getString().toLowerCase(java.util.Locale.ROOT).contains("experimental")) {
            for(var child:mc.screen.children()) {
                if(child instanceof net.minecraft.client.gui.components.Button button&&button.active
                        &&button.getMessage().getString().equalsIgnoreCase("Proceed")) {
                    LogUtils.getLogger().info("MIRROR_TEST_WORLD_PROCEED");button.onPress();return;
                }
            }
        }
        if(mc.level==null||mc.player==null) return;
        if(mc.screen!=null) {
            if(++waiting%100==0) {
                LogUtils.getLogger().info("MIRROR_WAITING_SCREEN {}",mc.screen.getClass().getName());capture=-waiting;
            }
            if(waiting>600) mc.stop();
            return;
        }
        if(!prepared) {
            prepared=true;var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            server.execute(()-> {
                var level=server.overworld();var player=server.getPlayerList().getPlayer(id);
                player.teleportTo(0,-60,-1);player.setYRot(0);player.setXRot(0);
                for(int x=-5;x<=5;x++) for(int y=-60;y<=-56;y++) {
                    if(x!=-5&&x!=5&&y!=-56) continue;
                    level.setBlockAndUpdate(new BlockPos(x,y,-4),x<0?Blocks.RED_CONCRETE.defaultBlockState():Blocks.GOLD_BLOCK.defaultBlockState());
                }
                for(int y=-60;y<=-57;y++) level.setBlockAndUpdate(new BlockPos(-2,y,-2),Blocks.EMERALD_BLOCK.defaultBlockState());
                level.getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class,
                        new net.minecraft.world.phys.AABB(-5,-61,-6,5,-54,5))
                        .forEach(net.minecraft.world.entity.Entity::discard);
                var cow=net.minecraft.world.entity.EntityType.COW.create(level);
                if(cow!=null) {cow.setNoAi(true);cow.moveTo(2,-60,-2,30,0);level.addFreshEntity(cow);}
                level.setDayTime(6000);
            });
            mc.options.renderDistance().set(6);mc.options.pauseOnLostFocus=false;
            mc.options.hideGui=true;
        }
        ++ticks;
        if(ticks>80&&ticks<240) {
            double x=Math.sin((ticks-80)*.025)*1.2;
            mc.player.setPos(x,-60,-1);mc.player.setYRot(0);mc.player.setXRot(0);
        }
        if(!review()&&(ticks==100||ticks==140||ticks==180||ticks==220)) capture=ticks;
        if(review()) reviewTick(mc);
        if(ticks==(review()?980:260)) {
            LogUtils.getLogger().info("{} finished (visual review required)",review()?"MIRROR_RITUAL_REVIEW":"MIRROR_PROTOTYPE_SCENE");
            mc.stop();
        }
    }
    private static void preview(Minecraft mc) {
        var id=mc.player.getUUID();var server=mc.getSingleplayerServer();
        server.execute(()->{
            var player=server.getPlayerList().getPlayer(id);
            var blade=dev.bladetetra.registry.ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            var module=dev.bladetetra.item.ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE;
            se.mickelus.tetra.items.modular.IModularItem.putModuleInSlot(blade,
                    dev.bladetetra.item.ModularSlashBladeItem.INSCRIPTION_SLOT,module,module+"_material",
                    "awakened_soul_inscription/proudsoul_sphere");
            se.mickelus.tetra.items.modular.IModularItem.updateIdentifier(blade);
            dev.bladetetra.visual.SayaPresetSkin.apply(blade,dev.bladetetra.visual.SayaPresetSkin.KYOUKA);
            if(!dev.bladetetra.easteregg.KyoukaAwakening.unlockForDebug(blade)) throw new IllegalStateException("Review inscription invalid");
            player.getInventory().selected=0;player.getInventory().setItem(0,blade);player.inventoryMenu.broadcastChanges();
        });
        mc.player.getInventory().selected=0;
    }
    private static void startRitual(Minecraft mc) {
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
        server.execute(()->dev.bladetetra.easteregg.AwakeningPresentationService.success(server.getPlayerList().getPlayer(id),
                dev.bladetetra.easteregg.SoulLegacyState.Legacy.KYOUKA));
        LogUtils.getLogger().info("MIRROR_RITUAL_REVIEW_START tick={} quality={}",ticks,
                dev.bladetetra.config.ClientVisualConfig.MIRROR_REFLECTION_QUALITY.get());
    }
    private static void reviewTick(Minecraft mc) {
        if(ticks==20||ticks==200||ticks==360||ticks==520||ticks==700||ticks==820) preview(mc);
        if(ticks==60||ticks==220||ticks==380||ticks==540||ticks==720||ticks==840) startRitual(mc);
        if(ticks==205) {
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);mc.options.fov().set(95);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1000,600);
        }
        if(ticks==365) {compatCaptures=MirrorRitualResources.captureCount();mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);dev.bladetetra.config.ClientVisualConfig.MIRROR_REFLECTION_QUALITY.set(0);}
        if(ticks==525) dev.bladetetra.config.ClientVisualConfig.MIRROR_REFLECTION_QUALITY.set(1);
        if(ticks==575) {
            mc.player.getInventory().selected=1;
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            server.execute(()->{var p=server.getPlayerList().getPlayer(id);p.getInventory().selected=1;p.inventoryMenu.broadcastChanges();});
        }
        if(ticks==580) {
            if(MirrorRitualResources.hasCapture()||MirrorRitualResources.targetCount()!=0) throw new IllegalStateException("Target retained after slot abort");
            LogUtils.getLogger().info("MIRROR_REVIEW_ABORT_CLEAN {}",MirrorRitualResources.status());
        }
        if(ticks==705) dev.bladetetra.config.ClientVisualConfig.MIRROR_REFLECTION_QUALITY.set(3);
        if(ticks==760&&!reloadStarted) {
            reloadStarted=true;mc.reloadResourcePacks().thenRun(()->mc.execute(()->{
                reloadDone=true;
                if(MirrorRitualResources.targetCount()!=0) throw new IllegalStateException("Target retained after resource reload");
                LogUtils.getLogger().info("MIRROR_REVIEW_RELOAD_DONE {}",MirrorRitualResources.status());
            }));
        }
        if(ticks==820&&reloadStarted&&!reloadDone) {ticks=819;return;}
        for(int start:new int[]{60,220,380,540,720,840})
            if(ticks==start+24||ticks==start+56||ticks==start+79||ticks==start+92||ticks==start+105) capture=ticks;
        if(ticks==190&&MirrorRitualResources.captureCount()==0) throw new IllegalStateException("No real world reflection captured during native ritual");
        if(ticks==510&&MirrorRitualResources.captureCount()!=compatCaptures) throw new IllegalStateException("Compatibility mode performed world captures");
        if(ticks==190||ticks==350||ticks==510||ticks==975) {
            if(MirrorRitualResources.targetCount()!=0) throw new IllegalStateException("Target retained after normal end");
            LogUtils.getLogger().info("MIRROR_REVIEW_LIFECYCLE tick={} {}",ticks,MirrorRitualResources.status());
        }
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e) {
        if(!enabled()||e.phase!=TickEvent.Phase.END||capture==0) return;
        var mc=Minecraft.getInstance();
        try {
            var out=mc.gameDirectory.toPath().resolve("evidence");Files.createDirectories(out);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(out.resolve((review()?"ritual-":"mirror-")+capture+".png")); }
            LogUtils.getLogger().info("{}_CAPTURE {}",review()?"MIRROR_RITUAL_REVIEW":"MIRROR_PROTOTYPE",capture);capture=0;
        } catch(java.io.IOException ex) {throw new IllegalStateException(ex);}
    }
    private MirrorPrototypeScene() {}
}

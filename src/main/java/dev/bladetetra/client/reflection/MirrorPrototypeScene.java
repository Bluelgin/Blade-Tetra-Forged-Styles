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
@Mod.EventBusSubscriber(modid="blade_tetra",value=Dist.CLIENT)
public final class MirrorPrototypeScene {
    private static boolean started,prepared;
    private static int ticks,capture,waiting;
    public static boolean ready() { return prepared; }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(!Boolean.getBoolean("blade_tetra.mirrorPrototype")||e.phase!=TickEvent.Phase.END) return;
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
        if(ticks==100||ticks==140||ticks==180||ticks==220) capture=ticks;
        if(ticks==260) {
            LogUtils.getLogger().info("MIRROR_PROTOTYPE_SCENE_FINISHED (requires visual review; not an automatic pass)");
            mc.stop();
        }
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e) {
        if(!Boolean.getBoolean("blade_tetra.mirrorPrototype")||e.phase!=TickEvent.Phase.END||capture==0) return;
        var mc=Minecraft.getInstance();
        try {
            var out=mc.gameDirectory.toPath().resolve("evidence");Files.createDirectories(out);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(out.resolve("mirror-"+capture+".png")); }
            LogUtils.getLogger().info("MIRROR_PROTOTYPE_CAPTURE {}",capture);capture=0;
        } catch(java.io.IOException ex) {throw new IllegalStateException(ex);}
    }
    private MirrorPrototypeScene() {}
}

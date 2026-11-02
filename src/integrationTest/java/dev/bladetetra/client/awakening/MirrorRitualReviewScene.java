package dev.bladetetra.client.awakening;

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

/** Opt-in isolated client regression for repeated rituals, camera switches and abort. */
@Mod.EventBusSubscriber(modid="blade_tetra",value=Dist.CLIENT)
public final class MirrorRitualReviewScene {
    private static boolean started,prepared;
    private static int ticks,capture,waiting,completed;
    private static boolean enabled() {return Boolean.getBoolean("blade_tetra.mirrorRitualReview");}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(!enabled()||e.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance();
        if(!started&&mc.screen instanceof TitleScreen) {
            started=true; String name="mirror-shatter-review";
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
                player.teleportTo(0,-60,-1);player.setYRot(0);player.setXRot(-10);
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
            mc.options.hideGui=false;
            mc.options.fov().set(80);
            mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;
        }
        ++ticks;
        reviewTick(mc);
        if(ticks==1020) {
            if(completed!=6) throw new IllegalStateException("Missing lifecycle checks");
            LogUtils.getLogger().info("MIRROR_SHATTER_REVIEW_PASS cycles={} firstThirdSwitches=true",completed);
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
        server.execute(()-> {
            var player=server.getPlayerList().getPlayer(id);var level=player.serverLevel();
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(level.getGameTime(),level.getDayTime(),false));
            LogUtils.getLogger().info("MIRROR_REVIEW_SERVER soul={} id={} time={}",
                    dev.bladetetra.easteregg.SoulLegacyState.active(player.getMainHandItem()),
                    player.getMainHandItem().getOrCreateTag().getString("id"),level.getGameTime());
            dev.bladetetra.easteregg.AwakeningPresentationService.success(player,dev.bladetetra.easteregg.SoulLegacyState.Legacy.KYOUKA);
        });
        LogUtils.getLogger().info("MIRROR_RITUAL_REVIEW_START tick={} quality={}",ticks,
                dev.bladetetra.config.ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get());
    }
    private static void reviewTick(Minecraft mc) {
        int cycle=(ticks-40)/160, age=(ticks-40)%160;
        if(ticks<40||cycle>=6) return;
        if(age==0) {
            mc.options.setCameraType(cycle%2==0?net.minecraft.client.CameraType.FIRST_PERSON:
                    net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            dev.bladetetra.config.ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.set(cycle%3);
            preview(mc);
        }
        if(age==15) startRitual(mc);
        if(age==65||age==105) {
            mc.options.setCameraType(mc.options.getCameraType().isFirstPerson()?
                    net.minecraft.client.CameraType.THIRD_PERSON_BACK:net.minecraft.client.CameraType.FIRST_PERSON);
        }
        if(cycle==4&&age==90) {
            mc.player.getInventory().selected=1;
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            server.execute(()->{var p=server.getPlayerList().getPlayer(id);p.getInventory().selected=1;p.inventoryMenu.broadcastChanges();});
        }
        if(age==48&&!AwakeningVisualController.presenting(mc.player))
            throw new IllegalStateException("Review never entered the native ritual");
        if(age==48||age==92||age==103||age==110||age==120) capture=ticks;
        if(age==150) {
            if(AwakeningVisualController.presenting(mc.player)) throw new IllegalStateException("Ritual retained after end/abort");
            ++completed;
            LogUtils.getLogger().info("MIRROR_REVIEW_LIFECYCLE cycle={} clean=true",cycle);
        }
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e) {
        if(!enabled()||e.phase!=TickEvent.Phase.END||capture==0) return;
        var mc=Minecraft.getInstance();
        try {
            var out=mc.gameDirectory.toPath().resolve("evidence");Files.createDirectories(out);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(out.resolve("shatter-"+capture+".png")); }
            LogUtils.getLogger().info("{}_CAPTURE {}","MIRROR_SHATTER_REVIEW",capture);capture=0;
        } catch(java.io.IOException ex) {throw new IllegalStateException(ex);}
    }
    private MirrorRitualReviewScene() {}
}

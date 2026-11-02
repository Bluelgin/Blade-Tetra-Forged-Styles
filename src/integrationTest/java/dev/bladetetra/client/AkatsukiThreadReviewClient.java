package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.easteregg.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;

/** Opt-in screenshots in a separate save. Never touches the user's test world. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID,value=Dist.CLIENT)
public final class AkatsukiThreadReviewClient {
    private static boolean started,prepared;private static int ticks,capture;private static java.util.UUID targetId;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(!Boolean.getBoolean("blade_tetra.bloodThreadReview")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(!started&&mc.screen instanceof TitleScreen) {
            started=true;String name="blood-thread-review";
            if(Files.exists(mc.gameDirectory.toPath().resolve("saves/"+name+"/level.dat")))mc.createWorldOpenFlows().loadLevel(mc.screen,name);
            else mc.createWorldOpenFlows().createFreshLevel(name,new LevelSettings(name,GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(158,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
        if(started&&mc.screen!=null&&mc.screen.getTitle().getString().toLowerCase(java.util.Locale.ROOT).contains("experimental"))
            for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.active&&b.getMessage().getString().equalsIgnoreCase("Proceed")){b.onPress();return;}
        if(mc.player==null||mc.level==null||mc.screen!=null)return;
        if(!prepared) {
            prepared=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);mc.options.hideGui=true;
            mc.options.fov().set(75);mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;
            dev.bladetetra.config.ClientVisualConfig.ENABLE_AUDIO_PACK_REMINDER.set(false);
            var blade=BladeArtPreviewSamples.createAwakened().get(0).stack().copy();var id=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);p.teleportTo(0,-60,0);p.setYRot(0);p.setXRot(0);p.setHealth(20);
                p.setGameMode(GameType.SURVIVAL);p.getInventory().selected=0;p.getInventory().setItem(0,blade);
                p.serverLevel().setDayTime(6000);p.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.getServer());
                p.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.getServer());
                var target=new Zombie(p.level());target.moveTo(1.5,-60,5,0,0);target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setHealth(100);target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));p.serverLevel().addFreshEntity(target);targetId=target.getUUID();
                var orbit=new mods.flammpfeil.slashblade.entity.EntitySpiralSwords(mods.flammpfeil.slashblade.SlashBlade.RegistryEvents.SpiralSwords,p.level());
                orbit.setOwner(p);orbit.setPos(p.position());orbit.setDelay(10000);orbit.startRiding(p,true);p.serverLevel().addFreshEntity(orbit);
                p.inventoryMenu.broadcastChanges();
            });
        }
        mc.player.getInventory().selected=0;ticks++;
        if(ticks==30) {
            mc.player.setYRot(0);mc.player.setXRot(0);var id=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);p.setOnGround(true);
                p.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).ifPresent(s->{
                    for(int i=0;i<3;i++){s.updateComboSeq(p,ComboStateRegistry.AERIAL_CLEAVE.getId());s.updateComboSeq(p,ComboStateRegistry.AERIAL_CLEAVE_LANDING.getId());}
                    p.getCapability(CapabilityConcentrationRank.RANK_POINT).ifPresent(r->{r.setRawRankPoint(1800);r.setLastUpdte(p.level().getGameTime());});
                    p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(12);s.setComboSeq(ComboStateRegistry.NONE.getId());s.doChargeAction(p,40);
                    com.mojang.logging.LogUtils.getLogger().info("BLOOD_THREAD_REVIEW_STARTED: active={}, full={}",AkatsukiBloodTendrils.active(p),AkatsukiBloodBlade.full(p));
                });
            });
        }
        if(ticks==95){mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=false;}
        if(ticks==180){mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);mc.options.hideGui=true;}
        if(ticks>35&&ticks<380&&ticks%5==0){
            var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var target=p.serverLevel().getEntity(targetId);
                if(target instanceof Zombie zombie&&zombie.isAlive())p.getCapability(CapabilityConcentrationRank.RANK_POINT).ifPresent(r->{
                    if(r.getRankPoint(p.level().getGameTime())>0)zombie.setHealth(80);else zombie.setHealth(8);
                });
            });
            capture=ticks;
        }
        if(ticks==400){com.mojang.logging.LogUtils.getLogger().info("BLOOD_THREAD_REVIEW_CAPTURED");mc.stop();}
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e) {
        if(!Boolean.getBoolean("blade_tetra.bloodThreadReview")||e.phase!=TickEvent.Phase.END||capture==0)return;
        var mc=Minecraft.getInstance();try{
            var path=mc.gameDirectory.toPath().resolve("evidence");Files.createDirectories(path);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path.resolve("thread-"+capture+".png"));}capture=0;
        }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
    }
    private AkatsukiThreadReviewClient() {}
}

package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.easteregg.AkatsukiBloodBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;

/** Opt-in real-world visual review, isolated from the player's normal save. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID,value=Dist.CLIENT)
public final class AkatsukiBloodReviewClient {
    private static boolean started,prepared;
    private static int ticks,capture;
    private static boolean enabled() {return Boolean.getBoolean("blade_tetra.bloodBladeReview");}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(!enabled()||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(!started&&mc.screen instanceof TitleScreen) {
            started=true;String name="akatsuki-blood-review";
            if(Files.exists(mc.gameDirectory.toPath().resolve("saves/"+name+"/level.dat")))mc.createWorldOpenFlows().loadLevel(mc.screen,name);
            else {
                var rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel(name,new LevelSettings(name,GameType.CREATIVE,false,
                        Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(158,false,false),
                        r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
        }
        if(started&&mc.screen!=null&&mc.screen.getTitle().getString().toLowerCase(java.util.Locale.ROOT).contains("experimental"))
            for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b
                    &&b.active&&b.getMessage().getString().equalsIgnoreCase("Proceed")) {b.onPress();return;}
        if(mc.player==null||mc.level==null||mc.screen!=null)return;
        // The dedicated review save may remember another selected slot from an earlier launch.
        mc.player.getInventory().selected=0;
        if(!prepared) {
            prepared=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);mc.options.hideGui=true;
            mc.options.fov().set(70);
            mc.options.showSubtitles().set(false);
            ClientVisualConfig.ENABLE_AUDIO_PACK_REMINDER.set(false);mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;
            var blade=BladeArtPreviewSamples.createAwakened().get(0).stack().copy();
            var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                p.setGameMode(GameType.SURVIVAL);p.setHealth(20);p.getFoodData().setFoodLevel(20);
                p.getInventory().selected=0;p.getInventory().setItem(0,blade);
                p.serverLevel().setDayTime(6000);p.teleportTo(0,-60,0);
                p.serverLevel().getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false,p.getServer());
                p.setYRot(35);p.setXRot(0);p.inventoryMenu.broadcastChanges();
            });
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.player.setYRot(35);mc.player.setXRot(12);
        }
        ticks++;
        if(ticks==35||ticks==155) {
            mc.options.setCameraType(ticks==35?CameraType.THIRD_PERSON_FRONT:CameraType.FIRST_PERSON);
            mc.options.hideGui=ticks==35;
            var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                AkatsukiBloodBlade.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
                p.setHealth(20);p.teleportTo(0,-57,0);p.setOnGround(false);
                var orbit=new mods.flammpfeil.slashblade.entity.EntitySpiralSwords(mods.flammpfeil.slashblade.SlashBlade.RegistryEvents.SpiralSwords,p.level());
                orbit.setOwner(p);orbit.setPos(p.position());orbit.setDelay(10000);orbit.startRiding(p,true);p.serverLevel().addFreshEntity(orbit);
                p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                p.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).ifPresent(s->s.updateComboSeq(p,ComboStateRegistry.AERIAL_CLEAVE.getId()));
            });
        }
        if(ticks==60||ticks==170||ticks==180) {
            var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                p.setOnGround(true);
                p.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).ifPresent(s->{
                    s.updateComboSeq(p,ComboStateRegistry.AERIAL_CLEAVE.getId());
                    s.updateComboSeq(p,ComboStateRegistry.AERIAL_CLEAVE_LANDING.getId());
                });
            });
        }
        if(ticks==70||ticks==190) {
            var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                p.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).ifPresent(s->s.updateComboSeq(p,ComboStateRegistry.NONE.getId()));
            });
        }
        if(ticks>=38&&ticks<=79||ticks>=158&&ticks<=199)capture=ticks;
        if(ticks==80||ticks==200) {
            var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                p.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).ifPresent(s->s.updateComboSeq(p,ComboStateRegistry.COMBO_A1.getId()));
            });
        }
        if(ticks>=81&&ticks<=100||ticks>=201&&ticks<=220)capture=ticks;
        if(ticks==110||ticks==230) {
            var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                p.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).ifPresent(s->s.updateComboSeq(p,ComboStateRegistry.UPPERSLASH.getId()));
            });
        }
        if(ticks>=111&&ticks<=135||ticks>=231&&ticks<=255)capture=ticks;
        if(ticks==280) {
            com.mojang.logging.LogUtils.getLogger().info("AKATSUKI_BLOOD_REVIEW_CAPTURED: native falling cleave/landing, instant blood, melee and blood recall in first/third person");
            mc.stop();
        }
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e) {
        if(!enabled()||e.phase!=TickEvent.Phase.END||capture==0)return;
        var mc=Minecraft.getInstance();
        try {
            Path out=mc.gameDirectory.toPath().resolve("evidence");Files.createDirectories(out);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {image.writeToFile(out.resolve("blood-"+capture+".png"));}
            capture=0;
        }catch(java.io.IOException ex) {throw new IllegalStateException(ex);}
    }
    private AkatsukiBloodReviewClient() {}
}

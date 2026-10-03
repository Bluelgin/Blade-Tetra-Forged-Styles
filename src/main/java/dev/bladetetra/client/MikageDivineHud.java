package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.challenge.DivineDomainManager;
import dev.bladetetra.challenge.DivineDomainWipNotice;
import dev.bladetetra.network.DivineSupportStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Compact, expiring participant-only support HUD, with no world or combat authority. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID,value=Dist.CLIENT)
public final class MikageDivineHud {
    private static DivineSupportStatePacket state;
    private static ClientLevel world;
    private static int remaining;
    public static void accept(DivineSupportStatePacket packet) {
        var mc=Minecraft.getInstance();
        if(mc.level==null || !mc.level.dimension().equals(DivineDomainManager.DIVINE_REALM)) return;
        if(!packet.active()) { if(state!=null && state.session()==packet.session()) clear(); return; }
        state=packet; world=mc.level; remaining=45;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance();
        if(world!=mc.level || mc.player==null || !mc.player.isAlive()) clear();
        else if(!mc.isPaused() && --remaining<=0) clear();
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post e) {
        var mc=Minecraft.getInstance();
        if(state==null || mc.options.hideGui || mc.screen!=null) return;
        var g=e.getGuiGraphics(); int y=g.guiHeight()/2-24;
        g.drawString(mc.font,DivineDomainWipNotice.title(),12,y-13,0xE0B45C,true);
        int ticks=Math.max(0,state.remainingTicks()-(45-remaining));
        boolean burning=state.rescuing() && ticks>0;
        icon(g,burning?"divine_fire_active":state.ready()?"divine_fire_ready":"divine_fire_spent",y);
        int seconds=(ticks+19)/20;
        var text=net.minecraft.network.chat.Component.translatable(burning?"hud.blade_tetra.divine.fire_active"
                :state.ready()?"hud.blade_tetra.divine.fire_ready":"hud.blade_tetra.divine.fire_spent",seconds);
        g.drawString(mc.font,text,28,y+3,0xEEE6D5,true);
    }
    private static void icon(net.minecraft.client.gui.GuiGraphics g,String name,int y) {
        g.blit(new ResourceLocation("blade_tetra","textures/divine/"+name+"_32.png"),12,y,12,12,0,0,32,32,32,32);
    }
    private static void clear() { state=null; remaining=0; world=null; }
}

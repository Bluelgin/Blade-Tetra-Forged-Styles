package dev.bladetetra.client;

import com.mojang.math.Axis;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.easteregg.AkatsukiBloodBlade;
import dev.bladetetra.easteregg.AkatsukiBloodRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Procedural edge vessels: one short cue per confirmed transfer, no textures or post-processing. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID,value=Dist.CLIENT)
public final class AkatsukiBloodScreenEffect {
    private record Cue(double at,boolean returning,float strength,float health) {}
    private record Vessel(float x0,float y0,float x1,float y1,float radius,float depth) {}
    private static final List<Vessel> VESSELS=new ArrayList<>();
    private static ClientLevel world;
    private static int owner=-1,width,height;
    private static Cue cue;
    @SubscribeEvent public static void gui(RenderGuiEvent.Post e) {
        var mc=Minecraft.getInstance();
        if(world!=mc.level||mc.player==null||owner!=mc.player.getId()) {
            world=mc.level;owner=mc.player==null?-1:mc.player.getId();cue=null;VESSELS.clear();
        }
        if(mc.player==null||!mc.player.isAlive()) {cue=null;return;}
        if(!ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) {cue=null;return;}
        float intensity=Math.min(1,ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue());
        if(intensity<=0)return;
        double now=world.getGameTime()+e.getPartialTick();
        var flow=AkatsukiBloodBlade.flow(mc.player,mc.player.getMainHandItem(),e.getPartialTick());
        if(flow!=null) {
            double at=now-flow.age();
            if(cue==null||Math.abs(cue.at-at)>.25||cue.returning!=flow.returning()) {
                cue=new Cue(at,flow.returning(),flow.strength(),flow.health());VESSELS.clear();
            }
        }
        if(cue==null||mc.options.hideGui||mc.screen!=null)return;
        float age=(float)(now-cue.at),duration=cue.returning?36:32;
        if(age<0||age>=duration)return;
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        if(width!=w||height!=h||VESSELS.isEmpty()) {width=w;height=h;build(w,h,(long)cue.at);}
        float pulse=AkatsukiBloodRules.smooth(age/2)*(1-AkatsukiBloodRules.smooth(age/duration));
        pulse*=intensity*(.75F+.25F*cue.strength);
        var g=e.getGuiGraphics();
        if(mc.options.getCameraType().isFirstPerson()) {
            edge(g,w,h,pulse,cue.returning);
            float extent=cue.returning?1-AkatsukiBloodRules.smooth(age/28):.3F+.7F*AkatsukiBloodRules.smooth(age/6);
            for(var v:VESSELS) {
                if(v.depth>extent)continue;
                float fade=Math.min(1,(extent-v.depth)*12);
                float beat=.85F+.15F*(float)Math.sin(age*.65F-v.depth*8);
                float alpha=pulse*fade*beat;
                stroke(g,v,2.1F,(int)(alpha*70),0x36020D);
                stroke(g,v,1,(int)(alpha*185),cue.returning?0xCF3D62:0x9C102D);
                stroke(g,v,.32F,(int)(alpha*(cue.returning?145:70)),cue.returning?0xFFD1CB:0xEC5368);
            }
        }
        if(cue.returning&&cue.health>0) {
            String value=String.format(Locale.ROOT,"+%.1f \u2665",cue.health/2);
            int alpha=(int)(255*AkatsukiBloodRules.smooth(age/2)*(1-AkatsukiBloodRules.smooth((age-20)/16))*intensity);
            if(alpha>3)g.drawString(mc.font,value,(w-mc.font.width(value))/2,h-55-(int)(age*.08F),alpha<<24|0xFFD2CB,true);
        }
    }
    private static void edge(GuiGraphics g,int w,int h,float pulse,boolean returning) {
        int bands=18,rgb=returning?0xB43A56:0x770E25;
        for(int i=0;i<bands;i++) {
            float t=i/(float)bands;
            int alpha=(int)(pulse*(returning?62:96)*(1-t)*(1-t));if(alpha==0)continue;
            int x0=(int)(i*w*.12F/bands),x1=(int)((i+1)*w*.12F/bands);
            int y0=(int)(i*h*.16F/bands),y1=(int)((i+1)*h*.16F/bands),color=alpha<<24|rgb;
            g.fill(x0,0,x1,h,color);g.fill(w-x1,0,w-x0,h,color);
            g.fill(x1,y0,w-x1,y1,color);g.fill(x1,h-y1,w-x1,h-y0,color);
        }
    }
    private static void stroke(GuiGraphics g,Vessel v,float scale,int alpha,int rgb) {
        if(alpha<1)return;
        float dx=v.x1-v.x0,dy=v.y1-v.y0,length=(float)Math.hypot(dx,dy);
        var pose=g.pose();pose.pushPose();pose.translate(v.x0,v.y0,0);
        pose.mulPose(Axis.ZP.rotation((float)Math.atan2(dy,dx)));pose.scale(length,Math.max(.18F,v.radius*scale),1);
        g.fill(0,-1,1,1,Math.min(255,alpha)<<24|rgb);pose.popPose();
    }
    private static void build(int w,int h,long seed) {
        VESSELS.clear();var random=new Random(seed^0xB100D);
        int roots=ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get()==0?3:5;
        for(int edge=0;edge<4;edge++)for(int root=0;root<roots;root++) {
            float along=(root+.15F+random.nextFloat()*.7F)/roots;
            float nx=edge==0?1:edge==1?-1:0,ny=edge==2?1:edge==3?-1:0;
            float x=edge==0?-2:edge==1?w+2:along*w;
            float y=edge==2?-2:edge==3?h+2:along*h;
            float depth=Math.min(w,h)*(.13F+random.nextFloat()*.12F);
            float radius=Math.max(.65F,Math.min(w,h)*.0045F);
            float bend=(random.nextFloat()-.5F)*depth*.65F;
            branch(x,y,nx,ny,depth,bend,radius,0,1,random,true);
        }
    }
    private static void branch(float x,float y,float nx,float ny,float length,float bend,float radius,
            float startDepth,float endDepth,Random random,boolean forks) {
        float previousX=x,previousY=y,phase=random.nextFloat()*6;
        int steps=forks?14:8;
        for(int i=1;i<=steps;i++) {
            float t=i/(float)steps;
            float sway=bend*t*t+(float)Math.sin(t*7+phase)*length*.035F*t;
            float px=x+nx*length*t+ny*sway,py=y+ny*length*t-nx*sway;
            float depth=startDepth+(endDepth-startDepth)*t;
            VESSELS.add(new Vessel(previousX,previousY,px,py,radius*(1-.87F*t),depth));
            if(forks&&(i==5||i==9)) {
                float angle=(i==5?1:-1)*(.5F+random.nextFloat()*.4F);
                float bx=nx*(float)Math.cos(angle)-ny*(float)Math.sin(angle);
                float by=nx*(float)Math.sin(angle)+ny*(float)Math.cos(angle);
                branch(px,py,bx,by,length*(1-t)*.65F,bend*.35F,radius*(1-t)*.6F,depth,
                        Math.min(1,depth+.3F),random,false);
            }
            previousX=px;previousY=py;
        }
    }
    private AkatsukiBloodScreenEffect() {}
}

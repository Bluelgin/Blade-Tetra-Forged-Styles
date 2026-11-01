package dev.bladetetra.client;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.util.List;

/** Opt-in, world-free review of the actual tooltip factory, font layout and scrolling. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID,value=Dist.CLIENT)
public final class SoulGuideReviewClient {
    private static int phase,ticks,capture=-1;
    private static boolean started;
    static boolean enabled(){return Boolean.getBoolean("blade_tetra.soulGuideReview");}
    static boolean expanded(){return enabled()&&started&&phase%4!=0;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!enabled()||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null)return;
        if(!started) {
            if(!(mc.screen instanceof TitleScreen))return;
            started=true;mc.setScreen(new Review());
        }
        if(++ticks%24==0)capture=phase;
        if(ticks%24==1&&ticks>24) {
            phase++;
            if(phase>=16){LogUtils.getLogger().info("SOUL_GUIDE_REVIEW_PASS: four lore cards, complete Shift guides, skill emphasis, small-screen clipping and end-of-guide scrolling");mc.stop();}
        }
    }
    private static final class Review extends Screen {
        private int appliedPhase=-1;
        private final List<ItemStack> stacks=List.of(ModItems.AKATSUKI_SOUL_FRAGMENT.get().getDefaultInstance(),
                ModItems.SENBONZAKURA_SOUL_FRAGMENT.get().getDefaultInstance(),ModItems.RAIKIRI_SOUL_FRAGMENT.get().getDefaultInstance(),
                ModItems.KYOUKA_SOUL_FRAGMENT.get().getDefaultInstance());
        Review(){super(Component.literal("Soul guide visual review"));}
        @Override public void render(GuiGraphics g,int mx,int my,float partial) {
            g.fill(0,0,width,height,0xFF20232B);
            if(phase>=16)return;
            g.renderTooltip(font,stacks.get(phase/4),Math.max(5,width/2-100),height/2);
            if(appliedPhase!=phase) {
                if(phase%4==3)SoulFragmentTooltipRenderer.reviewScrollEnd(this);
                if(phase%4==2)SoulFragmentTooltipRenderer.reviewScrollSkills(this);
                appliedPhase=phase;
            }
        }
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if(!enabled()||event.phase!=TickEvent.Phase.END||capture<0)return;
        var mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null)return;
        try {
            var path=mc.gameDirectory.toPath().resolve("evidence");Files.createDirectories(path);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path.resolve("guide-"+capture+".png"));}
            if(capture%4==3&&!SoulFragmentTooltipRenderer.reviewAtEnd())throw new IllegalStateException("Guide scroll did not reach its final lines");
            capture=-1;
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
    private SoulGuideReviewClient(){}
}

package dev.bladetetra.client;

import com.mojang.datafixers.util.Either;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.item.AwakenedSoulFragmentItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** One bounded component inside the normal Forge tooltip, isolated to guide fragments. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class SoulFragmentTooltipRenderer {
    private static final Scroll SCROLL = new Scroll();
    private static Layout cached;
    private record Data(ItemStack stack, String soul, List<FormattedText> text, int width, int screenHeight, boolean expanded)
            implements TooltipComponent {}
    private record Layout(List<FormattedText> source, Font font, int width, int screenHeight, List<Row> lines) {}
    private record Run(Component text, int width, boolean animated, int color) {}
    private record Row(List<Run> runs) {
        static Row of(Font font, FormattedCharSequence line) {
            var runs=new ArrayList<Run>();var buffer=new StringBuilder();final Style[] previous={null};
            line.accept((index,style,code)->{
                if(!style.equals(previous[0])&&buffer.length()>0){add(runs,font,buffer.toString(),previous[0]);buffer.setLength(0);}
                previous[0]=style;buffer.appendCodePoint(code);return true;
            });
            if(buffer.length()>0)add(runs,font,buffer.toString(),previous[0]);
            return new Row(List.copyOf(runs));
        }
        private static void add(List<Run> runs,Font font,String value,Style style){
            boolean motion=SoulGuideText.MOTION.equals(style.getInsertion());
            if(motion) value.codePoints().forEach(code->addGlyph(runs,font,new String(Character.toChars(code)),style,true));
            else addGlyph(runs,font,value,style,false);
        }
        private static void addGlyph(List<Run> runs,Font font,String value,Style style,boolean motion){
            var text=Component.literal(value).withStyle(style);
            runs.add(new Run(text,font.width(text),motion,style.getColor()==null?0xD6D3CE:style.getColor().getValue()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void gather(RenderTooltipEvent.GatherComponents event) {
        if (!(event.getItemStack().getItem() instanceof AwakenedSoulFragmentItem item)) return;
        List<FormattedText> text = new ArrayList<>();
        var foreign = new ArrayList<Either<FormattedText, TooltipComponent>>();
        for (var element : event.getTooltipElements()) {
            element.ifLeft(text::add);element.ifRight(value -> foreign.add(Either.right(value)));
        }
        if (!text.isEmpty()) text.remove(0);
        int width = Math.min(330, Math.max(80, event.getScreenWidth() - 36));
        if (event.getMaxWidth() > 0) width = Math.min(width, event.getMaxWidth());
        event.getTooltipElements().clear();
        event.getTooltipElements().add(Either.right(new Data(event.getItemStack(), item.soul(), List.copyOf(text),
                width, event.getScreenHeight(), AwakenedSoulGuideTooltip.expanded())));
        event.getTooltipElements().addAll(foreign);
    }
    @SubscribeEvent public static void colors(RenderTooltipEvent.Color event) {
        if (!(event.getItemStack().getItem() instanceof AwakenedSoulFragmentItem item)) return;
        int accent = accent(item.soul());
        event.setBackgroundStart(0xF016151C);event.setBackgroundEnd(0xF00B0F15);
        event.setBorderStart(0xD0000000 | mix(accent, 0xE7DECA, .35));
        event.setBorderEnd(0xB0000000 | mix(accent, 0x15212D, .65));
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!AwakenedSoulGuideTooltip.expanded() || SCROLL.screen != event.getScreen() || SCROLL.max <= 0
                || Util.getMillis() - SCROLL.seen > 180 || Math.abs(event.getMouseX() - SCROLL.mouseX) > 1
                || Math.abs(event.getMouseY() - SCROLL.mouseY) > 1) return;
        SCROLL.offset = Math.max(0, Math.min(SCROLL.max, SCROLL.offset - (int)Math.round(event.getScrollDelta() * 30)));
        SCROLL.lastScroll=Util.getMillis();
        event.setCanceled(true);
    }
    static void reviewScrollEnd(Screen screen) {
        if(SoulGuideReviewClient.enabled()) scroll(new ScreenEvent.MouseScrolled.Pre(screen,SCROLL.mouseX,SCROLL.mouseY,-1000));
    }
    static void reviewScrollSkills(Screen screen) {
        if(SoulGuideReviewClient.enabled()) scroll(new ScreenEvent.MouseScrolled.Pre(screen,SCROLL.mouseX,SCROLL.mouseY,-3));
    }
    static boolean reviewAtEnd(){return SCROLL.max>0&&SCROLL.offset==SCROLL.max;}
    @Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Factories {
        @SubscribeEvent public static void register(RegisterClientTooltipComponentFactoriesEvent event) { event.register(Data.class, View::new); }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> cached = null);
        }
    }
    private static final class Scroll {
        Screen screen;ItemStack stack;boolean expanded;int offset,max;double mouseX,mouseY;long seen,lastScroll;
        void bind(Data data, int limit) {
            var screenNow = Minecraft.getInstance().screen;
            if (screen != screenNow || stack != data.stack || expanded != data.expanded || Util.getMillis()-seen > 250) offset = 0;
            screen=screenNow;stack=data.stack;expanded=data.expanded;max=limit;offset=Math.max(0,Math.min(max,offset));
        }
        void drawn() {
            var mc=Minecraft.getInstance();seen=Util.getMillis();
            mouseX=mc.mouseHandler.xpos()*mc.getWindow().getGuiScaledWidth()/mc.getWindow().getScreenWidth();
            mouseY=mc.mouseHandler.ypos()*mc.getWindow().getGuiScaledHeight()/mc.getWindow().getScreenHeight();
        }
    }
    private static final class View implements ClientTooltipComponent {
        private final Data data;
        private final List<Row> lines;
        private final int bodyHeight, fullHeight;
        View(Data data) {
            this.data=data;var font=Minecraft.getInstance().font;
            if (cached == null || cached.font != font || cached.width != data.width || cached.screenHeight != data.screenHeight
                    || !cached.source.equals(data.text)) {
                var wrapped=new ArrayList<Row>();
                for (var text:data.text) for(var line:font.split(text,data.width-12))wrapped.add(Row.of(font,line));
                cached=new Layout(data.text,font,data.width,data.screenHeight,List.copyOf(wrapped));
            }
            lines=cached.lines;fullHeight=lines.size()*11;
            bodyHeight=Math.min(fullHeight,Math.max(22,data.screenHeight-70));
        }
        @Override public int getHeight() { return 26+bodyHeight+14; }
        @Override public int getWidth(Font font) { return data.width; }
        @Override public void renderImage(Font font,int x,int y,GuiGraphics g) {
            SCROLL.bind(data,Math.max(0,fullHeight-bodyHeight));SCROLL.drawn();
            int accent=accent(data.soul);
            g.renderItem(data.stack,x+1,y);
            title(g,font,data.stack.getHoverName().getString(),data.soul,x+23,y+3,data.width-25);
            g.fillGradient(x,y+20,x+data.width,y+21,0x90000000|accent,0x12000000|accent);
            int top=y+26,bottom=top+bodyHeight;
            g.enableScissor(x,top,x+data.width,bottom);
            for(int i=Math.max(0,SCROLL.offset/11);i<lines.size();i++) {
                int row=top+i*11-SCROLL.offset;if(row>=bottom)break;
                body(g,font,lines.get(i),data.soul,x+2,row,i);
            }
            g.disableScissor();
            if(SCROLL.max>0) {
                int thumb=Math.max(8,bodyHeight*bodyHeight/Math.max(1,fullHeight));
                int at=top+(bodyHeight-thumb)*SCROLL.offset/SCROLL.max;
                g.fill(x+data.width-3,top,x+data.width-2,bottom,0x352F3945);
                g.fill(x+data.width-3,at,x+data.width-2,at+thumb,0xC0000000|accent);
            }
            var footer=Component.translatable(AwakenedSoulGuideTooltip.PREFIX+(data.expanded?(SCROLL.max>0?"scroll":"release"):"seal"));
            g.drawString(font,footer,x+2,bottom+4,0x8D929A,false);
        }
    }
    /** Animated runs have cached glyph widths; stable runs remain one draw each. Layout never moves. */
    private static void body(GuiGraphics g,Font font,Row row,String soul,int x,int y,int rowIndex) {
        boolean animate=ClientVisualConfig.ANIMATE_SOUL_FRAGMENT_TEXT.get()&&Util.getMillis()-SCROLL.lastScroll>300;
        double time=Util.getMillis()/1000.0;int cursor=0,index=0;
        for(var run:row.runs) {
            if(!animate||!run.animated)g.drawString(font,run.text,x+cursor,y,run.color,false);
            else {
                double phase=time*1.7-index*.34-rowIndex*.17;
                double light=switch(soul){
                    case "akatsuki"->(.5+.5*Math.sin(time*2.1))*.32;
                    case "senbonzakura"->Math.pow(Math.max(0,Math.cos(phase)),6)*.6;
                    case "raikiri"->Math.pow(Math.max(0,Math.cos(time*2.4+rowIndex*1.9)),24)
                            *Math.pow(Math.max(0,Math.cos(index*.8-time*3)),4)*.75;
                    default->(.5+.5*Math.sin(phase))*.28;
                };
                if(soul.equals("kyouka")) {
                    int alpha=(int)(18+32*Math.pow(Math.max(0,Math.cos(time*1.1+rowIndex)),8));
                    var echo=run.text.copy().withStyle(style->style.withColor(0x8DC6CE));
                    g.drawString(font,echo,x+cursor+1,y,alpha<<24|0x8DC6CE,false);
                }
                var lit=run.text.copy().withStyle(style->style.withColor(mix(run.color,0xFFF3DC,light)));
                g.drawString(font,lit,x+cursor,y,run.color,false);
            }
            cursor+=run.width;index++;
        }
    }
    private static void title(GuiGraphics g,Font font,String name,String soul,int x,int y,int width) {
        boolean animate=ClientVisualConfig.ANIMATE_SOUL_FRAGMENT_TITLES.get();
        double time=animate?Util.getMillis()/1000.0:0;
        int base=accent(soul),cursor=0,index=0;
        g.enableScissor(x-1,y-2,x+width,y+13);
        for(int code:name.codePoints().limit(80).toArray()) {
            var glyph=Component.literal(new String(Character.toChars(code))).withStyle(net.minecraft.ChatFormatting.BOLD,net.minecraft.ChatFormatting.ITALIC);
            int advance=font.width(glyph);
            double wave=animate?Math.sin(time*1.8-index*.55)*.35:0;
            double shine=animate?Math.pow(Math.max(0,Math.cos(index*.45-time*1.1)),8):.15;
            int color=mix(base,0xFFF4D9,shine*.8+.2);
            g.pose().pushPose();g.pose().translate(x+cursor,y+wave,0);
            g.drawString(font,glyph,1,1,0x24141D,false);g.drawString(font,glyph,0,0,color,false);
            g.pose().popPose();cursor+=advance;index++;
            if(cursor>=width)break;
        }
        g.disableScissor();
        if(animate) {
            int at=(int)((Math.sin(time*.65)*.5+.5)*Math.min(width,Math.max(4,cursor-2)));
            g.fill(x+at,y+16,x+Math.min(width,at+3),y+17,0xC0000000|mix(base,0xFFFFFF,.65));
        }
    }
    private static int accent(String soul) {
        return SoulGuideText.accent(soul);
    }
    private static int mix(int a,int b,double t) {
        t=Math.max(0,Math.min(1,t));int r=(int)(((a>>16)&255)*(1-t)+((b>>16)&255)*t);
        int g=(int)(((a>>8)&255)*(1-t)+((b>>8)&255)*t),blue=(int)((a&255)*(1-t)+(b&255)*t);
        return (r<<16)|(g<<8)|blue;
    }
    private SoulFragmentTooltipRenderer() {}
}

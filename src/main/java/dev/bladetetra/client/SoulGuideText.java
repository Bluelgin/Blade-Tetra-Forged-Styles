package dev.bladetetra.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;

/** Semantic emphasis is applied after translation; no formatting codes leak into translations. */
final class SoulGuideText {
    static final String MOTION = "blade_tetra:soul_word";
    private static final int INK=0xD6D3CE, GOLD=0xE6C382, BENEFIT=0xA9D7CA, COST=0xE3A18B;
    private static final Pattern NUMBER=Pattern.compile("[+＋×≥]?\\d+(?:[.／/—–-]\\d+)*(?:%|秒|格|点|生命)?");

    static MutableComponent decorate(String key, Component translated) {
        String value=translated.getString();
        String soul=key.contains(".")?key.substring(0,key.indexOf('.')):"";
        boolean lore=key.contains(".lore."), skill=key.contains(".skills."), detail=key.contains(".details.");
        boolean control=key.startsWith("controls."), coda=key.endsWith(".coda");
        int base=coda?0xB7A9A2:INK;
        if(!lore&&!skill&&!detail&&!control&&!coda)return Component.literal(value).withStyle(Style.EMPTY.withColor(INK));
        var styles=new Style[value.length()];
        Arrays.fill(styles,Style.EMPTY.withColor(base).withItalic(lore||coda));
        if(skill||detail||control) {
            int end=value.startsWith("【")?value.indexOf('】')+1:value.indexOf(':')+1;
            if(end==0)end=value.indexOf('：')+1;
            if(end>0)paint(styles,0,end,Style.EMPTY.withColor(control?GOLD:detail?COST:accent(soul)).withBold(true)
                    .withInsertion(skill?MOTION:null));
            var numbers=NUMBER.matcher(value);
            while(numbers.find()) {
                String context=value.substring(Math.max(0,numbers.start()-16),numbers.start()).toLowerCase(java.util.Locale.ROOT);
                boolean cost=context.matches(".*(?:消耗|燃烧|投入|占用|扣后|不足|冷却|降低|耗|cost|spend|burn|cooldown|reduced).*" )
                        ||value.substring(0,Math.min(12,value.length())).matches(".*(?:代价|消耗|维持|之限|限制).*" );
                paint(styles,numbers.start(),numbers.end(),Style.EMPTY.withColor(cost?COST:BENEFIT));
            }
            emphasize(value,styles,I18n.get(AwakenedSoulGuideTooltip.PREFIX+"warning_words"),COST);
            emphasize(value,styles,I18n.get(AwakenedSoulGuideTooltip.PREFIX+"benefit_words"),BENEFIT);
        }
        if(lore) {
            String words=I18n.get(AwakenedSoulGuideTooltip.PREFIX+soul+".words");
            for(String word:Arrays.stream(words.split("\\|")).filter(w->!w.isBlank())
                    .sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
                for(int at=value.indexOf(word);at>=0;at=value.indexOf(word,at+word.length()))
                    paint(styles,at,at+word.length(),Style.EMPTY.withColor(accent(soul)).withItalic(true).withInsertion(MOTION));
            }
        }
        var result=Component.empty();
        for(int start=0;start<value.length();) {
            int end=start+1;while(end<value.length()&&styles[start].equals(styles[end]))end++;
            result.append(Component.literal(value.substring(start,end)).withStyle(styles[start]));start=end;
        }
        return result;
    }
    private static void paint(Style[] styles,int start,int end,Style style){Arrays.fill(styles,start,end,style);}
    private static void emphasize(String value,Style[] styles,String words,int color){
        for(String word:words.split("\\|")) {
            if(word.isBlank())continue;
            for(int at=value.indexOf(word);at>=0;at=value.indexOf(word,at+word.length()))
                for(int i=at;i<at+word.length();i++)
                    if(!styles[i].isBold())styles[i]=styles[i].withColor(color);
        }
    }
    static int accent(String soul){return switch(soul){case "akatsuki"->0xE49295;case "senbonzakura"->0xE8B2D1;case "raikiri"->0x94D8ED;default->0xA1D4D0;};}
    private SoulGuideText(){}
}

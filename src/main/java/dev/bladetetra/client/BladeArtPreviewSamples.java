package dev.bladetetra.client;

import dev.bladetetra.compat.attachments.SwordAttachmentSchematics;
import dev.bladetetra.easteregg.AkatsukiAwakening;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.visual.SayaPresetSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.module.SchematicRegistry;
import se.mickelus.tetra.items.modular.IModularItem;
import java.util.ArrayList;
import java.util.List;

/** Real module/improvement stacks for the opt-in development gallery. */
final class BladeArtPreviewSamples {
    record Sample(String label, String detail, ItemStack stack) {}
    private BladeArtPreviewSamples() {}

    static List<Sample> create() {
        List<Sample> result = new ArrayList<>();
        ItemStack base = blade("katana_blade", "iron");
        add(result, "基础铁刀", "未裹柄 / 未镶嵌 / 未涂层", base);
        ItemStack wool = wrap(base, new ItemStack(Items.WHITE_WOOL, 64));
        add(result, "布料裹柄", "编织纤维与交叠柄绳", wool);
        ItemStack leather = wrap(base, new ItemStack(Items.LEATHER, 64));
        add(result, "皮革裹柄", "宽带搭接与缝线", leather);
        ItemStack gem = socket(base);
        add(result, "钻石镶嵌", "柄头镶座 / 切面 / 高光", gem);
        ItemStack coat = coat(base);
        add(result, "材料涂层", "局部双层光泽 / 保留刃口", coat);
        add(result, "组合改装", "皮革 + 钻石 + 材料涂层", coat(socket(leather)));
        add(result, "轻型刀镡", "镂空轮廓与内圈刻线", part(base, "tsuba", "light_tsuba", "iron"));
        add(result, "护御刀镡", "方形收边与对称刻纹", part(base, "tsuba", "guard_tsuba", "iron"));
        add(result, "迅捷刀柄", "紧密柄绳 / 轻巧比例", part(base, "tsuka", "swift_tsuka", "stick"));
        add(result, "稳固刀柄", "宽幅柄绳 / 金属收口", part(base, "tsuka", "stable_tsuka", "stick"));
        add(result, "快拔刀鞘", "鞘口加固与收边", part(base, "saya", "quickdraw_saya", "oak"));
        add(result, "灵力刀鞘", "漆面小型灵纹", part(base, "saya", "spirit_saya", "oak"));

        add(result, "本传 / 铁", "朴素锻造与精细收口", blade("orthodox_blade", "iron"));
        add(result, "连舞 / 下界合金", "短刀轮廓与层叠暗纹", blade("wakizashi_blade", "netherite"));
        add(result, "断岳 / 铁", "长刀轮廓与厚重刀背", blade("nodachi_blade", "iron"));
        add(result, "黄金刀", "材质本色 / 克制高光", blade("katana_blade", "gold"));
        add(result, "冰龙钢", "冰晶折线与寒色锻纹", blade("katana_blade", "dragonsteel_ice"));
        add(result, "火龙钢", "暗槽与细窄热裂纹", blade("katana_blade", "dragonsteel_fire"));
        add(result, "雷龙钢", "分段电纹与冷紫光泽", blade("katana_blade", "dragonsteel_lightning"));
        add(result, "钻石刀", "大块晶面 / 清晰刃口", blade("katana_blade", "diamond"));
        ItemStack black = base.copy(); SayaPresetSkin.apply(black, SayaPresetSkin.BLACK_GOLD);
        add(result, "黑金鞘绘", "既有鞘绘与金属细节", black);
        ItemStack cloud = base.copy(); SayaPresetSkin.apply(cloud, SayaPresetSkin.VERMILION_CLOUD);
        add(result, "朱云鞘绘", "既有鞘绘与漆面层次", cloud);
        ItemStack awake = part(base, "inscription", "awakened_soul_inscription", "blood_crystal");
        awake.getOrCreateTag().putBoolean(AkatsukiAwakening.TAG_UNLOCKED, true);
        SayaPresetSkin.apply(awake, SayaPresetSkin.AKATSUKI);
        add(result, "赤月觉醒", "魂印 / 血色刀纹 / 鞘绘", awake);
        ItemStack dormant = awake.copy();
        dormant.getOrCreateTag().putBoolean(AkatsukiAwakening.TAG_UNLOCKED, false);
        add(result, "未觉醒魂铭", "对照：保留材料本色", dormant);
        for(String material:List.of("iron","gold","netherite")) {
            String label=switch(material) {case "gold"->"黄金";case "netherite"->"下界合金";default->"铁";};
            add(result,label+" / 椭圆刀镡","薄刃包边 / 内圈刻槽",part(base,"tsuba","simple_tsuba",material));
            add(result,label+" / 四叶刀镡","真实镂空 / 四叶轮廓",part(base,"tsuba","light_tsuba",material));
            add(result,label+" / 八角刀镡","切角轮廓 / 加厚边框",part(base,"tsuba","guard_tsuba",material));
            if(material.equals("iron")) add(result,"无刀镡","无刀镡仍然不显示刀镡",part(base,"tsuba","tsubaless","iron"));
            else if(material.equals("gold")) add(result,"赤月 / 四叶刀镡","通用新刀镡 / 赤月整套配色",part(awake,"tsuba","light_tsuba","iron"));
            else add(result,"赤月 / 八角刀镡","通用新刀镡 / 赤月整套配色",part(awake,"tsuba","guard_tsuba","iron"));
        }
        return result;
    }

    private static void add(List<Sample> list, String label, String detail, ItemStack stack) {
        if (stack.isEmpty()) throw new IllegalStateException("Empty art sample: " + label);
        IModularItem.updateIdentifier(stack);
        ModItems.MODULAR_SLASHBLADE.get().syncDerivedBladeState(stack);
        com.mojang.logging.LogUtils.getLogger().info("BLADE_ART_SAMPLE: {} {}", label,
                dev.bladetetra.visual.BladeAttachmentAppearance.fromStack(stack).signature());
        stack.setHoverName(Component.literal("美术验收 · " + label));
        list.add(new Sample(label, detail, stack));
    }
    private static ItemStack blade(String module, String material) {
        return part(ModItems.MODULAR_SLASHBLADE.get().createDefaultStack(), "blade", module, material);
    }
    private static ItemStack part(ItemStack source, String slot, String module, String material) {
        ItemStack copy = source.copy();
        IModularItem.putModuleInSlot(copy, "slashblade/"+slot, "slashblade/"+module,
                "slashblade/"+module+"_material", module+"/"+material);
        IModularItem.updateIdentifier(copy);
        return copy;
    }
    private static ItemStack wrap(ItemStack source, ItemStack ingredient) {
        var recipe = SchematicRegistry.getSchematic(ResourceLocation.fromNamespaceAndPath("tetra", "sword/basic_hilt/wrap_hilt"));
        if (recipe == null || !recipe.isMaterialsValid(source, ModularSlashBladeItem.TSUKA_SLOT, new ItemStack[]{ingredient})) {
            throw new IllegalStateException("Native wrap unavailable: " + ingredient);
        }
        return recipe.applyUpgrade(source.copy(), new ItemStack[]{ingredient}, false, ModularSlashBladeItem.TSUKA_SLOT,
                net.minecraft.client.Minecraft.getInstance().player);
    }
    private static ItemStack socket(ItemStack source) {
        var recipe = SchematicRegistry.getSchematic(SwordAttachmentSchematics.SOCKET_SCHEMATIC);
        if (recipe == null) throw new IllegalStateException("Native socket unavailable");
        ItemStack result = recipe.applyUpgrade(source.copy(), new ItemStack[]{new ItemStack(Items.DIAMOND, 64)}, false,
                ModularSlashBladeItem.KASHIRA_SLOT, net.minecraft.client.Minecraft.getInstance().player);
        if (dev.bladetetra.visual.BladeAttachmentAppearance.fromStack(result).socket().isBlank()) {
            throw new IllegalStateException("Native socket did not install a gem");
        }
        return result;
    }
    private static ItemStack coat(ItemStack source) {
        for (var entry : DataManager.instance.schematicData.getData().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            var definition = entry.getValue();
            if (!SwordAttachmentSchematics.isCoating(definition)) continue;
            var recipe = SchematicRegistry.getSchematic(entry.getKey());
            if (recipe == null || !recipe.isApplicableForSlot(ModularSlashBladeItem.BLADE_SLOT, source)) continue;
            for (var outcome : definition.outcomes) {
                if (outcome.improvements == null || outcome.material == null) continue;
                for (ItemStack ingredient : outcome.material.getApplicableItemStacks()) {
                    if (ingredient.isEmpty()) continue;
                    ItemStack material = ingredient.copy();
                    material.setCount(64);
                    if (!recipe.isMaterialsValid(source, ModularSlashBladeItem.BLADE_SLOT, new ItemStack[]{material})) continue;
                    ItemStack copy = recipe.applyUpgrade(source.copy(), new ItemStack[]{material}, false,
                            ModularSlashBladeItem.BLADE_SLOT, net.minecraft.client.Minecraft.getInstance().player);
                    if (!dev.bladetetra.visual.BladeAttachmentAppearance.fromStack(copy).coatings().isEmpty()) return copy;
                }
            }
        }
        throw new IllegalStateException("No loaded coating for art preview");
    }
}

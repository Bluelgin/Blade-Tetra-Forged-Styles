package dev.bladetetra.forging;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Required in-memory Tetra data pack.
 *
 * <p>Compatibility-only modules and improvements keep historical stacks readable.
 * New equipment uses the shared named ingot material in ordinary Tetra modules.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NamedLegacyPack extends AbstractPackResources {
    private final Map<String, byte[]> resources = new LinkedHashMap<>();

    public NamedLegacyPack(String id) {
        super(id, true);
        putUnique("pack.mcmeta",
                "{\"pack\":{\"pack_format\":15,\"description\":\"Named blade imprint patterns\"}}");

        JsonObject en = new JsonObject();
        JsonObject zh = new JsonObject();
        List<JsonObject> historicalImprovements = new ArrayList<>();
        List<LegacyImprintKind> accepted = new ArrayList<>();
        List<LegacyImprintKind> discovered;

        try {
            discovered = List.copyOf(NamedLegacyCatalog.values());
        } catch (RuntimeException | LinkageError failure) {
            LogUtils.getLogger().warn(
                    "Named-blade discovery failed; loading the generic Blade Tetra pack without addon imprints",
                    failure);
            discovered = List.of();
        }

        for (LegacyImprintKind kind : discovered) {
            try {
                if (kind.id() == null || kind.id().isBlank()) throw new IllegalArgumentException("blank kind id");
                accepted.add(kind);
                appendKindLanguage(kind, en, zh);

                // Retain the old grip improvement definition so historical saves do not
                // lose an improvement key merely because V2 no longer crafts it.
                JsonObject improvement = new JsonObject();
                improvement.addProperty("key", kind.improvement());
                improvement.addProperty("level", 1);
                historicalImprovements.add(improvement);
            } catch (RuntimeException | LinkageError failure) {
                LogUtils.getLogger().warn(
                        "Skipping named imprint pack resources for {} without affecting other blades",
                        kind.id(), failure);
            }
        }

        putHistoricalImprovements(historicalImprovements);
        putGenericModule("blade");
        putGenericModule("saya");
        putGenericModule("tsuba");

        en.addProperty("tetra.module.slashblade/legacy_blade.name", "Imprinted blade");
        zh.addProperty("tetra.module.slashblade/legacy_blade.name", "名刀刀身");
        en.addProperty("tetra.variant.legacy_blade/imprinted", "Named blade");
        zh.addProperty("tetra.variant.legacy_blade/imprinted", "名刀刀身");
        for (String part : List.of("blade", "saya", "tsuba")) {
            String enPart = part.equals("blade") ? "Blade" : part.equals("saya") ? "Saya" : "Complete hilt";
            String zhPart = part.equals("blade") ? "刀身" : part.equals("saya") ? "刀鞘" : "镡柄一体刀装";
            en.addProperty("tetra/schematic/slashblade/legacy_ingot_" + part + ".name", "Named ingot · " + enPart);
            zh.addProperty("tetra/schematic/slashblade/legacy_ingot_" + part + ".name", "名刀锭 · " + zhPart);
            en.addProperty("tetra/schematic/slashblade/legacy_ingot_" + part + ".description", "Uses the named blade ingot in the Tetra workbench; source identity comes from the ingot.");
            zh.addProperty("tetra/schematic/slashblade/legacy_ingot_" + part + ".description", "消耗名刀锭进行锻造，刀型来源由名刀锭决定。");
        }
        en.addProperty("item.blade_tetra.named_blade_ingot", "Named blade ingot");
        zh.addProperty("item.blade_tetra.named_blade_ingot", "名刀锭");
        en.addProperty("item.blade_tetra.named_blade_ingot.named", "%s ingot");
        zh.addProperty("item.blade_tetra.named_blade_ingot.named", "%s锭");
        en.addProperty("tooltip.blade_tetra.named_blade_ingot.parts", "Tetra: blade, saya and complete hilt only");
        zh.addProperty("tooltip.blade_tetra.named_blade_ingot.parts", "仅可用于 Tetra 刀身、刀鞘与镡柄一体刀装");
        en.addProperty("tooltip.blade_tetra.named_blade_ingot.missing", "Missing named blade source: %s");
        zh.addProperty("tooltip.blade_tetra.named_blade_ingot.missing", "来源名刀未加载：%s");
        en.addProperty("tetra.module.slashblade/legacy_saya.name", "Imprinted saya");
        zh.addProperty("tetra.module.slashblade/legacy_saya.name", "映锻刀鞘");
        en.addProperty("tetra.module.slashblade/legacy_tsuba.name", "Imprinted hilt");
        zh.addProperty("tetra.module.slashblade/legacy_tsuba.name", "映锻镡柄刀装");
        en.addProperty("tetra.variant.legacy_saya/imprinted", "Imprinted saya");
        zh.addProperty("tetra.variant.legacy_saya/imprinted", "映锻刀鞘");
        en.addProperty("tetra.variant.legacy_tsuba/imprinted", "Imprinted hilt");
        zh.addProperty("tetra.variant.legacy_tsuba/imprinted", "映锻镡柄刀装");
        en.addProperty("message.blade_tetra.imprint.unsupported_model",
                "%1$s cannot be studied: unsupported model (%2$s).");
        zh.addProperty("message.blade_tetra.imprint.unsupported_model",
                "%1$s无法映录：模型结构暂不支持（%2$s）。");
        en.addProperty("tooltip.blade_tetra.imprint.model_supported",
                "✦ Named-ingot fittings support this blade's model");
        zh.addProperty("tooltip.blade_tetra.imprint.model_supported",
                "✦ 名刀锭刀装支持此刀模型");
        en.addProperty("tooltip.blade_tetra.imprint.ingot_ready",
                "✦ Can extract a named blade ingot (8 broken Proud Souls)");
        zh.addProperty("tooltip.blade_tetra.imprint.ingot_ready",
                "✦ 可提取名刀锭（需8个破碎的耀魂）");
        en.addProperty("tooltip.blade_tetra.imprint.ingot_repair",
                "Repair the blade before extracting a named ingot");
        zh.addProperty("tooltip.blade_tetra.imprint.ingot_repair",
                "名刀耐久不足，修复后才能提取名刀锭");
        en.addProperty("tooltip.blade_tetra.imprint.visual_fallback",
                "Model adaptation unsupported: named fittings will use a fallback model");
        zh.addProperty("tooltip.blade_tetra.imprint.visual_fallback",
                "此刀模型暂不支持完整适配，名刀刀装将回退为普通模型");
        en.addProperty("tooltip.blade_tetra.imprint.catalog_id",
                "Blade Tetra · Source: %1$s");
        zh.addProperty("tooltip.blade_tetra.imprint.catalog_id",
                "Blade Tetra · 名刀来源：%1$s");

        putUnique("assets/blade_tetra/lang/en_us.json", en.toString());
        putUnique("assets/blade_tetra/lang/zh_cn.json", zh.toString());
        LogUtils.getLogger().info(
                "Blade Tetra named-pattern pack retained {} historical blade records; new crafts use native named ingot material",
                accepted.size());
    }

    private void putHistoricalImprovements(List<JsonObject> historicalImprovements) {
        List<JsonArray> chunks = HistoricalImprovementChunker.chunk(historicalImprovements);
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = String.format(Locale.ROOT, "%03d", i);
            putUnique("data/tetra/improvements/slashblade/tsuka/legacy_auto/"
                    + chunk + ".json", chunks.get(i).toString());
        }
        LogUtils.getLogger().debug(
                "Blade Tetra split {} historical named-imprint improvements into {} sync-safe resources",
                historicalImprovements.size(), chunks.size());
    }

    private static void appendKindLanguage(LegacyImprintKind kind, JsonObject en, JsonObject zh) {
        String titleEn = NamedLegacyCatalog.localizedName(kind, "en_us");
        String titleZh = NamedLegacyCatalog.localizedName(kind, "zh_cn");
        String improvementLang = "tetra.improvement." + kind.improvement();
        en.addProperty(improvementLang + ".name", titleEn + " old grip record");
        zh.addProperty(improvementLang + ".name", titleZh + " · 旧仿造柄记录");
        en.addProperty(improvementLang + ".description",
                "Retained for old saves; no longer changes the grip independently.");
        zh.addProperty(improvementLang + ".description",
                "保留旧存档记录，不再独立改变刀柄外观。");

        for (String part : List.of("saya", "tsuba")) {
            String schema = kind.schematic(part);
            String prefix = "tetra/schematic/" + schema;
            en.addProperty(prefix + ".name", titleEn + " · "
                    + (part.equals("tsuba") ? "Complete hilt" : "Saya"));
            zh.addProperty(prefix + ".name", titleZh + " · "
                    + (part.equals("tsuba") ? "仿造镡柄刀装" : "仿造刀鞘"));
            en.addProperty(prefix + ".description",
                    "Imprinted fitting. A matching saya and hilt form affinity and inherit registered orthodox abilities.");
            zh.addProperty(prefix + ".description",
                    "同源刀鞘与镡柄刀装形成映锻契合，并继承原刀登记的正传能力。");
            en.addProperty(prefix + ".slot1", "Proud Soul Ingot");
            zh.addProperty(prefix + ".slot1", "耀魂铁锭");
            en.addProperty(prefix + ".slot2", "Source recipe material");
            zh.addProperty(prefix + ".slot2", "原刀配方材料");
        }

        String scroll = "item.tetra.scroll.blade_tetra.legacy_auto."
                + kind.id().replace('/', '.');
        en.addProperty(scroll + ".name", titleEn);
        zh.addProperty(scroll + ".name", titleZh);
        en.addProperty(scroll + ".prefix", "Imprinting pattern");
        zh.addProperty(scroll + ".prefix", "映锻刀谱");
        en.addProperty(scroll + ".description",
                "Retired pattern. Use named ingots in ordinary Tetra forging.");
        zh.addProperty(scroll + ".description",
                "旧刀谱已退役；请在普通Tetra锻造中使用名刀锭。");
        en.addProperty(scroll + ".details",
                "Kept for historical saves. Matching named-material fittings retain registered SA/SE inheritance.");
        zh.addProperty(scroll + ".details",
                "保留旧存档记录；同源名刀锭刀装仍可继承原刀登记的SA或SE。");
    }

    private void putGenericModule(String part) {
        JsonObject module = new JsonObject();
        module.addProperty("replace", true);
        JsonArray slots = new JsonArray();
        slots.add("slashblade/" + part);
        module.add("slots", slots);
        module.addProperty("type", part.equals("blade") ? "tetra:basic_major_module" : "tetra:basic_module");
        if (part.equals("blade")) {
            module.add("improvements", JsonParser.parseString("[\"tetra:sword/basic_blade/\",\"tetra:sword/shared_blade/\",\"tetra:sword/shared/\",\"tetra:shared/\",\"tetra:slashblade/blade/\"]"));
        }
        module.addProperty("renderLayer", part.equals("saya") ? "lower" : "higher");
        JsonArray variants = new JsonArray();
        JsonObject variant = new JsonObject();
        variant.addProperty("key", NamedLegacyImprintStorage.genericVariant(part));
        variant.addProperty("durability", part.equals("blade") ? 120 : part.equals("saya") ? 24 : 0);
        if (part.equals("blade")) {
            variant.add("attributes", JsonParser.parseString("{\"generic.attack_speed\":-1.9,\"generic.attack_damage\":4}"));
            variant.add("tools", JsonParser.parseString("{\"cut\":[1,2]}"));
            variant.add("aspects", JsonParser.parseString("{\"edged_weapon\":2,\"breakable\":2}"));
            variant.add("tags", JsonParser.parseString("[\"forge:swords\"]"));
        }
        variant.addProperty("integrity", part.equals("saya") ? 1 : 0);
        variants.add(variant);
        module.add("variants", variants);
        putUnique("data/tetra/modules/slashblade/legacy_" + part + ".json", module.toString());
    }

    private void putUnique(String path, String value) {
        if (path.contains("..") || resources.putIfAbsent(path,
                value.getBytes(StandardCharsets.UTF_8)) != null) {
            throw new IllegalStateException("duplicate generated pack resource " + path);
        }
    }

    private IoSupplier<InputStream> resource(String path) {
        byte[] bytes = resources.get(path);
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return resource(String.join("/", path));
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        return resource(type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath());
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? Set.of("tetra") : Set.of("blade_tetra");
    }

    @Override
    public void listResources(PackType type, String namespace, String path,
            PackResources.ResourceOutput output) {
        String prefix = type.getDirectory() + "/" + namespace + "/";
        resources.keySet().stream()
                .filter(key -> key.startsWith(prefix + path))
                .forEach(key -> output.accept(
                        new ResourceLocation(namespace, key.substring(prefix.length())), resource(key)));
    }

    @Override
    public void close() {}

    @SubscribeEvent
    public static void addPack(AddPackFindersEvent event) {
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate("blade_tetra_named_patterns",
                    Component.literal("Blade Tetra named patterns"), true, NamedLegacyPack::new,
                    event.getPackType(), Pack.Position.BOTTOM, PackSource.BUILT_IN);
            if (pack != null) consumer.accept(pack);
        });
    }
}

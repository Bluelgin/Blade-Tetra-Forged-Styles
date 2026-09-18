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
import java.util.Map;
import java.util.Set;

/**
 * Required in-memory Tetra data pack.
 *
 * <p>V2 keeps per-blade schematic ids for old scroll compatibility and for a
 * clear source choice in Tetra's UI, but all named blades share only two module
 * variants. The source blade id is written to Blade Tetra NBT by a crafting
 * effect, so addon count no longer expands Tetra's module-variant table.</p>
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
        JsonArray historicalImprovements = new JsonArray();
        List<LegacyImprintKind> accepted = new ArrayList<>();

        for (LegacyImprintKind kind : NamedLegacyCatalog.values()) {
            try {
                Map<String, byte[]> staged = buildKindResources(kind);
                ensureNoCollisions(staged);
                resources.putAll(staged);
                accepted.add(kind);
                appendKindLanguage(kind, en, zh);

                // Retain the old grip improvement definition so historical saves do not
                // lose an improvement key merely because V2 no longer crafts it.
                JsonObject improvement = new JsonObject();
                improvement.addProperty("key", kind.improvement());
                improvement.addProperty("level", 1);
                historicalImprovements.add(improvement);
            } catch (RuntimeException exception) {
                LogUtils.getLogger().warn(
                        "Skipping named imprint pack resources for {} without affecting other blades",
                        kind.id(), exception);
            }
        }

        putUnique("data/tetra/improvements/slashblade/tsuka/legacy_auto.json",
                historicalImprovements.toString());
        putGenericModule("saya");
        putGenericModule("tsuba");

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

        putUnique("assets/blade_tetra/lang/en_us.json", en.toString());
        putUnique("assets/blade_tetra/lang/zh_cn.json", zh.toString());
        LogUtils.getLogger().info(
                "Blade Tetra named-pattern pack staged {} blades using 2 generic module variants",
                accepted.size());
    }

    private Map<String, byte[]> buildKindResources(LegacyImprintKind kind) {
        if (kind == null || kind.id() == null || kind.id().isBlank()) {
            throw new IllegalArgumentException("blank kind id");
        }
        Map<String, byte[]> staged = new LinkedHashMap<>();
        for (String part : List.of("saya", "tsuba")) {
            String schema = kind.schematic(part);
            ResourceLocation schemaId = ResourceLocation.tryParse("tetra:" + schema);
            if (schemaId == null) {
                throw new IllegalArgumentException("invalid schematic id " + schema);
            }
            JsonObject root = JsonParser.parseString("""
                    {"replace":true,"materialSlotCount":2,"displayType":"minor",
                     "glyph":{"textureX":24,"textureY":112},
                     "outcomes":[]}
                    """).getAsJsonObject();
            JsonArray slots = new JsonArray();
            slots.add("slashblade/" + part);
            root.add("slots", slots);

            JsonObject requirement = new JsonObject();
            requirement.addProperty("type", "blade_tetra:legacy_pattern");
            requirement.addProperty("id", kind.id());
            requirement.addProperty("part", part);
            root.add("requirement", requirement);

            JsonObject core = JsonParser.parseString("""
                    {"material":{"items":["slashblade:proudsoul_ingot"],"count":1},
                     "materialSlot":0,"requiredTools":{"hammer_dig":"minecraft:iron"}}
                    """).getAsJsonObject();
            core.addProperty("moduleKey", "slashblade/legacy_" + part);
            core.addProperty("moduleVariant", NamedLegacyImprintStorage.genericVariant(part));

            JsonObject feature = new JsonObject();
            JsonObject material = new JsonObject();
            JsonArray items = new JsonArray();
            ResourceLocation featureId = ResourceLocation.tryParse(kind.material());
            if (featureId == null) {
                throw new IllegalArgumentException("invalid source material " + kind.material());
            }
            items.add(featureId.toString());
            material.add("items", items);
            material.addProperty("count", 1);
            feature.add("material", material);
            feature.addProperty("materialSlot", 1);

            root.getAsJsonArray("outcomes").add(core);
            root.getAsJsonArray("outcomes").add(feature);
            stage(staged, "data/tetra/schematics/" + schema + ".json", root.toString());
        }
        return staged;
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
                "Unlocks this named blade's fittings. Unroll beside a Tetra workbench.");
        zh.addProperty(scroll + ".description",
                "解锁对应名刀的刀装仿造，展开并放在Tetra工作台附近。");
        en.addProperty(scroll + ".details",
                "Reproduce the saya and complete hilt while the blade remains modular. Matching fittings grant affinity and registered SA/SE inheritance.");
        zh.addProperty(scroll + ".details",
                "可仿造刀鞘和完整镡柄刀装，刀身仍由模块化锻造；同源形制形成动态契合，并继承原刀登记的SA或SE。");
    }

    private void putGenericModule(String part) {
        JsonObject module = new JsonObject();
        module.addProperty("replace", true);
        JsonArray slots = new JsonArray();
        slots.add("slashblade/" + part);
        module.add("slots", slots);
        module.addProperty("type", "tetra:basic_module");
        module.addProperty("renderLayer", part.equals("saya") ? "lower" : "higher");
        JsonArray variants = new JsonArray();
        JsonObject variant = new JsonObject();
        variant.addProperty("key", NamedLegacyImprintStorage.genericVariant(part));
        variant.addProperty("durability", part.equals("saya") ? 24 : 0);
        variant.addProperty("integrity", part.equals("saya") ? 1 : 0);
        variants.add(variant);
        module.add("variants", variants);
        putUnique("data/tetra/modules/slashblade/legacy_" + part + ".json", module.toString());
    }

    private void ensureNoCollisions(Map<String, byte[]> staged) {
        for (String path : staged.keySet()) {
            if (resources.containsKey(path)) {
                throw new IllegalStateException("duplicate generated resource " + path);
            }
        }
    }

    private static void stage(Map<String, byte[]> target, String path, String value) {
        if (path.contains("..") || target.putIfAbsent(path,
                value.getBytes(StandardCharsets.UTF_8)) != null) {
            throw new IllegalArgumentException("unsafe or duplicate generated path " + path);
        }
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

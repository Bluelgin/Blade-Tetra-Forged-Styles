package dev.bladetetra.forging;

import com.google.gson.*;
import dev.bladetetra.BladeTetra;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Required in-memory Tetra data pack; only JSON is generated, never copied models. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NamedLegacyPack extends AbstractPackResources {
    private final Map<String, byte[]> resources = new LinkedHashMap<>();
    public NamedLegacyPack(String id) {
        super(id, true);
        put("pack.mcmeta", "{\"pack\":{\"pack_format\":15,\"description\":\"Named blade imprint patterns\"}}");
        JsonObject en = new JsonObject();
        JsonObject zh = new JsonObject();
        JsonArray improvements = new JsonArray();
        for (LegacyImprintKind kind : NamedLegacyCatalog.values()) {
            JsonObject improvement = new JsonObject();
            improvement.addProperty("key", kind.improvement());
            improvement.addProperty("level", 1);
            improvements.add(improvement);
            String improvementLang = "tetra.improvement." + kind.improvement();
            en.addProperty(improvementLang + ".name", NamedLegacyCatalog.localizedName(kind, "en_us") + " old grip record");
            zh.addProperty(improvementLang + ".name", NamedLegacyCatalog.localizedName(kind, "zh_cn") + " · 旧仿造柄记录");
            en.addProperty(improvementLang + ".description", "Retained for old saves; no longer changes the grip independently.");
            zh.addProperty(improvementLang + ".description", "保留旧存档记录，不再独立改变刀柄外观。");
            for (String part : List.of("saya", "tsuba")) {
                String schema = kind.schematic(part);
                JsonObject root = JsonParser.parseString("""
                        {"replace":true,"materialSlotCount":2,"displayType":"minor",
                         "glyph":{"textureX":24,"textureY":112},
                         "outcomes":[]}
                        """).getAsJsonObject();
                JsonArray slots = new JsonArray(); slots.add("slashblade/" + part); root.add("slots", slots);
                if (part.equals("tsuka")) root.addProperty("displayType", "improvement");
                JsonObject requirement = new JsonObject();
                requirement.addProperty("type", "blade_tetra:legacy_pattern");
                requirement.addProperty("id", kind.id());
                requirement.addProperty("part", part);
                root.add("requirement", requirement);
                JsonObject core = JsonParser.parseString("""
                        {"material":{"items":["slashblade:proudsoul_ingot"],"count":1},
                         "materialSlot":0,"requiredTools":{"hammer_dig":"minecraft:iron"}}
                        """).getAsJsonObject();
                String moduleVariant = "legacy_" + part + "/" + kind.id();
                if (part.equals("tsuka")) {
                    JsonObject values = new JsonObject(); values.addProperty(kind.improvement(), 1);
                    core.add("improvements", values);
                } else {
                    core.addProperty("moduleKey", "slashblade/legacy_" + part);
                    core.addProperty("moduleVariant", moduleVariant);
                }
                JsonObject feature = new JsonObject();
                JsonObject material = new JsonObject();
                JsonArray items = new JsonArray(); items.add(kind.material()); material.add("items", items);
                material.addProperty("count", 1); feature.add("material", material);
                feature.addProperty("materialSlot", 1);
                root.getAsJsonArray("outcomes").add(core);
                root.getAsJsonArray("outcomes").add(feature);
                put("data/tetra/schematics/" + schema + ".json", root.toString());
                String title = NamedLegacyCatalog.localizedName(kind, "en_us");
                String prefix = "tetra/schematic/" + schema;
                en.addProperty(prefix + ".name", title + " · " + (part.equals("tsuba") ? "Complete hilt" : "Saya"));
                zh.addProperty(prefix + ".name", NamedLegacyCatalog.localizedName(kind, "zh_cn") + " · " + switch(part) {
                    case "saya" -> "仿造刀鞘"; case "tsuba" -> "仿造镡柄刀装"; default -> "仿造刀柄"; });
                String variant = "tetra.variant." + moduleVariant;
                en.addProperty(variant, title + " · "
                        + (part.equals("tsuba") ? "Imprinted hilt" : "Imprinted saya"));
                zh.addProperty(variant, NamedLegacyCatalog.localizedName(kind, "zh_cn") + " · "
                        + (part.equals("tsuba") ? "仿造镡柄刀装" : "仿造刀鞘"));
                en.addProperty(prefix + ".description", "Imprinted fitting. A matching saya and hilt form affinity and inherits the original blade's registered orthodox abilities.");
                zh.addProperty(prefix + ".description", "同源刀鞘与镡柄刀装形成映锻契合，并继承原刀登记的正传能力。");
                en.addProperty(prefix + ".slot1", "Proud Soul Ingot"); zh.addProperty(prefix + ".slot1", "耀魂铁锭");
                en.addProperty(prefix + ".slot2", "Source recipe material"); zh.addProperty(prefix + ".slot2", "原刀配方材料");
            }
            String scroll = "item.tetra.scroll.blade_tetra.legacy_auto." + kind.id().replace('/', '.');
            en.addProperty(scroll + ".name", NamedLegacyCatalog.localizedName(kind, "en_us"));
            zh.addProperty(scroll + ".name", NamedLegacyCatalog.localizedName(kind, "zh_cn"));
            en.addProperty(scroll + ".prefix", "Imprinting pattern"); zh.addProperty(scroll + ".prefix", "映锻刀谱");
            en.addProperty(scroll + ".description", "Unlocks this named blade's fittings. Unroll beside a Tetra workbench.");
            zh.addProperty(scroll + ".description", "解锁对应名刀的刀装仿造，展开并放在Tetra工作台附近。");
            en.addProperty(scroll + ".details", "Reproduce the saya and complete hilt while the blade remains modular. A matching form grants dynamic affinity and the original blade's registered SA or SE.");
            zh.addProperty(scroll + ".details", "可仿造刀鞘和完整镡柄刀装，刀身仍由模块化锻造；同源形制会形成动态契合，并继承原刀登记的SA或SE。");
        }
        put("data/tetra/improvements/slashblade/tsuka/legacy_auto.json", improvements.toString());
        for (String part : List.of("saya", "tsuba")) {
            JsonObject module = new JsonObject();
            module.addProperty("replace", true);
            JsonArray slots = new JsonArray(); slots.add("slashblade/" + part); module.add("slots", slots);
            module.addProperty("type", "tetra:basic_module");
            module.addProperty("renderLayer", part.equals("saya") ? "lower" : "higher");
            JsonArray variants = new JsonArray();
            for (LegacyImprintKind kind : NamedLegacyCatalog.values()) {
                JsonObject variant = new JsonObject();
                variant.addProperty("key", "legacy_" + part + "/" + kind.id());
                variant.addProperty("durability", part.equals("saya") ? 24 : 0);
                variant.addProperty("integrity", part.equals("saya") ? 1 : 0);
                variants.add(variant);
            }
            module.add("variants", variants);
            put("data/tetra/modules/slashblade/legacy_" + part + ".json", module.toString());
            en.addProperty("tetra.module.slashblade/legacy_" + part + ".name", part.equals("tsuba") ? "Imprinted hilt" : "Imprinted saya");
            zh.addProperty("tetra.module.slashblade/legacy_" + part + ".name", part.equals("saya") ? "映锻刀鞘" : "映锻镡柄刀装");
        }
        put("assets/blade_tetra/lang/en_us.json", en.toString());
        put("assets/blade_tetra/lang/zh_cn.json", zh.toString());
    }
    private void put(String path, String value) { resources.put(path, value.getBytes(StandardCharsets.UTF_8)); }
    private IoSupplier<InputStream> resource(String path) {
        byte[] bytes = resources.get(path);
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }
    @Override public IoSupplier<InputStream> getRootResource(String... path) { return resource(String.join("/", path)); }
    @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        return resource(type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath());
    }
    @Override public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? Set.of("tetra") : Set.of("blade_tetra");
    }
    @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        String prefix = type.getDirectory() + "/" + namespace + "/";
        resources.keySet().stream().filter(key -> key.startsWith(prefix + path))
                .forEach(key -> output.accept(new ResourceLocation(namespace, key.substring(prefix.length())), resource(key)));
    }
    @Override public void close() {}
    @SubscribeEvent public static void addPack(AddPackFindersEvent event) {
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate("blade_tetra_named_patterns",
                    Component.literal("Blade Tetra named patterns"), true, NamedLegacyPack::new,
                    event.getPackType(), Pack.Position.BOTTOM, PackSource.BUILT_IN);
            if (pack != null) consumer.accept(pack);
        });
    }
}

package dev.btsealed.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.util.thread.SidedThreadGroups;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Portable profiles are selected by Resharped's immutable definition key, not the anvil display name. */
final class PortableProfiles {
    private static volatile Map<String, Profile> serverProfiles = Map.of();
    private static volatile Map<String, Profile> clientProfiles = Map.of();

    record Modifier(Attribute attribute, AttributeModifier modifier) {}
    record Move(ResourceLocation action, int minimumTicks) {}
    record Profile(List<Modifier> modifiers, List<Move> moves, PortableComponentEffects.Effects effects) {}

    static Profile forAttributes(ItemStack stack) {
        if (stack.getCapability(ItemSlashBlade.BLADESTATE).map(state -> state.isBroken()).orElse(false)) return null;
        return forBlade(stack, Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER
                || FMLEnvironment.dist.isDedicatedServer() ? LogicalSide.SERVER : LogicalSide.CLIENT);
    }

    static Profile forBlade(ItemStack stack, LogicalSide side) {
        if (!stack.is(SlashBladeItems.SLASHBLADE.get())) return null;
        var profiles = side.isClient() ? clientProfiles : serverProfiles;
        var state = stack.getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        return state == null ? null : profiles.get(state.getTranslationKey());
    }

    static boolean hasSealedBlade(ResourceLocation id) {
        return serverProfiles.containsKey("item." + id.getNamespace() + "." + id.getPath());
    }

    static SimpleJsonResourceReloadListener serverListener() {
        return listener(profiles -> serverProfiles = profiles);
    }

    static SimpleJsonResourceReloadListener clientListener() {
        return listener(profiles -> clientProfiles = profiles);
    }

    private static SimpleJsonResourceReloadListener listener(Consumer<Map<String, Profile>> publish) {
        return new SimpleJsonResourceReloadListener(new Gson(), "blade_export") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                    ProfilerFiller profiler) {
                Map<String, Profile> next = new HashMap<>();
                files.forEach((id, file) -> {
                    if (id.getNamespace().startsWith("bt_export_") && id.getPath().equals("sealed_blade")) {
                        next.put("item." + id.getNamespace() + "." + id.getPath(), read(id, file.getAsJsonObject()));
                    }
                });
                publish.accept(Map.copyOf(next));
            }
        };
    }

    private static Profile read(ResourceLocation id, JsonObject json) {
        if (json.get("schema").getAsInt() != 1) {
            throw new IllegalArgumentException("Unsupported blade profile schema: " + id);
        }
        var attributes = json.getAsJsonArray("attributes");
        if (attributes.size() > 64) throw new IllegalArgumentException("Too many modifiers: " + id);
        var modifiers = new ArrayList<Modifier>();
        var keys = new HashSet<Map.Entry<ResourceLocation, UUID>>();
        for (JsonElement entry : attributes) {
            JsonObject data = entry.getAsJsonObject();
            var key = ResourceLocation.parse(data.get("id").getAsString());
            var attribute = ForgeRegistries.ATTRIBUTES.getValue(key);
            double amount = data.get("amount").getAsDouble();
            var uuid = UUID.fromString(data.get("modifierId").getAsString());
            if (attribute == null || !Double.isFinite(amount) || !keys.add(Map.entry(key, uuid))) {
                throw new IllegalArgumentException("Invalid portable attribute: " + key);
            }
            var operation = AttributeModifier.Operation.valueOf(data.get("operation").getAsString());
            modifiers.add(new Modifier(attribute, new AttributeModifier(uuid, "Sealed blade", amount, operation)));
        }
        var moves = new ArrayList<Move>();
        JsonArray actions = json.getAsJsonArray("moves");
        if (actions.size() > 6) throw new IllegalArgumentException("Too many custom moves: " + id);
        for (JsonElement entry : actions) {
            JsonObject data = entry.getAsJsonObject();
            var action = ResourceLocation.parse(data.get("action").getAsString());
            int ticks = data.get("minimumTicks").getAsInt();
            if (!PortableCombos.hasMove(action) || ticks < 1 || ticks > 40) {
                throw new IllegalArgumentException("Unsupported portable action: " + action);
            }
            moves.add(new Move(action, ticks));
        }
        var effectData = json.getAsJsonObject("effects");
        if (effectData != null && effectData.has("unsupported")
                && effectData.get("unsupported").getAsBoolean()) {
            throw new IllegalArgumentException("Exported sword contains an explicitly unsupported effect: " + id);
        }
        var loadedEffects = effects(effectData);
        var declared = json.getAsJsonArray("capabilities");
        if (declared == null || !json.has("comboRoot")) {
            throw new IllegalArgumentException("Missing versioned portable capability manifest: " + id);
        }
        List<String> ids = new ArrayList<>();
        for (JsonElement feature : declared) {
            String capability = feature.getAsString();
            PortableCapabilityCatalog.requireKnown(capability);
            ids.add(capability);
        }
        var expected = PortableCapabilityCatalog.forShape(json.get("comboRoot").getAsString(),
                !modifiers.isEmpty(), moves.size(), loadedEffects.drawBonus() > 0,
                loadedEffects.spiritBonus() > 0, loadedEffects.heavyPommel(),
                loadedEffects.rankDivisor() > 0, loadedEffects.guardReduction() > 0,
                loadedEffects.precisionHabaki(), loadedEffects.reinforcedHabaki());
        if (!ids.equals(expected)) {
            throw new IllegalArgumentException("Portable capability manifest disagrees with actual blade data: "
                    + id + " (declared=" + ids + ", expected=" + expected + ")");
        }
        return new Profile(List.copyOf(modifiers), List.copyOf(moves), loadedEffects);
    }

    private static PortableComponentEffects.Effects effects(JsonObject json) {
        if (json == null) return PortableComponentEffects.Effects.NONE;
        return new PortableComponentEffects.Effects(
                factor(json.get("drawBonus").getAsDouble()), factor(json.get("spiritBonus").getAsDouble()),
                bounded(json.get("spiritTicks").getAsInt(), 200), json.get("heavyPommel").getAsBoolean(),
                bounded(json.get("rankDivisor").getAsInt(), 1000), (float) factor(json.get("guardReduction").getAsDouble()),
                bounded(json.get("guardDurability").getAsInt(), 32),
                json.has("precisionHabaki") && json.get("precisionHabaki").getAsBoolean(),
                json.has("reinforcedHabaki") && json.get("reinforcedHabaki").getAsBoolean());
    }

    private static double factor(double amount) {
        if (!Double.isFinite(amount) || amount < 0 || amount >= 1) {
            throw new IllegalArgumentException("Invalid sealed blade effect factor: " + amount);
        }
        return amount;
    }

    private static int bounded(int amount, int max) {
        if (amount < 0 || amount > max) throw new IllegalArgumentException("Invalid sealed blade effect value");
        return amount;
    }

    private PortableProfiles() {}
}

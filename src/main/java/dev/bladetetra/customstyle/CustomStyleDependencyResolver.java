package dev.bladetetra.customstyle;

import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.apache.maven.artifact.versioning.VersionRange;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Trusted adapters identify providers. Share-code metadata can never grant an action. */
public final class CustomStyleDependencyResolver {
    public enum Status { AVAILABLE, MISSING_MOD, INCOMPATIBLE_VERSION, UNREGISTERED_ACTION, UNSAFE_ACTION }
    public record Provider(String modId, String displayName, String versionRange) {
        public Provider {
            if (modId == null || !modId.matches("[a-z][a-z0-9_]{1,63}")
                    || displayName == null || displayName.length() > 96
                    || displayName.chars().anyMatch(Character::isISOControl) || versionRange == null
                    || versionRange.length() > 96) throw new IllegalArgumentException("Invalid provider");
            if (!versionRange.isEmpty()) try { VersionRange.createFromVersionSpec(versionRange); }
            catch (Exception exception) { throw new IllegalArgumentException("Invalid trusted version range", exception); }
        }
    }
    public record ActionInfo(ResourceLocation action, String modId, String provider, String installedVersion,
                             String requiredVersion, Status status) {}
    public record Preview(CustomStyleExchange.Status formatStatus, CustomStyleSpec style,
                          List<ActionInfo> actions, List<String> missingDeclaredMods) {
        public boolean allowed() { return formatStatus == CustomStyleExchange.Status.OK && style != null
                && missingDeclaredMods.isEmpty() && actions.stream().allMatch(it -> it.status == Status.AVAILABLE); }
    }
    private static final Map<ResourceLocation, Provider> ACTION_PROVIDERS = new ConcurrentHashMap<>();
    private static final Map<String, Provider> KNOWN_NAMESPACES = Map.of(
            "slashblade", new Provider("slashblade", "SlashBlade: Resharped", ""),
            "blade_tetra", new Provider("blade_tetra", "Blade Tetra", ""));

    /** Register even if the optional mod is absent. Namespace and mod ID may differ. */
    public static void registerProvider(ResourceLocation action, Provider provider) {
        if (action == null || provider == null) throw new IllegalArgumentException("Provider required");
        Provider previous = ACTION_PROVIDERS.putIfAbsent(action, provider);
        if (previous != null && !previous.equals(provider)) throw new IllegalStateException("Conflicting action provider: " + action);
    }
    public static Provider provider(ResourceLocation id) {
        return ACTION_PROVIDERS.getOrDefault(id, KNOWN_NAMESPACES.get(id.getNamespace()));
    }
    public static ActionInfo diagnose(ResourceLocation id) {
        Provider provider = provider(id);
        Status status;
        String installed = "";
        if (provider != null && !ModList.get().isLoaded(provider.modId)) status = Status.MISSING_MOD;
        else {
            if (provider != null) installed = ModList.get().getModContainerById(provider.modId)
                    .map(it -> it.getModInfo().getVersion().toString()).orElse("");
            boolean compatible = true;
            if (provider != null && !provider.versionRange.isEmpty()) try {
                var version = ModList.get().getModContainerById(provider.modId).orElseThrow().getModInfo().getVersion();
                compatible = VersionRange.createFromVersionSpec(provider.versionRange).containsVersion(version);
            } catch (Exception exception) { compatible = false; }
            if (!compatible) status = Status.INCOMPATIBLE_VERSION;
            else if (!ComboStateRegistry.REGISTRY.get().containsKey(id)) status = Status.UNREGISTERED_ACTION;
            else status = ComboCompatibilityResolver.playable(id) ? Status.AVAILABLE : Status.UNSAFE_ACTION;
        }
        return new ActionInfo(id, provider == null ? "" : provider.modId,
                provider == null ? "" : provider.displayName, installed,
                provider == null ? "" : provider.versionRange, status);
    }
    public static Preview inspect(String code) {
        var parsed = CustomStyleExchange.inspect(code);
        if (!parsed.accepted()) return new Preview(parsed.status(), null, List.of(), List.of());
        return new Preview(parsed.status(), parsed.style(), parsed.style().actions().stream()
                .map(CustomStyleDependencyResolver::diagnose).toList(), parsed.requirements().stream()
                .map(CustomStyleExchange.Requirement::modId).filter(id -> !ModList.get().isLoaded(id)).distinct().toList());
    }
    private CustomStyleDependencyResolver() {}
}

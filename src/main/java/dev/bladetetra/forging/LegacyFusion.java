package dev.bladetetra.forging;

import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.world.item.ItemStack;

/**
 * Ordered, deliberately authored combinations of two imprinted named-blade
 * fittings. The saya is the release form and the complete hilt is the response,
 * so reversing the same two sources may resolve to another legacy entirely.
 */
public enum LegacyFusion {
    BLACK_SAYA_WHITE_HILT(
            "black_saya_white_hilt"),
    WHITE_SAYA_BLACK_HILT(
            "white_saya_black_hilt"),
    YASHA_SAYA_KIKOUKU_HILT(
            "yasha_saya_kikouku_hilt"),
    SEALED_AGITO_SAYA_OROTIAGITO_HILT(
            "sealed_agito_saya_orotiagito_hilt");

    private final String id;

    LegacyFusion(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String improvement() {
        LegacyFusionDefinition definition = definition();
        return definition == null ? "" : definition.improvement();
    }

    public Ability ability() {
        LegacyFusionDefinition definition = definition();
        return definition != null
                && definition.abilityType() == LegacyFusionDefinition.AbilityType.SPECIAL_EFFECT
                ? Ability.SPECIAL_EFFECT : Ability.SLASH_ART;
    }

    public net.minecraft.resources.ResourceLocation abilityId() {
        LegacyFusionDefinition definition = definition();
        return definition == null ? null : definition.ability();
    }

    public boolean matches(NamedLegacyParts parts) {
        LegacyFusionDefinition definition = definition();
        return definition != null && definition.matches(parts);
    }

    public boolean isAttuned(ItemStack stack) {
        return matches(NamedLegacyParts.fromStack(stack))
                && ForgingImprovements.has(stack,
                ModularSlashBladeItem.BLADE_SLOT, improvement());
    }

    public static LegacyFusion installed(ItemStack stack) {
        return installed(NamedLegacyParts.fromStack(stack));
    }

    public static LegacyFusion installed(NamedLegacyParts parts) {
        LegacyFusionDefinition definition = LegacyFusionCatalog.resolve(parts);
        return definition == null ? null : byId(definition.id());
    }

    public static LegacyFusion active(ItemStack stack) {
        LegacyFusion installed = installed(stack);
        return installed != null && installed.isAttuned(stack) ? installed : null;
    }

    public static LegacyFusion byId(String id) {
        for (LegacyFusion fusion : values()) {
            if (fusion.id.equals(id)) return fusion;
        }
        return null;
    }

    private LegacyFusionDefinition definition() {
        return LegacyFusionCatalog.get(id);
    }

    public enum Ability {
        SLASH_ART,
        SPECIAL_EFFECT
    }
}

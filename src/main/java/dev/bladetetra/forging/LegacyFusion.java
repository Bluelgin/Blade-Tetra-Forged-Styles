package dev.bladetetra.forging;

import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * Ordered, deliberately authored combinations of two imprinted named-blade
 * fittings. The saya is the release form and the complete hilt is the response,
 * so reversing the same two sources may resolve to another legacy entirely.
 */
public enum LegacyFusion {
    BLACK_SAYA_WHITE_HILT(
            "black_saya_white_hilt",
            "fox_black",
            "fox_white",
            "blade_tetra/legacy_fusion/black_saya_white_hilt",
            Ability.SLASH_ART),
    WHITE_SAYA_BLACK_HILT(
            "white_saya_black_hilt",
            "fox_white",
            "fox_black",
            "blade_tetra/legacy_fusion/white_saya_black_hilt",
            Ability.SPECIAL_EFFECT),
    YASHA_SAYA_KIKOUKU_HILT(
            "yasha_saya_kikouku_hilt",
            "slashblade/yasha",
            "slashblade/yasha_true",
            "blade_tetra/legacy_fusion/yasha_saya_kikouku_hilt",
            Ability.SLASH_ART);

    private final String id;
    private final String sayaId;
    private final String hiltId;
    private final String improvement;
    private final Ability ability;

    LegacyFusion(String id, String sayaId, String hiltId,
            String improvement, Ability ability) {
        this.id = id;
        this.sayaId = sayaId;
        this.hiltId = hiltId;
        this.improvement = improvement;
        this.ability = ability;
    }

    public String id() {
        return id;
    }

    public String improvement() {
        return improvement;
    }

    public Ability ability() {
        return ability;
    }

    public boolean matches(NamedLegacyParts parts) {
        return parts != null
                && parts.saya() != null
                && parts.tsuba() != null
                && sayaId.equals(parts.saya().id())
                && hiltId.equals(parts.tsuba().id());
    }

    public boolean isAttuned(ItemStack stack) {
        return matches(NamedLegacyParts.fromStack(stack))
                && ForgingImprovements.has(stack,
                ModularSlashBladeItem.BLADE_SLOT, improvement);
    }

    public static LegacyFusion installed(ItemStack stack) {
        return installed(NamedLegacyParts.fromStack(stack));
    }

    public static LegacyFusion installed(NamedLegacyParts parts) {
        return Arrays.stream(values()).filter(fusion -> fusion.matches(parts))
                .findFirst().orElse(null);
    }

    public static LegacyFusion active(ItemStack stack) {
        LegacyFusion installed = installed(stack);
        return installed != null && installed.isAttuned(stack) ? installed : null;
    }

    public static LegacyFusion byId(String id) {
        return Arrays.stream(values()).filter(fusion -> fusion.id.equals(id))
                .findFirst().orElse(null);
    }

    public enum Ability {
        SLASH_ART,
        SPECIAL_EFFECT
    }
}

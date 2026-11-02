package dev.bladetetra.forging;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.item.ItemStack;

/**
 * Single, server-safe source-of-truth for named-ingot extraction.
 *
 * <p>Uses the exact same semantic eligibility as the existing imprint tooltip.
 * Rendering support is deliberately not queried here: client OBJ/resource checks
 * cannot run safely or authoritatively on a dedicated or integrated server thread.
 * Unsupported visuals keep their semantic identity and use the existing model
 * fallback. The client tooltip distinguishes that limitation explicitly.</p>
 */
public final class NamedBladeIngotEligibility {
    public enum Status {
        READY,
        UNKNOWN_SOURCE,
        INSUFFICIENT_DURABILITY
    }

    public record Result(Status status, LegacyImprintKind kind, int durabilityCost) {
        public boolean extractable() {
            return status == Status.READY && kind != null;
        }
    }

    public static Result inspect(ItemStack source) {
        LegacyResearchability.Result semantic = LegacyResearchability.inspect(source);
        if (!semantic.researchable()) {
            return new Result(Status.UNKNOWN_SOURCE, null, 0);
        }

        LegacyImprintKind kind = semantic.kind();
        return source.getCapability(ItemSlashBlade.BLADESTATE).resolve()
                .map(state -> {
                    int maximum = state.getMaxDamage();
                    int wear = cost(maximum);
                    if (maximum <= 1 || state.getDamage() < 0
                            || (long) state.getDamage() + wear >= maximum) {
                        return new Result(Status.INSUFFICIENT_DURABILITY, kind, wear);
                    }
                    return new Result(Status.READY, kind, wear);
                }).orElseGet(() -> new Result(Status.UNKNOWN_SOURCE, null, 0));
    }

    public static int cost(int maximum) {
        return Math.max(1, (int) Math.ceil(Math.max(2, maximum) * 0.10D));
    }

    private NamedBladeIngotEligibility() {}
}

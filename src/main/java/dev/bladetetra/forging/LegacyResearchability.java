package dev.bladetetra.forging;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.item.ItemStack;

/**
 * Shared semantic eligibility for named-blade research.
 *
 * <p>This class is deliberately side-neutral. It answers whether the stack is a
 * valid research subject from gameplay/state data only. Client-only geometry
 * support is layered on top by {@code LegacyResearchabilityClient}.</p>
 */
public final class LegacyResearchability {
    public enum Status {
        RESEARCHABLE,
        NOT_SLASHBLADE,
        UNKNOWN_BLADE,
        DEGRADED_STATE
    }

    public record Result(Status status, LegacyImprintKind kind) {
        public boolean researchable() {
            return status == Status.RESEARCHABLE && kind != null;
        }
    }

    public static Result inspect(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemSlashBlade)) {
            return new Result(Status.NOT_SLASHBLADE, null);
        }
        return stack.getCapability(ItemSlashBlade.BLADESTATE)
                .resolve()
                .map(state -> {
                    // Broken state definitions may deliberately share the intact blade's
                    // translation key (Yamato is the canonical example). Do not let that
                    // alias turn a broken blade into a researchable intact blade. Sealed
                    // is not rejected here because some named blades are authored as
                    // legitimate sealed forms with their own catalog identity.
                    if (!stateAllowsResearch(state.isBroken(), state.isSealed())) {
                        return new Result(Status.DEGRADED_STATE, null);
                    }
                    LegacyImprintKind kind = LegacyImprintKind.fromTranslationKey(
                            state.getTranslationKey());
                    return kind == null
                            ? new Result(Status.UNKNOWN_BLADE, null)
                            : new Result(Status.RESEARCHABLE, kind);
                })
                .orElseGet(() -> new Result(Status.UNKNOWN_BLADE, null));
    }

    public static LegacyImprintKind identify(ItemStack stack) {
        Result result = inspect(stack);
        return result.researchable() ? result.kind() : null;
    }

    static boolean stateAllowsResearch(boolean broken, boolean sealed) {
        return !broken;
    }

    private LegacyResearchability() {}
}

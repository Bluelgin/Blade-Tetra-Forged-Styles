package dev.bladetetra.client;

import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.LegacyResearchability;
import net.minecraft.world.item.ItemStack;

/** Client-only final research gate: semantic eligibility plus visual support. */
public final class LegacyResearchabilityClient {
    public record Result(boolean researchable, LegacyImprintKind kind, String reason) {}

    public static Result inspect(ItemStack stack) {
        LegacyResearchability.Result semantic = LegacyResearchability.inspect(stack);
        if (!semantic.researchable()) {
            return new Result(false, semantic.kind(), semantic.status().name().toLowerCase());
        }
        return inspect(semantic.kind());
    }

    public static Result inspect(LegacyImprintKind kind) {
        if (kind == null) return new Result(false, null, "unknown blade");
        if (!kind.visualUsable()) {
            return new Result(false, kind, kind.visualFailureReason());
        }
        return new Result(true, kind, "supported");
    }

    private LegacyResearchabilityClient() {}
}

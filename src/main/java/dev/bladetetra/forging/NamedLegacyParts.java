package dev.bladetetra.forging;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** tsuka is an effective appearance alias of tsuba, never an independent imitation slot. */
public record NamedLegacyParts(LegacyImprintKind saya, LegacyImprintKind tsuba,
        LegacyImprintKind tsuka) {
    public static NamedLegacyParts fromStack(ItemStack stack) {
        FoxLegacyParts old = FoxLegacyParts.fromStack(stack);
        LegacyImprintKind hilt = variant(stack, "tsuba", old.tsuba());
        // Old grip improvements remain stored, but never independently replace the core grip.
        return new NamedLegacyParts(variant(stack, "saya", old.saya()), hilt, hilt);
    }

    private static LegacyImprintKind fox(FoxLegacyParts.Color color) {
        if (color == FoxLegacyParts.Color.NONE) return null;
        LegacyImprintKind kind = NamedLegacyCatalog.get(
                color == FoxLegacyParts.Color.BLACK ? "fox_black" : "fox_white");
        return kind != null && kind.visualUsable() ? kind : null;
    }

    private static LegacyImprintKind variant(ItemStack stack, String part,
            FoxLegacyParts.Color old) {
        String id = NamedLegacyImprintStorage.sourceId(stack, part);
        if (id != null) {
            LegacyImprintKind kind = NamedLegacyCatalog.get(id);
            // Dedicated server resolver always returns usable. A physical client may
            // reject provider geometry and transparently render the ordinary fitting.
            return kind != null && kind.visualUsable() ? kind : null;
        }
        return fox(old);
    }

    private static LegacyImprintKind grip(ItemStack stack, FoxLegacyParts.Color old) {
        for (LegacyImprintKind kind : NamedLegacyCatalog.values()) {
            if (ForgingImprovements.has(stack, "slashblade/tsuka", kind.improvement())) {
                return kind;
            }
        }
        return fox(old);
    }

    public boolean present() {
        return saya != null || tsuba != null || tsuka != null;
    }

    public LegacyImprintKind completeSet() {
        return saya != null && saya.equals(tsuba) ? saya : null;
    }

    /** Records survive addon removal; ordinary module visuals become the safe fallback. */
    public static List<MissingPart> missing(ItemStack stack) {
        List<MissingPart> result = new ArrayList<>();
        for (String part : List.of("saya", "tsuba")) {
            String id = NamedLegacyImprintStorage.sourceId(stack, part);
            if (id != null && NamedLegacyCatalog.get(id) == null) {
                result.add(new MissingPart(part, id));
            }
        }
        return result;
    }

    public record MissingPart(String part, String id) {}
}

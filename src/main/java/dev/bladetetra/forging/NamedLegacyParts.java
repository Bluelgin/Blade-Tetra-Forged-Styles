package dev.bladetetra.forging;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** tsuka is an effective appearance alias of tsuba, never an independent imitation slot. */
public record NamedLegacyParts(LegacyImprintKind saya, LegacyImprintKind tsuba, LegacyImprintKind tsuka) {
    public static NamedLegacyParts fromStack(ItemStack stack) {
        FoxLegacyParts old = FoxLegacyParts.fromStack(stack);
        LegacyImprintKind hilt = variant(stack, "tsuba", old.tsuba());
        // Old grip improvements remain stored, but never independently replace the core grip.
        return new NamedLegacyParts(variant(stack, "saya", old.saya()), hilt, hilt);
    }
    private static LegacyImprintKind fox(FoxLegacyParts.Color color) {
        return color == FoxLegacyParts.Color.NONE ? null
                : NamedLegacyCatalog.get(color == FoxLegacyParts.Color.BLACK ? "fox_black" : "fox_white");
    }
    private static LegacyImprintKind variant(ItemStack stack, String part, FoxLegacyParts.Color old) {
        if (stack.getTag() == null) return null;
        String module = stack.getTag().getString("slashblade/" + part);
        String value = stack.getTag().getString(module + "_material");
        String prefix = "legacy_" + part + "/";
        return value.startsWith(prefix) ? NamedLegacyCatalog.get(value.substring(prefix.length())) : fox(old);
    }
    private static LegacyImprintKind grip(ItemStack stack, FoxLegacyParts.Color old) {
        for (LegacyImprintKind kind : NamedLegacyCatalog.values())
            if (ForgingImprovements.has(stack, "slashblade/tsuka", kind.improvement())) return kind;
        return fox(old);
    }
    public boolean present() { return saya != null || tsuba != null || tsuka != null; }
    public LegacyImprintKind completeSet() {
        return saya != null && saya.equals(tsuba) ? saya : null;
    }

    /** Records survive addon removal; ordinary module visuals become the safe fallback. */
    public static List<MissingPart> missing(ItemStack stack) {
        List<MissingPart> result = new ArrayList<>();
        for (String part : List.of("saya", "tsuba")) {
            String id = recordedId(stack, part);
            if (id != null && NamedLegacyCatalog.get(id) == null) result.add(new MissingPart(part, id));
        }
        return result;
    }

    private static String recordedId(ItemStack stack, String part) {
        if (stack.getTag() == null) return null;
        String module = stack.getTag().getString("slashblade/" + part);
        String value = stack.getTag().getString(module + "_material");
        String prefix = "legacy_" + part + "/";
        return value.startsWith(prefix) ? value.substring(prefix.length()) : null;
    }

    public record MissingPart(String part, String id) {}
}

package dev.bladetetra.forging;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Semantic named-imprint identity. Client geometry is deliberately separated via
 * {@link #visualFromStack(ItemStack)} so gameplay stays identical on client,
 * integrated server and dedicated server.
 *
 * <p>tsuka is an effective appearance alias of tsuba, never an independent imitation slot.</p>
 */
public record NamedLegacyParts(LegacyImprintKind saya, LegacyImprintKind tsuba,
        LegacyImprintKind tsuka) {
    /** Resolve source identity from NBT/catalog only; never consult client rendering. */
    public static NamedLegacyParts fromStack(ItemStack stack) {
        FoxLegacyParts old = FoxLegacyParts.fromStack(stack);
        LegacyImprintKind hilt = variant(stack, "tsuba", old.tsuba());
        // Old grip improvements remain stored, but never independently replace the core grip.
        return new NamedLegacyParts(variant(stack, "saya", old.saya()), hilt, hilt);
    }

    /** Client-only presentation view; unsupported models fall back without losing identity. */
    public static NamedLegacyParts visualFromStack(ItemStack stack) {
        NamedLegacyParts semantic = fromStack(stack);
        return new NamedLegacyParts(visible(semantic.saya), visible(semantic.tsuba),
                visible(semantic.tsuka));
    }

    private static LegacyImprintKind visible(LegacyImprintKind kind) {
        return kind != null && kind.visualUsable() ? kind : null;
    }

    private static LegacyImprintKind fox(FoxLegacyParts.Color color) {
        if (color == FoxLegacyParts.Color.NONE) return null;
        return NamedLegacyCatalog.get(
                color == FoxLegacyParts.Color.BLACK ? "fox_black" : "fox_white");
    }

    private static LegacyImprintKind variant(ItemStack stack, String part,
            FoxLegacyParts.Color old) {
        String id = NamedLegacyImprintStorage.sourceId(stack, part);
        if (id != null) {
            return NamedLegacyCatalog.get(id);
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

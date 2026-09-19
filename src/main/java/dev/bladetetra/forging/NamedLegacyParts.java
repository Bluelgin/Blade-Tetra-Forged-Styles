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
    /** Resolve source identity from persisted snapshot first; never consult client rendering. */
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
        return color == FoxLegacyParts.Color.BLACK
                ? LegacyImprintKind.BLACK_FOX : LegacyImprintKind.WHITE_FOX;
    }

    private static LegacyImprintKind variant(ItemStack stack, String part,
            FoxLegacyParts.Color old) {
        LegacyImprintKind snapshot = NamedLegacyImprintStorage.snapshot(stack, part);
        if (snapshot != null) {
            return snapshot;
        }
        String id = NamedLegacyImprintStorage.sourceId(stack, part);
        if (id != null) {
            return safeCatalogGet(id);
        }
        return fox(old);
    }

    /** Old 1.5.x source-only stacks may still need the catalog, but must fail open. */
    private static LegacyImprintKind safeCatalogGet(String id) {
        try {
            return NamedLegacyCatalog.get(id);
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    public boolean present() {
        return saya != null || tsuba != null || tsuka != null;
    }

    public LegacyImprintKind completeSet() {
        return saya != null && saya.equals(tsuba) ? saya : null;
    }

    /** Source-only legacy records report missing providers; snapshots remain self-contained. */
    public static List<MissingPart> missing(ItemStack stack) {
        List<MissingPart> result = new ArrayList<>();
        for (String part : List.of("saya", "tsuba")) {
            String id = NamedLegacyImprintStorage.sourceId(stack, part);
            if (id != null && NamedLegacyImprintStorage.snapshot(stack, part) == null
                    && safeCatalogGet(id) == null) {
                result.add(new MissingPart(part, id));
            }
        }
        return result;
    }

    public record MissingPart(String part, String id) {}
}

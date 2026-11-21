package dev.bladetetra.export;

import dev.bladetetra.customstyle.CustomBladeLength;
import dev.bladetetra.customstyle.CustomStyleTimingPolicy;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Versioned data consumed by the embedded runtime. */
record ExportBattleProfile(int schema, List<BladeExportSnapshot.AttributeValue> attributes, List<Move> moves, BladeExportSnapshot.ComponentEffects effects) {
    static final String RUNTIME_ID = "bt_export_runtime";
    static final String RUNTIME_VERSION = "1.4.0";
    static final String RESOURCE = "/META-INF/blade-export/bt-export-runtime-1.4.0.jar";
    static final ResourceLocation DANGAKU_ROOT = root("dangaku_root");
    static final ResourceLocation CUSTOM_ROOT = root("custom_root");
    static final ResourceLocation IAIDO_ROOT = root("iaido_root");
    static final ResourceLocation RENGEKI_ROOT = root("rengeki_root");

    record Move(ResourceLocation action, int minimumTicks) {}

    ExportBattleProfile {
        if (schema != 1 || attributes.size() > 64 || moves.size() > 6) {
            throw new IllegalArgumentException("Unsupported battle profile size or schema");
        }
        attributes = List.copyOf(attributes);
        moves = List.copyOf(moves);
        for (Move move : moves) {
            if (CustomStyleTimingPolicy.timing(move.action(), CustomBladeLength.STANDARD) == null
                    || move.minimumTicks() < 1 || move.minimumTicks() > 40) {
                throw new IllegalArgumentException("Unsupported portable sword move: " + move.action());
            }
        }
    }

    static ExportBattleProfile from(BladeExportSnapshot snapshot) {
        var swordStyle = snapshot.customStyle();
        if (swordStyle == null) return new ExportBattleProfile(1, snapshot.attributes(), List.of(), snapshot.componentEffects());

        var length = CustomBladeLength.valueOf(swordStyle.length());
        return new ExportBattleProfile(1, snapshot.attributes(), swordStyle.actions().stream().map(action -> {
            var timing = CustomStyleTimingPolicy.timing(action, length);
            if (timing == null) throw new IllegalArgumentException("Missing portable timeline for: " + action);
            return new Move(action, timing.effectiveTicks());
        }).toList(), snapshot.componentEffects());
    }

    boolean supports(ResourceLocation combo) {
        if (moves.isEmpty()) return IAIDO_ROOT.equals(combo) || RENGEKI_ROOT.equals(combo) || DANGAKU_ROOT.equals(combo);
        return CUSTOM_ROOT.equals(combo);
    }

    /**
     * Stable feature inventory for the exported package certificate. All listed behavior
     * must be supplied by the native definition or this exact runtime version.
     * This is an audit manifest, not a request to load separate copies of runtime classes.
     */
    List<String> capabilities(ResourceLocation combo) {
        if (effects.unsupported()) {
            throw new IllegalArgumentException("Unportable Tetra component effects cannot be certified");
        }
        return PortableCapabilityCatalog.forShape(combo.toString(), !attributes.isEmpty(), moves.size(),
                effects.drawBonus() > 0, effects.spiritBonus() > 0, effects.heavyPommel(),
                effects.rankDivisor() > 0, effects.guardReduction() > 0,
                effects.precisionHabaki(), effects.reinforcedHabaki());
    }

    private static ResourceLocation root(String name) {
        return ResourceLocation.fromNamespaceAndPath(RUNTIME_ID, name);
    }
}

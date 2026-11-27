package dev.bladetetra.export;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Shared source-of-truth for portable combat capabilities.
 *
 * <p>This file is pure Java and is mechanically copied into the independent
 * runtime with a package rewrite. A capability ID is a behavior contract, NOT
 * permission to copy Blade Tetra classes into a generated add-on. Each capability
 * has a stable schema, execution side and the earliest shared runtime version.</p>
 */
public final class PortableCapabilityCatalog {
    public enum Side { BOTH, SERVER }

    public enum Feature {
        NATIVE("slashblade:native_blade_v1", "named_blade", Side.BOTH, "1.0.0"),
        IAIDO("bt_export_runtime:iaido_v1", "combo.iaido", Side.BOTH, "1.1.0"),
        RENGEKI("bt_export_runtime:rengeki_v1", "combo.rengeki", Side.BOTH, "1.1.0"),
        DANGAKU("bt_export_runtime:dangaku_v1", "combo.dangaku", Side.BOTH, "1.1.0"),
        CUSTOM("bt_export_runtime:custom_combo_v1", "moves", Side.BOTH, "1.1.0"),
        ATTRIBUTES("bt_export_runtime:mainhand_attributes_v1", "attributes", Side.BOTH, "1.1.0"),
        QUICKDRAW("bt_export_runtime:quickdraw_saya_v1", "effects.drawBonus", Side.SERVER, "1.1.0"),
        SPIRIT("bt_export_runtime:spirit_saya_v1", "effects.spiritBonus", Side.SERVER, "1.1.0"),
        HEAVY("bt_export_runtime:heavy_kashira_v1", "effects.heavyPommel", Side.SERVER, "1.1.0"),
        STABLE("bt_export_runtime:stable_tsuka_v1", "effects.rankDivisor", Side.SERVER, "1.1.0"),
        GUARD("bt_export_runtime:guard_tsuba_v1", "effects.guardReduction", Side.SERVER, "1.1.0"),
        PRECISION("bt_export_runtime:precision_habaki_v1", "effects.precisionHabaki", Side.SERVER, "1.2.0"),
        REINFORCED("bt_export_runtime:reinforced_habaki_v1", "effects.reinforcedHabaki", Side.SERVER, "1.3.0"),
        SALTY_FISH("bt_export_runtime:salty_fish_feedback_v1", "effects.saltyFish", Side.SERVER, "1.6.0"),
        FUSION("bt_export_runtime:ordered_native_fusion_v1", "fusion", Side.SERVER, "1.7.0");

        private final String id;
        private final String payload;
        private final Side side;
        private final String sinceRuntime;

        Feature(String id, String payload, Side side, String sinceRuntime) {
            this.id = id;
            this.payload = payload;
            this.side = side;
            this.sinceRuntime = sinceRuntime;
        }

        public String id() { return id; }
        public String payload() { return payload; }
        public Side side() { return side; }
        public String sinceRuntime() { return sinceRuntime; }
    }

    private static final Map<String, Feature> FEATURES;
    static {
        Map<String, Feature> all = new HashMap<>();
        for (Feature feature : Feature.values()) {
            if (all.putIfAbsent(feature.id(), feature) != null) {
                throw new ExceptionInInitializerError("Duplicate portable capability: " + feature.id());
            }
        }
        FEATURES = Collections.unmodifiableMap(all);
    }

    public static Feature requireKnown(String id) {
        Feature feature = FEATURES.get(id);
        if (feature == null) throw new IllegalArgumentException("Unknown standalone capability " + id);
        return feature;
    }

    /**
     * Derive the exact contract from exported shape, not a user-writable list.
     * Same method is compiled into the exporter and clean receiving runtime.
     */
    public static List<String> forShape(String comboRoot, boolean attributes, int moveCount,
            boolean quickdraw, boolean spirit, boolean heavy, boolean stable, boolean guard,
            boolean precision, boolean reinforced) {
        return forShape(comboRoot, attributes, moveCount, quickdraw, spirit, heavy, stable, guard,
                precision, reinforced, false);
    }

    public static List<String> forShape(String comboRoot, boolean attributes, int moveCount,
            boolean quickdraw, boolean spirit, boolean heavy, boolean stable, boolean guard,
            boolean precision, boolean reinforced, boolean saltyFish) {
        return forShape(comboRoot, attributes, moveCount, quickdraw, spirit, heavy, stable, guard,
                precision, reinforced, saltyFish, false);
    }

    public static List<String> forShape(String comboRoot, boolean attributes, int moveCount,
            boolean quickdraw, boolean spirit, boolean heavy, boolean stable, boolean guard,
            boolean precision, boolean reinforced, boolean saltyFish, boolean fusion) {
        if (comboRoot == null || comboRoot.isBlank() || moveCount < 0 || moveCount > 6) {
            throw new IllegalArgumentException("Invalid portable combo contract");
        }
        if (precision && reinforced) {
            throw new IllegalArgumentException("A sealed blade cannot have two habaki variants");
        }
        var capabilities = new TreeSet<String>();
        capabilities.add(Feature.NATIVE.id());
        switch (comboRoot) {
            case "bt_export_runtime:iaido_root" -> capabilities.add(Feature.IAIDO.id());
            case "bt_export_runtime:rengeki_root" -> capabilities.add(Feature.RENGEKI.id());
            case "bt_export_runtime:dangaku_root" -> capabilities.add(Feature.DANGAKU.id());
            case "bt_export_runtime:custom_root" -> {
                if (moveCount == 0) throw new IllegalArgumentException("Empty custom sword cannot be sealed");
                capabilities.add(Feature.CUSTOM.id());
            }
            default -> {
                if (comboRoot.startsWith("bt_export_runtime:")) {
                    throw new IllegalArgumentException("Unknown portable combo root " + comboRoot);
                }
            }
        }
        if (!comboRoot.equals("bt_export_runtime:custom_root") && moveCount != 0) {
            throw new IllegalArgumentException("Portable moves require the custom combo root");
        }
        if (attributes) capabilities.add(Feature.ATTRIBUTES.id());
        if (quickdraw) capabilities.add(Feature.QUICKDRAW.id());
        if (spirit) capabilities.add(Feature.SPIRIT.id());
        if (heavy) capabilities.add(Feature.HEAVY.id());
        if (stable) capabilities.add(Feature.STABLE.id());
        if (guard) capabilities.add(Feature.GUARD.id());
        if (precision) capabilities.add(Feature.PRECISION.id());
        if (reinforced) capabilities.add(Feature.REINFORCED.id());
        if (saltyFish) capabilities.add(Feature.SALTY_FISH.id());
        if (fusion) capabilities.add(Feature.FUSION.id());
        return List.copyOf(capabilities);
    }

    private PortableCapabilityCatalog() {}
}

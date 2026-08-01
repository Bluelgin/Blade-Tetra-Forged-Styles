package dev.bladetetra.combat;

/**
 * A combat ruleset derived from the installed Tetra blade module.
 */
public enum BladeStyle {
    STANDARD("style.blade_tetra.standard"),
    IAIDO("style.blade_tetra.iaido"),
    RENGEKI("style.blade_tetra.rengeki"),
    DANGAKU("style.blade_tetra.dangaku");

    private final String translationKey;

    BladeStyle(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getDescriptionTranslationKey() {
        return translationKey + ".description";
    }
}

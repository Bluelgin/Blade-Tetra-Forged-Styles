package dev.bladetetra.client;

import dev.bladetetra.combat.ProgrammaticFusionPlan;
import dev.bladetetra.combat.ProgrammaticFusionProfile;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Client-side naming grammar for runtime-generated legacy fusions.
 *
 * <p>Native SlashBlade arts are deliberately restyled through Blade Tetra's
 * compact semantic vocabulary. Third-party arts keep their own localized source
 * names when available so add-on identity is not flattened into generic generated
 * names. Missing third-party translations fall back to the same bounded grammar.</p>
 */
public final class ProgrammaticFusionNameGrammar {
    private static final String NATIVE_NAMESPACE = "slashblade";
    private static final Map<ProgrammaticFusionProfile.Entry, Term> ENTRY_TERMS =
            new EnumMap<>(ProgrammaticFusionProfile.Entry.class);
    private static final Map<ProgrammaticFusionProfile.Response, Term> RESPONSE_TERMS =
            new EnumMap<>(ProgrammaticFusionProfile.Response.class);

    static {
        entry(ProgrammaticFusionProfile.Entry.DIRECT, "Flow", "流式");
        entry(ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT, "Severance", "断界");
        entry(ProgrammaticFusionProfile.Entry.SAKURA_END, "Sakura", "樱华");
        entry(ProgrammaticFusionProfile.Entry.VOID_SLASH, "Void", "虚空");
        entry(ProgrammaticFusionProfile.Entry.CIRCLE_SLASH, "Moonring", "月轮");
        entry(ProgrammaticFusionProfile.Entry.DRIVE_VERTICAL, "Falling Edge", "落锋");
        entry(ProgrammaticFusionProfile.Entry.DRIVE_HORIZONTAL, "Crosswind", "横风");
        entry(ProgrammaticFusionProfile.Entry.WAVE_EDGE, "Wave", "刃波");
        entry(ProgrammaticFusionProfile.Entry.PIERCING, "Piercing", "穿界");

        response(ProgrammaticFusionProfile.Response.FOCUSED_DRIVE, "Focus", "凝锋");
        response(ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO, "Echo", "回响");
        response(ProgrammaticFusionProfile.Response.SAKURA_CROSS, "Cross", "十文字");
        response(ProgrammaticFusionProfile.Response.VOID_TRIDENT, "Trident", "三华");
        response(ProgrammaticFusionProfile.Response.CIRCLE_RING, "Ringdance", "轮舞");
        response(ProgrammaticFusionProfile.Response.VERTICAL_DRIVE, "Falling Drive", "坠锋");
        response(ProgrammaticFusionProfile.Response.HORIZONTAL_DRIVE, "Gale", "横岚");
        response(ProgrammaticFusionProfile.Response.WAVE_EDGE, "Tide", "叠浪");
        response(ProgrammaticFusionProfile.Response.PIERCING_FOCUS, "Finale", "极");
    }

    public static Component name(ProgrammaticFusionPlan plan) {
        Objects.requireNonNull(plan, "programmatic fusion plan");
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        return Component.literal(compose(
                plan.releaseAbility(),
                plan.release().entry(),
                sourceName(plan.releaseAbility()),
                plan.responseAbility(),
                plan.response().response(),
                sourceName(plan.responseAbility()),
                language));
    }

    /** Pure semantic composition retained for native/native naming and tests. */
    static String compose(ProgrammaticFusionProfile.Entry entry,
            ProgrammaticFusionProfile.Response response, String language) {
        return compose(
                new ResourceLocation(NATIVE_NAMESPACE, "release"), entry, null,
                new ResourceLocation(NATIVE_NAMESPACE, "response"), response, null,
                language);
    }

    static String compose(ResourceLocation releaseAbility,
            ProgrammaticFusionProfile.Entry entry, String releaseSourceName,
            ResourceLocation responseAbility,
            ProgrammaticFusionProfile.Response response, String responseSourceName,
            String language) {
        Term release = Objects.requireNonNull(ENTRY_TERMS.get(entry),
                () -> "Missing release naming term for " + entry);
        Term reply = Objects.requireNonNull(RESPONSE_TERMS.get(response),
                () -> "Missing response naming term for " + response);
        boolean chinese = "zh_cn".equals(normalizeLanguage(language));
        String left = displayTerm(releaseAbility, releaseSourceName, release, chinese);
        String right = displayTerm(responseAbility, responseSourceName, reply, chinese);
        return chinese ? left + "·" + right : left + " · " + right;
    }

    static boolean coversEverySemantic() {
        return ENTRY_TERMS.size() == ProgrammaticFusionProfile.Entry.values().length
                && RESPONSE_TERMS.size() == ProgrammaticFusionProfile.Response.values().length;
    }

    private static String sourceName(ResourceLocation ability) {
        if (ability == null || isNative(ability)) {
            return null;
        }
        String key = Util.makeDescriptionId("slash_art", ability);
        return I18n.exists(key) ? I18n.get(key) : null;
    }

    private static String displayTerm(ResourceLocation ability, String sourceName,
            Term fallback, boolean chinese) {
        if (!isNative(ability) && sourceName != null && !sourceName.isBlank()) {
            return sourceName;
        }
        return chinese ? fallback.zh() : fallback.en();
    }

    private static boolean isNative(ResourceLocation ability) {
        return ability != null && NATIVE_NAMESPACE.equals(ability.getNamespace());
    }

    private static void entry(ProgrammaticFusionProfile.Entry entry, String en, String zh) {
        ENTRY_TERMS.put(entry, new Term(en, zh));
    }

    private static void response(ProgrammaticFusionProfile.Response response, String en, String zh) {
        RESPONSE_TERMS.put(response, new Term(en, zh));
    }

    private static String normalizeLanguage(String language) {
        return language == null ? "" : language.toLowerCase(Locale.ROOT);
    }

    private record Term(String en, String zh) {
    }

    private ProgrammaticFusionNameGrammar() {
    }
}

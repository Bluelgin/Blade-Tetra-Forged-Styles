package dev.bladetetra.client;

import dev.bladetetra.combat.ProgrammaticFusionPlan;
import dev.bladetetra.combat.ProgrammaticFusionProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Client-side naming grammar for runtime-generated legacy fusions.
 *
 * <p>The combat layer already reduces every source ability to an ordered
 * release/response semantic pair. This class gives those semantics a small,
 * bounded display vocabulary so one structural programmatic Slash Art can still
 * present a distinct name for every generated combination without registering
 * A+B-specific arts.</p>
 */
public final class ProgrammaticFusionNameGrammar {
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
                plan.release().entry(), plan.response().response(), language));
    }

    static String compose(ProgrammaticFusionProfile.Entry entry,
            ProgrammaticFusionProfile.Response response, String language) {
        Term release = Objects.requireNonNull(ENTRY_TERMS.get(entry),
                () -> "Missing release naming term for " + entry);
        Term reply = Objects.requireNonNull(RESPONSE_TERMS.get(response),
                () -> "Missing response naming term for " + response);
        boolean chinese = "zh_cn".equals(normalizeLanguage(language));
        return chinese
                ? release.zh() + "·" + reply.zh()
                : release.en() + " · " + reply.en();
    }

    static boolean coversEverySemantic() {
        return ENTRY_TERMS.size() == ProgrammaticFusionProfile.Entry.values().length
                && RESPONSE_TERMS.size() == ProgrammaticFusionProfile.Response.values().length;
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

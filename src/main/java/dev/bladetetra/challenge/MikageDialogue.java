package dev.bladetetra.challenge;

import net.minecraft.network.chat.Component;

/**
 * Canonical Mikage subtitle timing. Future voice clips use these same events,
 * so subtitle spacing and audio playback cannot drift into separate schedules.
 */
enum MikageDialogue {
    INTRO_1("dialogue.blade_tetra.mikage.intro1", "voice.mikage.intro_1", 130),
    INTRO_2("dialogue.blade_tetra.mikage.intro2", "voice.mikage.intro_2", 172),
    AKATSUKI("dialogue.blade_tetra.mikage.akatsuki", "voice.mikage.akatsuki", 141),
    BEGIN("dialogue.blade_tetra.mikage.begin", "voice.mikage.battle_begin", 141),
    PHASE_2("dialogue.blade_tetra.mikage.phase2", "voice.mikage.phase_2", 146),
    PHASE_3("dialogue.blade_tetra.mikage.phase3", "voice.mikage.phase_3", 170),
    BOUNDARY_SLASH("dialogue.blade_tetra.mikage.boundary_slash",
            "voice.mikage.boundary_slash", 62),
    TORII_SWEEP("dialogue.blade_tetra.mikage.torii_sweep",
            "voice.mikage.torii_sweep", 96),
    TORII_CAGE("dialogue.blade_tetra.mikage.torii_cage",
            "voice.mikage.torii_cage", 78),
    CAGE_GUARD("dialogue.blade_tetra.mikage.cage_guard",
            "voice.mikage.cage_guard", 122),
    DEFEAT_1("dialogue.blade_tetra.mikage.defeat1", "voice.mikage.defeat_1", 86),
    DEFEAT_2("dialogue.blade_tetra.mikage.defeat2", "voice.mikage.defeat_2", 136),
    DEFEAT_3("dialogue.blade_tetra.mikage.defeat3", "voice.mikage.defeat_3", 160),
    REMINISCENCE_INTRO("dialogue.blade_tetra.mikage.reminiscence_intro",
            "voice.mikage.reminiscence_intro", 150),
    REMINISCENCE_BEGIN("dialogue.blade_tetra.mikage.reminiscence_begin",
            "voice.mikage.reminiscence_begin", 122),
    VICTORY("dialogue.blade_tetra.mikage.victory", "voice.mikage.victory", 246),
    REMINISCENCE_VICTORY("dialogue.blade_tetra.mikage.reminiscence_victory",
            "voice.mikage.reminiscence_victory", 176);

    private final String translationKey;
    private final String voiceEvent;
    private final int holdTicks;

    MikageDialogue(String translationKey, String voiceEvent, int holdTicks) {
        this.translationKey = translationKey;
        this.voiceEvent = voiceEvent;
        this.holdTicks = holdTicks;
    }

    Component component() {
        return Component.translatable(translationKey);
    }

    int holdTicks() {
        return holdTicks;
    }

    String translationKey() {
        return translationKey;
    }

    String voiceEvent() {
        return voiceEvent;
    }
}

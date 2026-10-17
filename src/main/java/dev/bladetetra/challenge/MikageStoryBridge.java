package dev.bladetetra.challenge;

/** Stable connection to the existing subtitle/voice/postgame session. */
final class MikageStoryBridge {
    private final MikageEntity owner;
    MikageStoryBridge(MikageEntity owner) { this.owner = owner; }

    void phase(int phase) {
        ChallengeManager.queueDialogue(owner,
                phase == 2 ? MikageDialogue.PHASE_2 : MikageDialogue.PHASE_3);
    }

    void defeated() { ChallengeManager.onMikageDefeated(owner); }
}

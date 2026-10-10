package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.*;
import java.util.List;
import static dev.bladetetra.challenge.mikage.CombatObservation.Behavior.*;

/** Observable habits bias the next committed move; they never suppress a player's attack. */
final class MikageFollowups {
    static List<SkillTransition> links() {
        return List.of(
                new SkillTransition(MikageMove.COMBO.id(), MikageMove.ZANSHIN.id(), PRESSURE, .4, 1.4),
                new SkillTransition(MikageMove.IAIDO.id(), MikageMove.FAN.id(), RETREATING, .4, 1.2),
                new SkillTransition(MikageMove.WAVE.id(), MikageMove.VOLLEY.id(), AIRBORNE, .4, 1.3),
                new SkillTransition(MikageMove.FAN.id(), MikageMove.SAKURA.id(), APPROACHING, .4, 1.3),
                new SkillTransition(MikageMove.CUT.id(), MikageMove.CLEAVE.id(), GUARDING, .4, 1.2));
    }
    private MikageFollowups() { }
}

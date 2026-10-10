package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import static dev.bladetetra.challenge.mikage.SkillSpec.Tactic.*;

/** Every selectable attack is an owned execution; signature pacing is encounter-local. */
final class MikageSkillSelection {
    private static final SkillSpec GATES = special("thousand_gates", GAP_CLOSE, COUNTER);
    private static final SkillSpec CORRIDOR = special("gate_corridor", GAP_CLOSE, ANTI_AIR);
    private static final SkillSpec BARRAGE = special("gate_barrage", RANGED, GUARD_PRESSURE);
    private static final SkillSpec ECHO = special("moon_echo", COUNTER, CLOSE);
    private static final SkillSpec BOUNDARY = special("boundary_flash", SETUP, GUARD_PRESSURE);
    private static final List<SkillSpec> SPECIALS = List.of(GATES, CORRIDOR, BARRAGE, ECHO, BOUNDARY);
    private final MikageEntity owner;
    private final SkillPacing pacing = new SkillPacing();
    MikageSkillSelection(MikageEntity owner) { this.owner = owner; }
    private static SkillSpec special(String id, SkillSpec.Tactic... tactics) {
        return new SkillSpec("blade_tetra:" + id, "signature", Set.of(tactics), 0, 48);
    }
    List<SkillSpec> available(ServerPlayer target, int phase) {
        double distance = owner.distanceTo(target);
        if (owner.movement().needsMeleeExchange(target)) return List.of(MikageMove.COMBO.spec());
        var choices = new ArrayList<SkillSpec>();
        for (MikageMove move : MikageMove.values()) {
            if (phase < move.phase || distance > move.range || !pacing.ready(move.id(), owner.tickCount)) continue;
            if (move == MikageMove.CUT && phase == 3 || move == MikageMove.SUPER_CUT && phase != 3) continue;
            if ((move == MikageMove.RAIN || move == MikageMove.DUEL) && !owner.onGround()) continue;
            choices.add(move.spec());
        }
        for (SkillSpec special : SPECIALS) {
            if (!pacing.signatureReady(special.id(), owner.tickCount) || distance > special.maximumDistance()) continue;
            if (special == GATES && distance > 32 || special == ECHO && distance > 5.2
                    || special == BOUNDARY && (phase != 3 || !owner.onGround() || !target.onGround())
                    || special == BARRAGE && !target.onGround()) continue;
            choices.add(special);
        }
        return choices;
    }
    SkillSpec spec(String id) {
        return SPECIALS.stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseGet(() -> Arrays.stream(MikageMove.values()).filter(m -> m.id().equals(id))
                        .findFirst().orElseThrow().spec());
    }
    SkillExecution create(String id, ServerPlayer target, Runnable committed) {
        Runnable release = () -> {
            int cooldown = id.equals(BOUNDARY.id()) ? 1000 : id.equals(BARRAGE.id()) ? 720
                    : id.equals(CORRIDOR.id()) ? 480 : id.equals(GATES.id()) ? 400 : 300;
            pacing.committed(id, owner.tickCount, cooldown, SPECIALS.stream().anyMatch(s -> s.id().equals(id)));
            committed.run();
        };
        owner.getMainHandItem().getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                .ifPresent(state -> state.setTargetEntityId(target));
        if (id.equals(GATES.id())) return new MikageThousandGatesExecution(owner, target, release);
        if (id.equals(CORRIDOR.id())) return new MikageGateCorridorExecution(owner, target, release);
        if (id.equals(BARRAGE.id())) return new MikageGateBarrageExecution(owner, target, release);
        if (id.equals(ECHO.id())) return new MikageMoonEchoExecution(owner, target, release);
        if (id.equals(BOUNDARY.id())) return new MikageBoundaryExecution(owner, target, release);
        MikageMove move = Arrays.stream(MikageMove.values()).filter(m -> m.id().equals(id)).findFirst().orElseThrow();
        return move.melee() ? new MikageMeleeExecution(owner, target, move, release)
                : new MikageSwordplayExecution(owner, target, move, release);
    }
}

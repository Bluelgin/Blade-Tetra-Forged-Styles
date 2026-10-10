package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.*;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ServerLevelData;
import java.util.*;
import static dev.bladetetra.challenge.MikageNativeSmokeTest.*;

/** Only called by the explicitly enabled isolated Forge diagnostic; uses the actual encounter runner. */
final class MikageRhythmSmokeTest {
    static void run(MikageEntity boss, ServerPlayer player, ServerPlayer outsider) throws Exception {
        var field = MikageEncounter.class.getDeclaredField("skills"); field.setAccessible(true);
        SkillRunner runner = (SkillRunner) field.get(boss.encounter());
        var clock = (ServerLevelData) boss.level().getServer().overworld().getLevelData();
        boss.duel().clear(); reset(player, outsider);
        boss.setPos(180, 65, -10); boss.setYRot(0); boss.setOnGround(true);
        var phrase = new Phrase(boss, player, runner, clock);
        phrase.play(3);
        check(phrase.inputs == 3 && phrase.blades.size() == 3, "all three native beats actually release and require fresh input: inputs="
                + phrase.inputs + ", blades=" + phrase.blades.size() + ", health=" + player.getHealth()
                + ", early=" + phrase.earlyClash + ", protected=" + phrase.protectedSecond
                + ", clock=" + clock.getGameTime() + ", realm=" + boss.level().getGameTime());
        check(phrase.earlyClash && phrase.protectedSecond, "early clash continues; second fresh input works inside old protection");
        check(player.getHealth() == 20 && outsider.getHealth() == 20, "three real native parries prevent participant damage and isolate outsider");
        check(!runner.active() && boss.duel().staggered(), "finisher parry closes actual encounter execution and opens recovery");
        check(boss.actionRemainingTicks() == MeleeRhythm.FINISHER_OPENING, "finisher awards the full authored opening");

        clock.setGameTime(clock.getGameTime() + MeleeRhythm.FINISHER_OPENING + 1);
        boss.duel().tick((net.minecraft.server.level.ServerLevel) boss.level());
        phrase = new Phrase(boss, player, runner, clock); phrase.play(3);
        check(phrase.inputs == 2 && phrase.blades.size() == 2 && !runner.active(), "fifth accumulated parry stops the next phrase before its finisher");
        check(boss.actionRemainingTicks() == DuelDefenseState.STAGGER_TICKS, "full balance break is not shortened to a finisher opening");

        boss.duel().clear(); reset(player, outsider); boss.setAction(MikageEntity.MikageAction.IDLE, 1);
        phrase = new Phrase(boss, player, runner, clock); phrase.play(1);
        check(phrase.inputs == 1 && phrase.blades.size() == 2, "one input cannot reuse protection on the second beat; no third attack after hit");
        check(player.getHealth() < 20 && outsider.getHealth() == 20, "missing the next parry accepts real native damage only for participant");
        check(!runner.active() && !boss.duel().staggered(), "actual hit ends the round after brief recovery without a free counter opening");
        check(ownedMap().isEmpty(), "all rhythmic blade bindings release through actual encounter cleanup");
    }
    private static final class Phrase {
        final MikageEntity boss; final ServerPlayer player; final SkillRunner runner; final ServerLevelData clock;
        final Set<Entity> blades = Collections.newSetFromMap(new IdentityHashMap<>());
        int inputs; boolean earlyClash, protectedSecond;
        Phrase(MikageEntity boss, ServerPlayer player, SkillRunner runner, ServerLevelData clock) {
            this.boss = boss; this.player = player; this.runner = runner; this.clock = clock;
        }
        void play(int parryLimit) throws Exception {
            check(runner.start(new MikageMeleeExecution(boss, player, MikageMove.COMBO, () -> { })), "native rhythmic phrase starts");
            try {
                for (int frame = 0; frame < 100 && runner.active(); frame++) {
                    clock.setGameTime(clock.getGameTime() + 1);
                    if (frame == 0) check(clock.getGameTime() == boss.level().getGameTime(), "diagnostic advances the real encounter clock");
                    Set<Entity> attacks = ownedMap().getOrDefault(runner.scope(), Set.of());
                    if (inputs < parryLimit && attacks.stream().anyMatch(e -> e instanceof EntitySlashEffect && e.tickCount == 1)) {
                        if (inputs == 1) protectedSecond = boss.duel().protects(player);
                        boss.duel().swing(player, true); inputs++;
                    }
                    runner.tick();
                    if (runner.active()) {
                        blades.addAll(ownedMap().getOrDefault(runner.scope(), Set.of())); tick(runner.scope(), 1);
                    }
                    if (inputs == 1 && boss.duel().protects(player) && runner.active() && !boss.duel().staggered()) earlyClash = true;
                }
                check(!runner.active(), "native phrase has a finite ending");
            } finally { runner.stop(SkillExecution.StopReason.CANCELLED); }
        }
    }
    private MikageRhythmSmokeTest() { }
}

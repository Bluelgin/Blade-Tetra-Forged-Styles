package dev.bladetetra.client.sakura;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class SakuraShardChoreographyTest {
    private static final SakuraShardFormation.Frame FRAME = new SakuraShardFormation.Frame(Vec3.ZERO, 0, 1.62);
    private static final Vec3 TARGET = new Vec3(0, 1.3, 3.5);

    @Test void openingWingsAlternateAndAllQualityLevelsKeepSevenFinaleWaves() {
        for (int count : new int[]{24, 48, 72}) {
            for (int stage = 1; stage <= 2; stage++) {
                var pool = new SakuraShardPool(count, FRAME, 0);
                pool.launch(stage, 1, 0, 0, TARGET, -1, false);
                for (var s : pool.shards) if (s.flying()) assertEquals(stage == 1, s.index < count / 2);
            }
            var pool = new SakuraShardPool(count, FRAME, 0);
            pool.launch(6, 1, 0, 0, TARGET, -1, false);
            var waves = new HashSet<Double>();
            for (var s : pool.shards) if (s.flying()) waves.add(s.delay);
            assertEquals(7, waves.size(), "LOD must retain every finale wave");
            assertEquals(count / 2, count - pool.idleCount());
        }
    }

    @Test void selectionVisitsEveryIndexExactlyOnce() {
        for (int count : new int[]{13, 24, 48, 72}) for (int stage = 1; stage <= 6; stage++) {
            var selected = new HashSet<Integer>();
            for (int j = 0; j < count; j++) selected.add(SakuraShardChoreography.candidate(stage, j, count, 11));
            assertEquals(count, selected.size());
        }
    }

    @Test void sweepsCrossTheImpactCenterAndJoinsAreContinuous() {
        Vec3 start = new Vec3(-1, 1, -1), forward = new Vec3(0, 0, 1);
        for (int stage = 1; stage <= 6; stage++) for (int member = 0; member < 24; member++) {
            assertEquals(start, SakuraShardChoreography.launch(start, TARGET, forward, member, stage, 0));
            assertEquals(SakuraShardChoreography.cut(TARGET, forward, member, stage, 0),
                    SakuraShardChoreography.launch(start, TARGET, forward, member, stage, 1));
            assertTrue(SakuraShardChoreography.cut(TARGET, forward, member, stage, .5).distanceToSqr(TARGET) < .3);
            assertTrue(SakuraShardChoreography.cut(TARGET, forward, member, stage, 0)
                    .distanceToSqr(SakuraShardChoreography.cut(TARGET, forward, member, stage, 1)) > 1);
        }
    }

    @Test void movingOwnerAndRapidCyclesUseTheSameObjectsAndEventuallyReturn() {
        for (int count : new int[]{24, 48, 72}) {
            var pool = new SakuraShardPool(count, FRAME, 0);
            var identities = pool.shards.clone();
            for (int t = 0; t < 300; t++) {
                if (t % 7 == 0) assertTrue(pool.launch(t / 7 % 6 + 1, t + 1, t, t, TARGET, -1, false));
                var moving = new SakuraShardFormation.Frame(new Vec3(Math.sin(t * .05), 0, 0), t * .01, 1.62);
                pool.tick(moving, t);
                for (int i = 0; i < count; i++) {
                    assertSame(identities[i], pool.shards[i]);
                    assertTrue(pool.shards[i].position.distanceToSqr(pool.shards[i].previous) < 36);
                }
            }
            for (int t = 300; t < 340; t++) pool.tick(FRAME, t);
            assertEquals(count, pool.idleCount());
        }
    }
}

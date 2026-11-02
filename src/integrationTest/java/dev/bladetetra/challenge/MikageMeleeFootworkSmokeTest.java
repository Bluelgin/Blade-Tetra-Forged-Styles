package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import static dev.bladetetra.challenge.MikageNativeSmokeTest.check;

/** Actual grounded routes and facing locks in the isolated diagnostic, without simulating damage. */
final class MikageMeleeFootworkSmokeTest {
    static void run(MikageEntity boss, ServerPlayer player) {
        runRoute(boss, player, false);
        BlockPos wall = new BlockPos(180, 65, -8);
        boss.level().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        boss.level().setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
        try { runRoute(boss, player, true); }
        finally {
            boss.level().setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
            boss.level().setBlock(wall.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        boss.setPos(180, 65, -10);
    }
    private static void runRoute(MikageEntity boss, ServerPlayer player, boolean wall) {
        boss.setPos(180, 65, -10); boss.setYRot(0); boss.setOnGround(true); player.setPos(180, 65, -7);
        var rhythm = new MeleeRhythm(MikageMove.COMBO);
        var footwork = new MikageMeleeFootwork(boss, MikageMove.COMBO);
        int locks = 0; Vec3 finalPosition = null;
        for (int frame = 0; frame < 100 && !rhythm.complete(); frame++) {
            rhythm.tick();
            float yaw = boss.getYRot();
            boolean locked = rhythm.beat() >= 0 && rhythm.untilNextBeat() == 4;
            if (locked) player.setPos(player.getX() + 10, player.getY(), player.getZ());
            Vec3 before = boss.position(); footwork.tick(player, rhythm);
            check(before.distanceTo(boss.position()) <= .701, "pursuit uses bounded visible steps, never teleports");
            if (locked) { locks++; check(boss.getYRot() == yaw, "late sidestep cannot retarget the locked next blade"); }
            if (wall) check(boss.getBoundingBox().maxZ <= -8, "actual block collisions prevent chasing through a wall");
            if (rhythm.releaseDue()) {
                int beat = rhythm.release(); footwork.released(player);
                if (beat < 2) player.setPos(180, 65, -3 + beat * 4);
                else finalPosition = boss.position();
            }
            if (finalPosition != null) check(boss.position().equals(finalPosition), "finisher recovery cannot add pursuit or auto retreat");
        }
        check(locks == 2 && rhythm.pursuits() == 2, "whole phrase has two pursuit tells and two immutable final directions");
        if (!wall) check(boss.getZ() > -5, "retreat actually draws the boss forward instead of keeping her at original range");
    }
    private MikageMeleeFootworkSmokeTest() { }
}

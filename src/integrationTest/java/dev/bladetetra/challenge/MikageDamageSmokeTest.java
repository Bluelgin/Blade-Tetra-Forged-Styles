package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.CastScope;
import dev.bladetetra.challenge.mikage.SkillRunner;
import dev.bladetetra.challenge.mikage.SkillExecution;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import static dev.bladetetra.challenge.MikageNativeSmokeTest.*;

/** Real native hits exercise final mitigation, shield contact and cancellation, not string contracts. */
final class MikageDamageSmokeTest {
    static void run(MikageEntity boss, ServerPlayer player, ServerPlayer outsider) throws Exception {
        check(boss.getMainHandItem().is(SlashBladeItems.SLASHBLADE.get()), "boss holds native iron katana, not modular Tetra blade");
        boss.setPos(180, 65, -10); boss.setYRot(0); boss.duel().clear();
        reset(player, outsider); boss.defenseController().playerDefenseProfiles.clear();
        player.getAttribute(Attributes.ARMOR).setBaseValue(20);
        player.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        int[] contacts = {0};
        try {
            hit(boss, player, contacts, 901);
            float loss = 20 - player.getHealth();
            check(loss > 2 && loss <= 7.2001F, "armor mitigates corrected raw damage, final health cap remains: loss=" + loss);
            check(contacts[0] == 1, "successful native hit notifies once after actual health loss");
        } finally {
            player.getAttribute(Attributes.ARMOR).setBaseValue(0);
            player.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        }
        reset(player, outsider); contacts[0] = 0; player.setAbsorptionAmount(40);
        hit(boss, player, contacts, 902);
        check(player.getHealth() == 20 && player.getAbsorptionAmount() < 40 && contacts[0] == 1,
                "absorption-only impact counts as a real hit without health damage");

        reset(player, outsider); contacts[0] = 0;
        var cancellation = new CancelDamage(player);
        MinecraftForge.EVENT_BUS.register(cancellation);
        try {
            hit(boss, player, contacts, 903);
            check(player.getHealth() == 20 && contacts[0] == 0,
                    "late final-damage cancellation cannot end a melee phrase or report a hit");
        } finally { MinecraftForge.EVENT_BUS.unregister(cancellation); }
        reset(player, outsider); boss.defenseController().playerDefenseProfiles.clear();

        float maximum = boss.getMaxHealth(), floor = maximum * 2 / 3;
        boss.setPhase(1); boss.setHealth(floor + 10); boss.duel().clear();
        // The operation represents a real reduction after the raw phase-clamped amount.
        boolean accepted = boss.damage().hurt(player.damageSources().playerAttack(player), 10,
                (source, raw) -> { boss.setHealth(boss.getHealth() - boss.damage().phaseLimitedDamage(raw * .5F)); return true; });
        check(accepted && Math.abs(boss.getHealth() - (floor + 5)) < .001,
                "phase gate preserves actual reduced damage instead of setting health down to floor");
        boss.setHealth(maximum); boss.defenseController().hurtCooldownUntil.clear();
        landingAndUnavailableGate(boss, player, outsider);
    }
    private static void landingAndUnavailableGate(MikageEntity boss, ServerPlayer player, ServerPlayer outsider) throws Exception {
        Vec3 start = new Vec3(180, 65, -10);
        boss.setPos(start);
        check(!MikageLanding.supported(boss, start.add(0, 2, 0)), "an aerial position is not a landing");
        Vec3 ground = MikageLanding.find(boss, start.add(0, 2, 0));
        check(ground != null && MikageLanding.supported(boss, ground), "recovery finds actual supported ground");
        // The boss straddles block boundaries; replacing only the centre block leaves full-height
        // neighbouring support under its feet, correctly making Y=65 the first safe landing.
        var previous = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        try {
            for (int x = 179; x <= 181; x++) for (int z = -11; z <= -9; z++) {
                var floor = new BlockPos(x, 64, z);
                previous.put(floor, boss.level().getBlockState(floor));
                boss.level().setBlock(floor, Blocks.STONE_SLAB.defaultBlockState(), 3);
            }
            Vec3 slab = MikageLanding.find(boss, start);
            check(slab != null && Math.abs(slab.y - 64.5) < .001 && MikageLanding.supported(boss, slab),
                    "fractional slab tops are recovered using collision geometry");
        } finally { previous.forEach((at, state) -> boss.level().setBlock(at, state, 3)); }

        var field = MikageEncounter.class.getDeclaredField("skills"); field.setAccessible(true);
        SkillRunner runner = (SkillRunner) field.get(boss.encounter());
        reset(player, outsider); player.setPos(180, 500, -7); player.setOnGround(true);
        boss.setNoGravity(false); boss.noPhysics = false;
        try {
            check(runner.start(new MikageThousandGatesExecution(boss, player, () -> { })), "unavailable-arrival diagnostic cast starts");
            for (int i = 0; i < 200 && runner.active(); i++) runner.tick();
            check(!runner.active(), "consecutive unavailable arrivals terminate the real execution before timeout");
            check(!boss.isInvisible() && !boss.isNoGravity() && !boss.noPhysics && !boss.isWithinThousandGates(),
                    "failed-arrival exit restores visibility, gravity and physics");
            check(ownedMap().isEmpty(), "failed-arrival cast releases all native bindings");
        } finally {
            runner.stop(SkillExecution.StopReason.CANCELLED);
            reset(player, outsider); boss.setPos(start);
        }
    }
    private static void hit(MikageEntity boss, ServerPlayer player, int[] contacts, long id) throws Exception {
        try (var scope = new CastScope(id)) {
            var rule = new MikageNativeCombat.Rule(player.getUUID(), true, false,
                    (p, source) -> true, (p, source) -> contacts[0]++);
            MikageNativeCombat.run(boss, scope, rule, () -> AttackManager.doSlash(boss, 25, false, false, .88));
            tick(scope, 4);
        }
    }
    public static final class CancelDamage {
        private final ServerPlayer player;
        CancelDamage(ServerPlayer player) { this.player = player; }
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void damage(LivingDamageEvent event) {
            if (event.getEntity() == player) event.setCanceled(true);
        }
    }
    private MikageDamageSmokeTest() { }
}

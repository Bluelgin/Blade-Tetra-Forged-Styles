package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reuses SlashBlade's native Judgement Cut renderer as a cosmetic-only forged cue.
 *
 * <p>The spawned entity intentionally has no shooter and zero damage, so its
 * normal cyclic area-attack branch never runs. It is discarded at the end of the
 * native ten-tick presentation window, before EntityJudgementCut can enter its
 * potion/burst cleanup path. Actual forged-SA damage is owned exclusively by the
 * bounded summoned swords spawned by {@link ProceduralSlashArtExecutor}.</p>
 */
final class ForgedJudgementPresentation {
    static final int NATIVE_LIFETIME_TICKS = 10;
    static final int SAFE_DISCARD_TICKS = 10;

    private static final List<PendingVisual> PENDING = new ArrayList<>();

    static void spawn(ServerPlayer player, Vec3 position, int color) {
        ServerLevel level = player.serverLevel();
        EntityJudgementCut cut = new EntityJudgementCut(
                SlashBlade.RegistryEvents.JudgementCut, level);
        cut.setPos(position.x, position.y, position.z);
        cut.setColor(color);
        cut.setDamage(0.0D);
        cut.setNoClip(true);
        cut.setLifetime(NATIVE_LIFETIME_TICKS);
        // Do not assign an owner/shooter. EntityJudgementCut gates its native
        // areaAttack and critical slash spawning behind getShooter() != null.
        LegacyFusionCombatSupport.markVisualOnly(cut);
        level.addFreshEntity(cut);

        level.playSound(null, cut.getX(), cut.getY(), cut.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.5F, 0.8F / (player.getRandom().nextFloat() * 0.4F + 0.8F));

        PENDING.add(new PendingVisual(
                level.dimension(), cut.getUUID(),
                level.getGameTime() + SAFE_DISCARD_TICKS));
    }

    static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        var iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingVisual pending = iterator.next();
            ServerLevel level = event.getServer().getLevel(pending.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() < pending.discardAt()) {
                continue;
            }
            iterator.remove();
            Entity entity = level.getEntity(pending.entityId());
            if (entity instanceof EntityJudgementCut
                    && entity.getPersistentData().getBoolean(
                            BladeTechniqueHandler.TECHNIQUE_ENTITY)) {
                entity.discard();
            }
        }
    }

    static void onLevelUnload(ServerLevel level) {
        PENDING.removeIf(pending -> pending.dimension().equals(level.dimension()));
    }

    static void clear() {
        PENDING.clear();
    }

    private record PendingVisual(
            ResourceKey<Level> dimension,
            UUID entityId,
            long discardAt) {
    }

    private ForgedJudgementPresentation() {
    }
}
